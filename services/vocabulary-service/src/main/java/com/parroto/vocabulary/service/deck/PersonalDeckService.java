package com.parroto.vocabulary.service.deck;

import com.parroto.common.exception.ApiException;
import com.parroto.vocabulary.dto.CardResponse;
import com.parroto.vocabulary.dto.CreatePersonalWordRequest;
import com.parroto.vocabulary.dto.LearnedWordResponse;
import com.parroto.vocabulary.dto.MyDeckDtos.DeckUsage;
import com.parroto.vocabulary.dto.MyDeckDtos.PersonalCardRequest;
import com.parroto.vocabulary.dto.MyDeckDtos.PersonalDeckList;
import com.parroto.vocabulary.dto.MyDeckDtos.PersonalDeckRequest;
import com.parroto.vocabulary.dto.MyDeckDtos.PersonalDeckResponse;
import com.parroto.vocabulary.entity.VocabCard;
import com.parroto.vocabulary.entity.VocabDeck;
import com.parroto.vocabulary.repository.UserVocabRepository;
import com.parroto.vocabulary.repository.VocabCardRepository;
import com.parroto.vocabulary.repository.VocabDeckRepository;
import com.parroto.vocabulary.repository.VocabGroupRepository;
import com.parroto.vocabulary.service.PersonalWordService;
import com.parroto.vocabulary.service.PremiumClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * P2-C "Bộ thẻ của tôi": a learner's personal decks (the notebook + up to N custom decks) and
 * the cards in them. Custom decks behave like public decks for studying (a card enters SRS when it
 * is first graded); the notebook keeps its T13 rules (every card has a user_vocab row, due at once),
 * so adding to the notebook delegates to {@link PersonalWordService}.
 */
@Service
public class PersonalDeckService {

    static final String MSG_CARD_NOT_FOUND = "Không tìm thấy thẻ";
    static final String MSG_DUPLICATE_IN_DECK = "Từ này đã có trong bộ thẻ";
    static final String MSG_NOTEBOOK_FIXED = "Không thể đổi tên hoặc xoá sổ tay";

    private final VocabDeckRepository deckRepository;
    private final VocabCardRepository cardRepository;
    private final VocabGroupRepository groupRepository;
    private final UserVocabRepository userVocabRepository;
    private final PersonalWordService personalWordService;
    private final PremiumClient premiumClient;
    private final DeckGroupService groupService;
    private final DeckCacheEvictor cacheEvictor;

    public PersonalDeckService(VocabDeckRepository deckRepository, VocabCardRepository cardRepository,
                               VocabGroupRepository groupRepository, UserVocabRepository userVocabRepository,
                               PersonalWordService personalWordService, PremiumClient premiumClient,
                               DeckGroupService groupService, DeckCacheEvictor cacheEvictor) {
        this.deckRepository = deckRepository;
        this.cardRepository = cardRepository;
        this.groupRepository = groupRepository;
        this.userVocabRepository = userVocabRepository;
        this.personalWordService = personalWordService;
        this.premiumClient = premiumClient;
        this.groupService = groupService;
        this.cacheEvictor = cacheEvictor;
    }

    @Transactional
    public PersonalDeckList list(UUID userId) {
        // The notebook is created lazily elsewhere; make sure it shows up as a target from day one.
        if (deckRepository.findByOwnerIdForUpdate(userId).isEmpty()) {
            deckRepository.insertPersonalDeckIfAbsent(userId, PersonalWordService.PERSONAL_DECK_NAME);
        }
        List<PersonalDeckResponse> decks = deckRepository.findPersonalDecks(userId).stream()
                .map(d -> PersonalDeckResponse.from(d, (int) groupRepository.countByDeckId(d.getId())))
                .toList();
        return new PersonalDeckList(decks, usage(userId));
    }

    @Transactional(readOnly = true)
    public DeckUsage usage(UUID userId) {
        boolean premium = premiumClient.isPremium(userId.toString());
        long notebookCards = deckRepository.findPersonalDecks(userId).stream()
                .filter(VocabDeck::isNotebookDeck)
                .mapToLong(d -> d.getCardCount() == null ? 0 : d.getCardCount())
                .sum();
        return new DeckUsage(premium, deckRepository.countCustomDecks(userId), VocabLimits.maxDecks(premium),
                cardRepository.countCustomCards(userId), VocabLimits.maxCards(premium), notebookCards,
                VocabLimits.NOTEBOOK_MAX_CARDS);
    }

