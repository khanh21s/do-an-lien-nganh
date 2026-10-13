package com.parroto.vocabulary.service;

import com.parroto.vocabulary.dto.*;
import com.parroto.vocabulary.entity.VocabCard;
import com.parroto.vocabulary.entity.VocabDeck;
import com.parroto.vocabulary.exception.ApiException;
import com.parroto.vocabulary.repository.UserVocabRepository;
import com.parroto.vocabulary.repository.VocabCardRepository;
import com.parroto.vocabulary.repository.VocabDeckRepository;
import com.parroto.vocabulary.security.AuthenticatedUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DeckService {

    private static final Duration DECK_LIST_TTL = Duration.ofHours(24);
    private static final Duration DECK_CARDS_TTL = Duration.ofHours(24);

    private final VocabDeckRepository deckRepository;
    private final VocabCardRepository cardRepository;
    private final UserVocabRepository userVocabRepository;
    private final PremiumClient premiumClient;
    private final CacheService cacheService;

    public DeckService(VocabDeckRepository deckRepository, VocabCardRepository cardRepository,
                        UserVocabRepository userVocabRepository,
                        PremiumClient premiumClient, CacheService cacheService) {
        this.deckRepository = deckRepository;
        this.cardRepository = cardRepository;
        this.userVocabRepository = userVocabRepository;
        this.premiumClient = premiumClient;
        this.cacheService = cacheService;
    }

    @Transactional(readOnly = true)
    public PageResponse<DeckResponse> listDecks(String level, Boolean isProOnly, int page, int limit) {
        String cacheKey = "vocab:decks:list:%s:%s:%d:%d".formatted(level, isProOnly, page, limit);
        PageResponse<DeckResponse> cached = cacheService.get(cacheKey, PageResponse.class);
        if (cached != null) {
            return cached;
        }
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<VocabDeck> decks = deckRepository.search(level, isProOnly, pageable);
        Page<DeckResponse> mapped = decks.map(DeckResponse::from);
        PageResponse<DeckResponse> response = PageResponse.of(mapped, page, limit);
        cacheService.set(cacheKey, response, DECK_LIST_TTL);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<CardResponse> getDeckCards(AuthenticatedUser principal, UUID deckId, String searchWord, int page, int limit) {
        VocabDeck deck = deckRepository.findById(deckId)
                .orElseThrow(() -> ApiException.notFound("Deck not found"));
        // Personal decks (T13) are visible to their owner only; guests/others get 404 (no existence leak).
        if (deck.isPersonal() && (principal == null || !deck.isOwnedBy(principal.userUuid()))) {
            throw ApiException.notFound("Deck not found");
        }
        if (Boolean.TRUE.equals(deck.getIsProOnly())) {
            if (principal == null || !premiumClient.isPremium(principal.userId())) {
                throw ApiException.forbidden("This vocabulary deck requires a Premium subscription");
            }
        }

        // The shared cache holds the user-independent page; authenticated requests are enriched
        // with per-user userVocabId, so they always read cards from the DB (VOC-04).
        boolean cacheable = principal == null && (searchWord == null || searchWord.isBlank());
        String cacheKey = "vocab:deck:%s:cards:%d:%d".formatted(deckId, page, limit);
        if (cacheable) {
            PageResponse<CardResponse> cached = cacheService.get(cacheKey, PageResponse.class);
            if (cached != null) {
                return cached;
            }
        }

        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), limit);
        Page<VocabCard> cards = cardRepository.findByDeck(deckId, blankToNull(searchWord), pageable);
        Page<CardResponse> mapped = cards.map(CardResponse::from);
        if (principal != null && !mapped.isEmpty()) {
            Map<UUID, UUID> learned = learnedCardIds(principal.userUuid(),
                    mapped.getContent().stream().map(CardResponse::id).toList());
            mapped = mapped.map(c -> c.withUserVocabId(learned.get(c.id())));
        }
        PageResponse<CardResponse> response = PageResponse.of(mapped, page, limit);
        if (cacheable) {
            cacheService.set(cacheKey, response, DECK_CARDS_TTL);
        }
        return response;
    }

    /** cardId -> userVocabId for the cards (among {@code cardIds}) the user already learned; one query. */
    private Map<UUID, UUID> learnedCardIds(UUID userId, List<UUID> cardIds) {
        Map<UUID, UUID> result = new HashMap<>();
        for (Object[] row : userVocabRepository.findIdsByUserIdAndCardIdIn(userId, cardIds)) {
            result.put((UUID) row[0], (UUID) row[1]);
        }
        return result;
    }

    @Transactional
    public DeckResponse createDeck(CreateDeckRequest request, UUID createdBy) {
        VocabDeck deck = VocabDeck.builder()
                .name(request.name())
                .description(request.description())
                .level(request.level())
                .isProOnly(Boolean.TRUE.equals(request.isProOnly()))
                .cardCount(0)
                .createdBy(createdBy)
                .build();
        // saveAndFlush: the response below reads @CreationTimestamp, only populated once the
        // INSERT actually executes (a plain save() only guarantees that at transaction commit).
        deck = deckRepository.saveAndFlush(deck);
        cacheService.invalidateByPattern("vocab:decks:list:*");
        return DeckResponse.from(deck);
    }

    static final int DECK_NAME_MAX = 255;
    private static final Set<String> LEVELS = Set.of("A1", "A2", "B1", "B2", "C1", "C2");

    /**
     * T14-VOC: replaces name/description/level/isProOnly of a public deck. Personal decks (T13)
     * are reported as missing. Invalidates every cached deck listing ({@code vocab:decks:*}).
     */
    @Transactional
    public DeckResponse updateDeck(UUID deckId, UpdateDeckRequest request) {
        if (request == null) {
            throw ApiException.badRequest("Dữ liệu không hợp lệ");
        }
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) {
            throw ApiException.badRequest("Tên deck không được để trống");
        }
        if (name.length() > DECK_NAME_MAX) {
            throw ApiException.badRequest("Tên deck tối đa %d ký tự".formatted(DECK_NAME_MAX));
        }
        String level = blankToNull(request.level());
        if (level != null) {
            level = level.trim().toUpperCase(Locale.ROOT);
            if (!LEVELS.contains(level)) {
                throw ApiException.badRequest("Trình độ phải là một trong A1, A2, B1, B2, C1, C2");
            }
        }

        VocabDeck deck = deckRepository.findById(deckId)
                .filter(d -> !d.isPersonal())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy deck"));
        deck.setName(name);
        deck.setDescription(blankToNull(request.description()));
        deck.setLevel(level);
        deck.setIsProOnly(Boolean.TRUE.equals(request.isProOnly()));
        deck = deckRepository.saveAndFlush(deck);

        cacheService.invalidateByPattern("vocab:decks:*");
        return DeckResponse.from(deck);
    }

    @Transactional
    public List<CardResponse> addCards(UUID deckId, CreateCardsRequest request) {
        VocabDeck deck = findPublicDeck(deckId);

        for (CardInput input : request.cards()) {
            if (cardRepository.existsByDeck_IdAndWordIgnoreCase(deckId, input.word())) {
                throw ApiException.conflict("Word '%s' already exists in this deck".formatted(input.word()));
            }
        }

        List<VocabCard> toSave = request.cards().stream()
                .map(input -> VocabCard.builder()
                        .deck(deck)
                        .word(input.word())
                        .meaning(input.meaning())
                        .example(input.example())
                        .exampleVi(input.exampleVi())
                        .ipa(input.ipa())
                        .audioUrl(input.audioUrl())
                        .imageUrl(input.imageUrl())
                        .partOfSpeech(input.partOfSpeech())
                        .build())
                .collect(Collectors.toList());
        List<VocabCard> saved = cardRepository.saveAll(toSave);

        deck.setCardCount((int) cardRepository.countByDeck_Id(deckId));
        deckRepository.save(deck);

        cacheService.invalidateByPattern("vocab:deck:" + deckId + ":*");
        cacheService.invalidateByPattern("vocab:decks:list:*");
        return saved.stream().map(CardResponse::from).collect(Collectors.toList());
    }

    @Transactional
    public CardResponse updateCard(UUID cardId, UpdateCardRequest request) {
        VocabCard card = findPublicCard(cardId);
        if (request.meaning() != null) card.setMeaning(request.meaning());
        if (request.example() != null) card.setExample(request.example());
        if (request.exampleVi() != null) card.setExampleVi(request.exampleVi());
        if (request.ipa() != null) card.setIpa(request.ipa());
        if (request.audioUrl() != null) card.setAudioUrl(request.audioUrl());
        if (request.imageUrl() != null) card.setImageUrl(request.imageUrl());
        if (request.partOfSpeech() != null) card.setPartOfSpeech(request.partOfSpeech());
        card = cardRepository.save(card);
        cacheService.invalidateByPattern("vocab:deck:" + card.getDeck().getId() + ":*");
        return CardResponse.from(card);
    }

    /**
     * ADM-05: deletes a card (user_vocab rows go via ON DELETE CASCADE) and decrements the
     * deck's card_count in the same transaction.
     */
    @Transactional
    public void deleteCard(UUID cardId) {
        VocabCard card = findPublicCard(cardId);
        VocabDeck deck = card.getDeck();
        UUID deckId = deck.getId();
        cardRepository.delete(card);
        cardRepository.flush();
        deck.setCardCount((int) cardRepository.countByDeck_Id(deckId));
        deckRepository.save(deck);
        cacheService.invalidateByPattern("vocab:deck:" + deckId + ":*");
        cacheService.invalidateByPattern("vocab:decks:list:*");
    }

    @Transactional
    public void deleteDeck(UUID deckId) {
        VocabDeck deck = findPublicDeck(deckId);
        deckRepository.delete(deck); // cascades to cards via ON DELETE CASCADE
        cacheService.invalidateByPattern("vocab:deck:" + deckId + ":*");
        cacheService.invalidateByPattern("vocab:decks:list:*");
    }

    /** Admin endpoints never touch personal decks (T13): they behave as if the deck did not exist. */
    private VocabDeck findPublicDeck(UUID deckId) {
        return deckRepository.findById(deckId)
                .filter(d -> !d.isPersonal())
                .orElseThrow(() -> ApiException.notFound("Deck not found"));
    }

    private VocabCard findPublicCard(UUID cardId) {
        return cardRepository.findById(cardId)
                .filter(c -> c.getDeck() == null || !c.getDeck().isPersonal())
                .orElseThrow(() -> ApiException.notFound("Card not found"));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
