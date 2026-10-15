package com.parroto.vocabulary.service.deck;

import com.parroto.common.exception.ApiException;
import com.parroto.common.security.AdminGuard;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.vocabulary.entity.VocabDeck;
import com.parroto.vocabulary.repository.VocabDeckRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * P2-C ownership rules shared by groups, card moves and import:
 * <ul>
 *   <li>personal deck: visible to and managed by its owner only; anyone else gets 404 (no leak);</li>
 *   <li>public deck: visible to everyone, managed by admins only (403 for others).</li>
 * </ul>
 */
@Component
public class DeckAccess {

    public static final String MSG_DECK_NOT_FOUND = "Không tìm thấy bộ thẻ";

    private final VocabDeckRepository deckRepository;
    private final AdminGuard adminGuard;

    public DeckAccess(VocabDeckRepository deckRepository, AdminGuard adminGuard) {
        this.deckRepository = deckRepository;
        this.adminGuard = adminGuard;
    }

    public VocabDeck requireViewable(AuthenticatedUser principal, UUID deckId) {
        VocabDeck deck = deckRepository.findById(deckId).orElseThrow(DeckAccess::notFound);
        checkViewable(principal, deck);
        return deck;
    }

    /** Loads the deck row-locked (serializes limit/duplicate checks) and checks it may be managed. */
    public VocabDeck requireManageableForUpdate(AuthenticatedUser principal, UUID deckId) {
        VocabDeck deck = deckRepository.findByIdForUpdate(deckId).orElseThrow(DeckAccess::notFound);
        checkManageable(principal, deck);
        return deck;
    }

    public VocabDeck requireManageable(AuthenticatedUser principal, UUID deckId) {
        VocabDeck deck = deckRepository.findById(deckId).orElseThrow(DeckAccess::notFound);
        checkManageable(principal, deck);
        return deck;
    }

    public boolean canManage(AuthenticatedUser principal, VocabDeck deck) {
        if (principal == null) return false;
        return deck.isPersonal() ? deck.isOwnedBy(principal.userUuid()) : adminGuard.isAdmin(principal);
    }

    void checkViewable(AuthenticatedUser principal, VocabDeck deck) {
        if (deck.isPersonal() && (principal == null || !deck.isOwnedBy(principal.userUuid()))) {
            throw notFound();
        }
    }

    void checkManageable(AuthenticatedUser principal, VocabDeck deck) {
        checkViewable(principal, deck);
        if (!deck.isPersonal()) {
            adminGuard.requireAdmin(principal);
        }
    }

    static ApiException notFound() {
        return ApiException.notFound(MSG_DECK_NOT_FOUND);
    }
}
