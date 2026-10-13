package com.parroto.vocabulary.service;

import com.parroto.vocabulary.dto.CreatePersonalWordRequest;
import com.parroto.vocabulary.dto.LearnedWordResponse;
import com.parroto.vocabulary.entity.UserVocab;
import com.parroto.vocabulary.entity.VocabCard;
import com.parroto.vocabulary.entity.VocabDeck;
import com.parroto.vocabulary.exception.ApiException;
import com.parroto.vocabulary.repository.UserVocabRepository;
import com.parroto.vocabulary.repository.VocabCardRepository;
import com.parroto.vocabulary.repository.VocabDeckRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * T13: words a user adds to the notebook. Each one is a {@link VocabCard} in the user's hidden
 * personal deck ({@code vocab_deck.owner_id = userId}, created lazily) plus a {@link UserVocab}
 * due immediately, so it is reviewed with SM-2 like any other card. Also owns notebook removal.
 */
@Service
public class PersonalWordService {

    private static final Logger log = LoggerFactory.getLogger(PersonalWordService.class);

    public static final String PERSONAL_DECK_NAME = "Sổ tay cá nhân";
    public static final int MAX_PERSONAL_WORDS = 2000;

    static final String MSG_DUPLICATE = "Từ này đã có trong sổ tay";
    static final String MSG_NOT_FOUND = "Không tìm thấy từ";
    static final String MSG_NOT_CUSTOM = "Chỉ sửa được từ bạn tự thêm";
    static final String MSG_LIMIT = "Sổ tay cá nhân đã đạt giới hạn %d từ".formatted(MAX_PERSONAL_WORDS);

    private final UserVocabRepository userVocabRepository;
    private final VocabCardRepository cardRepository;
    private final VocabDeckRepository deckRepository;
    private final CacheService cacheService;

    public PersonalWordService(UserVocabRepository userVocabRepository, VocabCardRepository cardRepository,
                               VocabDeckRepository deckRepository, CacheService cacheService) {
        this.userVocabRepository = userVocabRepository;
        this.cardRepository = cardRepository;
        this.deckRepository = deckRepository;
        this.cacheService = cacheService;
    }

    @Transactional
    public LearnedWordResponse create(UUID userId, CreatePersonalWordRequest request) {
        CreatePersonalWordRequest in = request.normalized();
        // Locks the deck row: concurrent adds by the same user serialize on the checks below.
        VocabDeck deck = lockOrCreatePersonalDeck(userId);

        if (userVocabRepository.existsWord(userId, normalize(in.word()))) {
            throw ApiException.conflict(MSG_DUPLICATE);
        }
        long count = cardRepository.countByDeck_Id(deck.getId());
        if (count >= MAX_PERSONAL_WORDS) {
            throw ApiException.badRequest(MSG_LIMIT);
        }

        VocabCard card = VocabCard.builder().deck(deck).build();
        apply(card, in);
        card = cardRepository.save(card);

        UserVocab userVocab = UserVocab.builder()
                .userId(userId)
                .card(card)
                .nextReviewAt(OffsetDateTime.now()) // due now -> appears in the review queue at once
                .intervalDays(1)
                .easeFactor(BigDecimal.valueOf(2.5))
                .repetitions(0)
                .build();
        userVocab = userVocabRepository.save(userVocab);

        deck.setCardCount((int) count + 1);
        deckRepository.save(deck);

        invalidateAfterCommit(userId, deck.getId());
        return LearnedWordResponse.from(userVocab);
    }

    @Transactional
    public LearnedWordResponse update(UUID userId, UUID userVocabId, CreatePersonalWordRequest request) {
        CreatePersonalWordRequest in = request.normalized();
        UserVocab userVocab = findOwned(userId, userVocabId);
        VocabCard card = userVocab.getCard();
        if (!card.getDeck().isOwnedBy(userId)) {
            throw ApiException.forbidden(MSG_NOT_CUSTOM);
        }
        deckRepository.findByOwnerIdForUpdate(userId); // serialize with concurrent adds/edits

        if (userVocabRepository.existsWordExcluding(userId, normalize(in.word()), userVocab.getId())) {
            throw ApiException.conflict(MSG_DUPLICATE);
        }
        apply(card, in);
        cardRepository.save(card);

        invalidateAfterCommit(userId, card.getDeck().getId());
        return LearnedWordResponse.from(userVocab);
    }

    /**
     * DELETE /vocabs/user/{userVocabId}: removes the word from the notebook. A word the user added
     * also loses its card, and the personal deck's card_count is recomputed in the same transaction.
     */
    @Transactional
    public void remove(UUID userId, UUID userVocabId) {
        UserVocab userVocab = findOwned(userId, userVocabId);
        VocabCard card = userVocab.getCard();
        VocabDeck deck = card.getDeck();

        if (deck == null || !deck.isOwnedBy(userId)) {
            userVocabRepository.delete(userVocab);
            invalidateAfterCommit(userId, null);
            return;
        }
        VocabDeck locked = deckRepository.findByOwnerIdForUpdate(userId).orElse(deck);
        userVocabRepository.delete(userVocab);
        cardRepository.delete(card);
        cardRepository.flush();
        locked.setCardCount((int) cardRepository.countByDeck_Id(locked.getId()));
        deckRepository.save(locked);
        invalidateAfterCommit(userId, locked.getId());
    }

    private UserVocab findOwned(UUID userId, UUID userVocabId) {
        return userVocabRepository.findWithCardAndDeck(userVocabId)
                .filter(uv -> userId.equals(uv.getUserId()))
                .orElseThrow(() -> ApiException.notFound(MSG_NOT_FOUND));
    }

    private VocabDeck lockOrCreatePersonalDeck(UUID userId) {
        return deckRepository.findByOwnerIdForUpdate(userId).orElseGet(() -> {
            deckRepository.insertPersonalDeckIfAbsent(userId, PERSONAL_DECK_NAME);
            return deckRepository.findByOwnerIdForUpdate(userId)
                    .orElseThrow(() -> new IllegalStateException("Personal deck not found after insert"));
        });
    }

    private static void apply(VocabCard card, CreatePersonalWordRequest in) {
        card.setWord(in.word());
        card.setMeaning(in.meaning());
        card.setIpa(in.ipa());
        card.setPartOfSpeech(in.partOfSpeech());
        card.setExample(in.example());
        card.setExampleVi(in.exampleVi());
    }

    /** Same normalization as the SQL side of the duplicate check: {@code lower(trim(word))}. */
    static String normalize(String word) {
        return word.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Drops per-user vocab caches ({@code vocab:user:{userId}:*}) and the personal deck's card
     * pages once the transaction has committed. Redis being down must not fail a committed write.
     */
    private void invalidateAfterCommit(UUID userId, UUID deckId) {
        Runnable invalidate = () -> {
            try {
                cacheService.invalidateByPattern("vocab:user:" + userId + ":*");
                if (deckId != null) {
                    cacheService.invalidateByPattern("vocab:deck:" + deckId + ":*");
                }
            } catch (DataAccessException e) {
                log.warn("Cache invalidation failed for user {}: {}", userId, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidate.run();
                }
            });
        } else {
            invalidate.run();
        }
    }
}
