package com.parroto.vocabulary.service;

import com.parroto.vocabulary.dto.*;
import com.parroto.vocabulary.entity.VocabCard;
import com.parroto.vocabulary.entity.VocabDeck;
import com.parroto.vocabulary.exception.ApiException;
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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DeckService {

    private static final Duration DECK_LIST_TTL = Duration.ofHours(24);
    private static final Duration DECK_CARDS_TTL = Duration.ofHours(24);

    private final VocabDeckRepository deckRepository;
    private final VocabCardRepository cardRepository;
    private final PremiumClient premiumClient;
    private final CacheService cacheService;

    public DeckService(VocabDeckRepository deckRepository, VocabCardRepository cardRepository,
                        PremiumClient premiumClient, CacheService cacheService) {
        this.deckRepository = deckRepository;
        this.cardRepository = cardRepository;
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
        if (Boolean.TRUE.equals(deck.getIsProOnly())) {
            if (principal == null || !premiumClient.isPremium(principal.userId())) {
                throw ApiException.forbidden("This vocabulary deck requires a Premium subscription");
            }
        }

        boolean cacheable = (searchWord == null || searchWord.isBlank());
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
        PageResponse<CardResponse> response = PageResponse.of(mapped, page, limit);
        if (cacheable) {
            cacheService.set(cacheKey, response, DECK_CARDS_TTL);
        }
        return response;
    }

    @Transactional
    public DeckResponse createDeck(CreateDeckRequest request) {
        VocabDeck deck = VocabDeck.builder()
                .name(request.name())
                .description(request.description())
                .level(request.level())
                .isProOnly(Boolean.TRUE.equals(request.isProOnly()))
                .cardCount(0)
                .build();
        // saveAndFlush: the response below reads @CreationTimestamp, only populated once the
        // INSERT actually executes (a plain save() only guarantees that at transaction commit).
        deck = deckRepository.saveAndFlush(deck);
        cacheService.invalidateByPattern("vocab:decks:list:*");
        return DeckResponse.from(deck);
    }

    @Transactional
    public List<CardResponse> addCards(UUID deckId, CreateCardsRequest request) {
        VocabDeck deck = deckRepository.findById(deckId)
                .orElseThrow(() -> ApiException.notFound("Deck not found"));

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
        VocabCard card = cardRepository.findById(cardId)
                .orElseThrow(() -> ApiException.notFound("Card not found"));
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

    @Transactional
    public void deleteDeck(UUID deckId) {
        VocabDeck deck = deckRepository.findById(deckId)
                .orElseThrow(() -> ApiException.notFound("Deck not found"));
        deckRepository.delete(deck); // cascades to cards via ON DELETE CASCADE
        cacheService.invalidateByPattern("vocab:deck:" + deckId + ":*");
        cacheService.invalidateByPattern("vocab:decks:list:*");
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
