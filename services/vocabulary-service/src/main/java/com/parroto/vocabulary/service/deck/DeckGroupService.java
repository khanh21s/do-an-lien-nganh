package com.parroto.vocabulary.service.deck;

import com.parroto.common.exception.ApiException;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.vocabulary.dto.MyDeckDtos.DeckDetailResponse;
import com.parroto.vocabulary.dto.MyDeckDtos.GroupOrderRequest;
import com.parroto.vocabulary.dto.MyDeckDtos.GroupRequest;
import com.parroto.vocabulary.dto.MyDeckDtos.GroupResponse;
import com.parroto.vocabulary.dto.MyDeckDtos.MoveCardsRequest;
import com.parroto.vocabulary.dto.MyDeckDtos.MoveCardsResponse;
import com.parroto.vocabulary.entity.VocabDeck;
import com.parroto.vocabulary.entity.VocabGroup;
import com.parroto.vocabulary.repository.VocabCardRepository;
import com.parroto.vocabulary.repository.VocabGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * P2-C: named, ordered groups inside a deck. Same endpoints for learners (their personal decks)
 * and admins (public decks); see {@link DeckAccess} for who may do what.
 */
@Service
public class DeckGroupService {

    static final String MSG_GROUP_NOT_FOUND = "Không tìm thấy nhóm";
    static final String MSG_GROUP_NAME_EMPTY = "Tên nhóm không được để trống";
    static final String MSG_GROUP_NAME_TAKEN = "Bộ thẻ đã có nhóm tên này";

    private final DeckAccess deckAccess;
    private final VocabGroupRepository groupRepository;
    private final VocabCardRepository cardRepository;
    private final DeckCacheEvictor cacheEvictor;

    public DeckGroupService(DeckAccess deckAccess, VocabGroupRepository groupRepository,
                            VocabCardRepository cardRepository, DeckCacheEvictor cacheEvictor) {
        this.deckAccess = deckAccess;
        this.groupRepository = groupRepository;
        this.cardRepository = cardRepository;
        this.cacheEvictor = cacheEvictor;
    }