    @Transactional
    public PersonalDeckResponse create(UUID userId, PersonalDeckRequest request) {
        String name = cleanDeckName(request == null ? null : request.name());
        String description = cleanDescription(request == null ? null : request.description());
        // Serialize concurrent creates of one user on the notebook row (created if missing).
        lockOwner(userId);
        boolean premium = premiumClient.isPremium(userId.toString());
        if (deckRepository.countCustomDecks(userId) >= VocabLimits.maxDecks(premium)) {
            throw ApiException.badRequest(VocabLimits.deckLimitMessage(premium));
        }
        VocabDeck deck = deckRepository.saveAndFlush(VocabDeck.builder()
                .name(name).description(description).ownerId(userId).createdBy(userId)
                .isNotebook(false).isProOnly(false).cardCount(0).build());
        cacheEvictor.deckChanged(deck.getId(), userId);
        return PersonalDeckResponse.from(deck, 0);
    }

    @Transactional
    public PersonalDeckResponse update(UUID userId, UUID deckId, PersonalDeckRequest request) {
        VocabDeck deck = ownDeckForUpdate(userId, deckId);
        if (deck.isNotebookDeck()) {
            throw ApiException.badRequest(MSG_NOTEBOOK_FIXED);
        }
        deck.setName(cleanDeckName(request == null ? null : request.name()));
        deck.setDescription(cleanDescription(request == null ? null : request.description()));
        deck = deckRepository.saveAndFlush(deck);
        cacheEvictor.deckChanged(deckId, userId);
        return PersonalDeckResponse.from(deck, (int) groupRepository.countByDeckId(deckId));
    }

    /** Deletes a custom deck with its groups, cards and the SRS progress of those cards (FK cascades). */
    @Transactional
    public void delete(UUID userId, UUID deckId) {
        VocabDeck deck = ownDeckForUpdate(userId, deckId);
        if (deck.isNotebookDeck()) {
            throw ApiException.badRequest(MSG_NOTEBOOK_FIXED);
        }
        deckRepository.delete(deck);
        cacheEvictor.deckChanged(deckId, userId);
    }

    /** Adds one card to a personal deck (notebook: also schedules it for review, T13 rules). */
    @Transactional
    public CardResponse addCard(UUID userId, UUID deckId, PersonalCardRequest request) {
        if (request == null) throw ApiException.badRequest("Dữ liệu không hợp lệ");
        CreatePersonalWordRequest in = request.asWordRequest().normalized();
        VocabDeck deck = ownDeckForUpdate(userId, deckId);
        if (request.groupId() != null) {
            groupService.requireGroupForBody(deckId, request.groupId());
        }
        if (deck.isNotebookDeck()) {
            LearnedWordResponse created = personalWordService.create(userId, in);
            VocabCard card = cardRepository.findById(created.cardId()).orElseThrow();
            card.setGroupId(request.groupId());
            return CardResponse.from(cardRepository.save(card)).withUserVocabId(created.userVocabId());
        }

        if (cardRepository.normalizedWordsInDeck(deckId).contains(normalize(in.word()))) {
            throw ApiException.conflict(MSG_DUPLICATE_IN_DECK);
        }
        deckRepository.findByOwnerIdForUpdate(userId); // the card limit spans all decks of the user
        boolean premium = premiumClient.isPremium(userId.toString());
        long used = cardRepository.countCustomCards(userId);
        if (used + 1 > VocabLimits.maxCards(premium)) {
            throw ApiException.badRequest(VocabLimits.cardLimitMessage(premium, VocabLimits.maxCards(premium) - used));
        }
        VocabCard card = VocabCard.builder().deck(deck).groupId(request.groupId()).build();
        apply(card, in);
        card = cardRepository.save(card);
        deck.setCardCount((int) cardRepository.countByDeck_Id(deckId));
        deckRepository.save(deck);
        cacheEvictor.deckChanged(deckId, userId);
        return CardResponse.from(card);
    }

