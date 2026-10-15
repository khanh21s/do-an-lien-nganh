package com.parroto.vocabulary.service.deck;

import com.parroto.common.cache.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * P2-C: drops the cached card pages of a deck (and the public deck lists / the owner's vocab caches)
 * after the transaction commits. Redis being down never fails a committed write.
 */
@Component
public class DeckCacheEvictor {

    private static final Logger log = LoggerFactory.getLogger(DeckCacheEvictor.class);

    private final CacheService cacheService;

    public DeckCacheEvictor(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    public void deckChanged(UUID deckId, UUID ownerId) {
        afterCommit(() -> {
            cacheService.invalidateByPattern("vocab:deck:" + deckId + ":*");
            if (ownerId == null) {
                cacheService.invalidateByPattern("vocab:decks:list:*");
            } else {
                cacheService.invalidateByPattern("vocab:user:" + ownerId + ":*");
            }
        });
    }

    private void afterCommit(Runnable action) {
        Runnable safe = () -> {
            try {
                action.run();
            } catch (DataAccessException e) {
                log.warn("Cache invalidation failed: {}", e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safe.run();
                }
            });
        } else {
            safe.run();
        }
    }
}