    @Transactional(readOnly = true)
    public DeckDetailResponse detail(AuthenticatedUser principal, UUID deckId) {
        VocabDeck deck = deckAccess.requireViewable(principal, deckId);
        Map<UUID, Long> counts = new HashMap<>();
        long ungrouped = 0;
        for (Object[] row : cardRepository.countByGroup(deckId)) {
            long n = ((Number) row[1]).longValue();
            if (row[0] == null) ungrouped = n;
            else counts.put((UUID) row[0], n);
        }
        List<GroupResponse> groups = groupRepository.findByDeckIdOrderBySortOrderAscCreatedAtAsc(deckId).stream()
                .map(g -> GroupResponse.from(g, counts.getOrDefault(g.getId(), 0L)))
                .toList();
        return new DeckDetailResponse(deck.getId(), deck.getName(), deck.getDescription(), deck.getLevel(),
                Boolean.TRUE.equals(deck.getIsProOnly()), deck.getCardCount() == null ? 0 : deck.getCardCount(),
                deck.isPersonal(), deck.isNotebookDeck(), deckAccess.canManage(principal, deck), groups, ungrouped,
                deck.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> list(AuthenticatedUser principal, UUID deckId) {
        return detail(principal, deckId).groups();
    }

    @Transactional
    public GroupResponse create(AuthenticatedUser principal, UUID deckId, GroupRequest request) {
        VocabDeck deck = deckAccess.requireManageableForUpdate(principal, deckId);
        String name = cleanName(request == null ? null : request.name());
        if (groupRepository.countByDeckId(deckId) >= VocabLimits.MAX_GROUPS_PER_DECK) {
            throw ApiException.badRequest("Mỗi bộ thẻ tối đa %d nhóm".formatted(VocabLimits.MAX_GROUPS_PER_DECK));
        }
        if (groupRepository.existsName(deckId, name)) {
            throw ApiException.conflict(MSG_GROUP_NAME_TAKEN);
        }
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : groupRepository.maxSortOrder(deckId) + 1;
        VocabGroup group = groupRepository.saveAndFlush(
                VocabGroup.builder().deckId(deckId).name(name).sortOrder(sortOrder).build());
        cacheEvictor.deckChanged(deckId, deck.getOwnerId());
        return GroupResponse.from(group, 0);
    }

    /** Find-or-create by name (import into a new group). Caller holds the deck lock. */
    @Transactional
    public VocabGroup findOrCreate(UUID deckId, String rawName) {
        String name = cleanName(rawName);
        return groupRepository.findByDeckIdOrderBySortOrderAscCreatedAtAsc(deckId).stream()
                .filter(g -> g.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> {
                    if (groupRepository.countByDeckId(deckId) >= VocabLimits.MAX_GROUPS_PER_DECK) {
                        throw ApiException.badRequest(
                                "Mỗi bộ thẻ tối đa %d nhóm".formatted(VocabLimits.MAX_GROUPS_PER_DECK));
                    }
                    return groupRepository.saveAndFlush(VocabGroup.builder().deckId(deckId).name(name)
                            .sortOrder(groupRepository.maxSortOrder(deckId) + 1).build());
                });
    }

    @Transactional
    public GroupResponse update(AuthenticatedUser principal, UUID deckId, UUID groupId, GroupRequest request) {
        VocabDeck deck = deckAccess.requireManageableForUpdate(principal, deckId);
        VocabGroup group = requireGroup(deckId, groupId);
        if (request != null && request.name() != null) {
            String name = cleanName(request.name());
            if (groupRepository.existsNameExcluding(deckId, name, groupId)) {
                throw ApiException.conflict(MSG_GROUP_NAME_TAKEN);
            }
            group.setName(name);
        }
        if (request != null && request.sortOrder() != null) {
            group.setSortOrder(request.sortOrder());
        }
        group = groupRepository.saveAndFlush(group);
        cacheEvictor.deckChanged(deckId, deck.getOwnerId());
        long count = cardRepository.countByGroup(deckId).stream()
                .filter(r -> groupId.equals(r[0])).mapToLong(r -> ((Number) r[1]).longValue()).sum();
        return GroupResponse.from(group, count);
    }

    /** Sets sort_order 0..n-1 following {@code groupIds}, which must list every group of the deck once. */
    @Transactional
    public List<GroupResponse> reorder(AuthenticatedUser principal, UUID deckId, GroupOrderRequest request) {
        VocabDeck deck = deckAccess.requireManageableForUpdate(principal, deckId);
        List<VocabGroup> groups = groupRepository.findByDeckIdOrderBySortOrderAscCreatedAtAsc(deckId);
        List<UUID> order = request == null || request.groupIds() == null ? List.of() : request.groupIds();
        Set<UUID> wanted = new LinkedHashSet<>(order);
        Set<UUID> existing = new HashSet<>();
        groups.forEach(g -> existing.add(g.getId()));
        if (wanted.size() != order.size() || !wanted.equals(existing)) {
            throw ApiException.badRequest("Danh sách nhóm không khớp với bộ thẻ");
        }
        Map<UUID, VocabGroup> byId = new HashMap<>();
        groups.forEach(g -> byId.put(g.getId(), g));
        int i = 0;
        for (UUID id : order) {
            byId.get(id).setSortOrder(i++);
        }
        groupRepository.saveAllAndFlush(groups);
        cacheEvictor.deckChanged(deckId, deck.getOwnerId());
        return list(principal, deckId);
    }

    /** Deletes the group; its cards stay in the deck, ungrouped. */
    @Transactional
    public void delete(AuthenticatedUser principal, UUID deckId, UUID groupId) {
        VocabDeck deck = deckAccess.requireManageableForUpdate(principal, deckId);
        VocabGroup group = requireGroup(deckId, groupId);
        groupRepository.ungroupCards(groupId);
        groupRepository.deleteById(group.getId());
        cacheEvictor.deckChanged(deckId, deck.getOwnerId());
    }

    /** Moves cards of this deck into {@code groupId} (null = ungrouped); ids of other decks are ignored. */
    @Transactional
    public MoveCardsResponse moveCards(AuthenticatedUser principal, UUID deckId, MoveCardsRequest request) {
        VocabDeck deck = deckAccess.requireManageableForUpdate(principal, deckId);
        if (request == null || request.cardIds() == null || request.cardIds().isEmpty()) {
            throw ApiException.badRequest("Chưa chọn thẻ nào");
        }
        if (request.cardIds().size() > VocabLimits.IMPORT_MAX_ROWS) {
            throw ApiException.badRequest("Tối đa %d thẻ mỗi lần".formatted(VocabLimits.IMPORT_MAX_ROWS));
        }
        if (request.groupId() != null) {
            requireGroup(deckId, request.groupId());
        }
        int moved = groupRepository.moveCards(deckId, new HashSet<>(request.cardIds()), request.groupId());
        cacheEvictor.deckChanged(deckId, deck.getOwnerId());
        return new MoveCardsResponse(moved);
    }

    /** A group of {@code deckId}, else 400 (bad group id in a request body) — use {@link #requireGroup} for paths. */
    public VocabGroup requireGroupForBody(UUID deckId, UUID groupId) {
        return groupRepository.findByIdAndDeckId(groupId, deckId)
                .orElseThrow(() -> ApiException.badRequest("Nhóm không thuộc bộ thẻ này"));
    }

    VocabGroup requireGroup(UUID deckId, UUID groupId) {
        return groupRepository.findByIdAndDeckId(groupId, deckId)
                .orElseThrow(() -> ApiException.notFound(MSG_GROUP_NOT_FOUND));
    }

    static String cleanName(String raw) {
        String name = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw ApiException.badRequest(MSG_GROUP_NAME_EMPTY);
        }
        if (name.length() > VocabLimits.GROUP_NAME_MAX) {
            throw ApiException.badRequest("Tên nhóm tối đa %d ký tự".formatted(VocabLimits.GROUP_NAME_MAX));
        }
        return name;
    }
}