    @Transactional
    public CardResponse updateCard(UUID userId, UUID cardId, PersonalCardRequest request) {
        if (request == null) throw ApiException.badRequest("Dữ liệu không hợp lệ");
        CreatePersonalWordRequest in = request.asWordRequest().normalized();
        VocabCard card = ownCard(userId, cardId);
        UUID deckId = card.getDeck().getId();
        deckRepository.findByIdForUpdate(deckId);
        if (request.groupId() != null) {
            groupService.requireGroupForBody(deckId, request.groupId());
        }
        String word = normalize(in.word());
        if (cardRepository.existsWordInDeckExcluding(deckId, word, cardId)) {
            throw ApiException.conflict(MSG_DUPLICATE_IN_DECK);
        }
        if (card.getDeck().isNotebookDeck()) {
            userVocabRepository.findByUserIdAndCard_Id(userId, cardId).ifPresent(uv -> {
                if (userVocabRepository.existsWordExcluding(userId, word, uv.getId())) {
                    throw ApiException.conflict("Từ này đã có trong sổ tay");
                }
            });
        }
        apply(card, in);
        card.setGroupId(request.groupId());
        card = cardRepository.save(card);
        cacheEvictor.deckChanged(deckId, userId);
        return CardResponse.from(card);
    }

    /** Deletes a card of a personal deck (its user_vocab row goes with it via ON DELETE CASCADE). */
    @Transactional
    public void deleteCard(UUID userId, UUID cardId) {
        VocabCard card = ownCard(userId, cardId);
        UUID deckId = card.getDeck().getId();
        VocabDeck deck = deckRepository.findByIdForUpdate(deckId).orElseThrow(DeckAccess::notFound);
        cardRepository.delete(card);
        cardRepository.flush();
        deck.setCardCount((int) cardRepository.countByDeck_Id(deckId));
        deckRepository.save(deck);
        cacheEvictor.deckChanged(deckId, userId);
    }

    private VocabDeck ownDeckForUpdate(UUID userId, UUID deckId) {
        VocabDeck deck = deckRepository.findByIdForUpdate(deckId).orElseThrow(DeckAccess::notFound);
        if (!deck.isOwnedBy(userId)) {
            throw DeckAccess.notFound();
        }
        return deck;
    }

    private VocabCard ownCard(UUID userId, UUID cardId) {
        return cardRepository.findWithDeck(cardId)
                .filter(c -> c.getDeck() != null && c.getDeck().isOwnedBy(userId))
                .orElseThrow(() -> ApiException.notFound(MSG_CARD_NOT_FOUND));
    }

    /** Locks the user's notebook row (creating it if needed): one place where all their deck creations queue. */
    private void lockOwner(UUID userId) {
        if (deckRepository.findByOwnerIdForUpdate(userId).isEmpty()) {
            deckRepository.insertPersonalDeckIfAbsent(userId, PersonalWordService.PERSONAL_DECK_NAME);
            deckRepository.findByOwnerIdForUpdate(userId);
        }
    }

    static void apply(VocabCard card, CreatePersonalWordRequest in) {
        card.setWord(in.word());
        card.setMeaning(in.meaning());
        card.setIpa(in.ipa());
        card.setPartOfSpeech(in.partOfSpeech());
        card.setExample(in.example());
        card.setExampleVi(in.exampleVi());
    }

    static String normalize(String word) {
        return word.trim().toLowerCase(Locale.ROOT);
    }

    static String cleanDeckName(String raw) {
        String name = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) throw ApiException.badRequest("Tên bộ thẻ không được để trống");
        if (name.length() > VocabLimits.DECK_NAME_MAX) {
            throw ApiException.badRequest("Tên bộ thẻ tối đa %d ký tự".formatted(VocabLimits.DECK_NAME_MAX));
        }
        return name;
    }

    static String cleanDescription(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String d = raw.trim();
        if (d.length() > VocabLimits.DECK_DESCRIPTION_MAX) {
            throw ApiException.badRequest("Mô tả tối đa %d ký tự".formatted(VocabLimits.DECK_DESCRIPTION_MAX));
        }
        return d;
    }
}
