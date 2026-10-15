package com.parroto.vocabulary.service.importer;

import com.parroto.common.exception.ApiException;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.vocabulary.dto.ImportDtos.ImportCommitRequest;
import com.parroto.vocabulary.dto.ImportDtos.ImportCommitResponse;
import com.parroto.vocabulary.dto.ImportDtos.ImportPreviewResponse;
import com.parroto.vocabulary.dto.ImportDtos.ImportRow;
import com.parroto.vocabulary.dto.ImportDtos.ImportRowError;
import com.parroto.vocabulary.dto.ImportDtos.ImportRowInput;
import com.parroto.vocabulary.entity.UserVocab;
import com.parroto.vocabulary.entity.VocabCard;
import com.parroto.vocabulary.entity.VocabDeck;
import com.parroto.vocabulary.entity.VocabGroup;
import com.parroto.vocabulary.repository.UserVocabRepository;
import com.parroto.vocabulary.repository.VocabCardRepository;
import com.parroto.vocabulary.repository.VocabDeckRepository;
import com.parroto.vocabulary.service.PremiumClient;
import com.parroto.vocabulary.service.deck.DeckAccess;
import com.parroto.vocabulary.service.deck.DeckCacheEvictor;
import com.parroto.vocabulary.service.deck.DeckGroupService;
import com.parroto.vocabulary.service.deck.VocabLimits;
import com.parroto.vocabulary.service.importer.ImportRowValidator.Checked;
import com.parroto.vocabulary.service.importer.VocabFileParser.Format;
import com.parroto.vocabulary.service.importer.VocabFileParser.ParseResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * P2-C import: {@link #preview} parses a CSV/TXT/PDF upload and returns rows + per-row errors
 * without saving; {@link #commit} re-validates the rows the client sends back and inserts them
 * into a deck (and group), skipping words already in the deck.
 */
@Service
public class VocabImportService {

    private final DeckAccess deckAccess;
    private final DeckGroupService groupService;
    private final VocabDeckRepository deckRepository;
    private final VocabCardRepository cardRepository;
    private final UserVocabRepository userVocabRepository;
    private final PremiumClient premiumClient;
    private final DeckCacheEvictor cacheEvictor;

    public VocabImportService(DeckAccess deckAccess, DeckGroupService groupService,
                              VocabDeckRepository deckRepository, VocabCardRepository cardRepository,
                              UserVocabRepository userVocabRepository, PremiumClient premiumClient,
                              DeckCacheEvictor cacheEvictor) {
        this.deckAccess = deckAccess;
        this.groupService = groupService;
        this.deckRepository = deckRepository;
        this.cardRepository = cardRepository;
        this.userVocabRepository = userVocabRepository;
        this.premiumClient = premiumClient;
        this.cacheEvictor = cacheEvictor;
    }

    @Transactional(readOnly = true)
    public ImportPreviewResponse preview(AuthenticatedUser principal, MultipartFile file, UUID deckId) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Vui lòng chọn file để nhập");
        }
        if (file.getSize() > VocabLimits.IMPORT_MAX_BYTES) {
            throw ApiException.badRequest(fileTooLargeMessage());
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("Không đọc được file");
        }
        List<String> existing = List.of();
        if (deckId != null) {
            VocabDeck deck = deckAccess.requireManageable(principal, deckId);
            existing = existingWords(deck);
        }
        Format format = VocabFileParser.detect(file.getOriginalFilename(), bytes);
        ParseResult parsed = VocabFileParser.parse(format, bytes);
        if (parsed.entries().size() > VocabLimits.IMPORT_MAX_ROWS) {
            throw ApiException.badRequest(tooManyRowsMessage(parsed.entries().size()));
        }
        if (parsed.entries().isEmpty()) {
            throw ApiException.badRequest("Không tìm thấy dòng từ vựng nào trong file");
        }
        List<ImportRow> rows = ImportRowValidator.toPreviewRows(parsed.entries(), existing);
        int valid = (int) rows.stream().filter(r -> r.valid() && !r.duplicateInFile() && !r.existsInDeck()).count();
        int invalid = (int) rows.stream().filter(r -> !r.valid()).count();
        int duplicates = (int) rows.stream().filter(r -> r.valid() && (r.duplicateInFile() || r.existsInDeck()))
                .count();
        return new ImportPreviewResponse(format.name().toLowerCase(Locale.ROOT), file.getOriginalFilename(),
                rows.size(), valid, invalid, duplicates, parsed.skippedLines(), rows, parsed.warnings());
    }

    @Transactional
    public ImportCommitResponse commit(AuthenticatedUser principal, UUID deckId, ImportCommitRequest request) {
        VocabDeck deck = deckAccess.requireManageableForUpdate(principal, deckId);
        List<ImportRowInput> rows = request == null || request.rows() == null ? List.of() : request.rows();
        if (rows.isEmpty()) {
            throw ApiException.badRequest("Không có dòng nào để nhập");
        }
        if (rows.size() > VocabLimits.IMPORT_MAX_ROWS) {
            throw ApiException.badRequest(tooManyRowsMessage(rows.size()));
        }
        UUID groupId = null;
        if (request.groupId() != null) {
            groupId = groupService.requireGroupForBody(deckId, request.groupId()).getId();
        } else if (request.newGroupName() != null && !request.newGroupName().isBlank()) {
            VocabGroup group = groupService.findOrCreate(deckId, request.newGroupName());
            groupId = group.getId();
        }

        Set<String> taken = new HashSet<>(existingWords(deck));
        List<Checked> toInsert = new ArrayList<>();
        List<ImportRowError> errors = new ArrayList<>();
        int skippedDuplicate = 0;
        for (int i = 0; i < rows.size(); i++) {
            Checked c = ImportRowValidator.check(rows.get(i));
            if (!c.valid()) {
                if (errors.size() < 200) errors.add(new ImportRowError(i, c.word(), String.join("; ", c.errors())));
                continue;
            }
            if (!taken.add(c.key())) {
                skippedDuplicate++;
                continue;
            }
            toInsert.add(c);
        }
        int skippedInvalid = rows.size() - toInsert.size() - skippedDuplicate;

        enforceLimits(deck, toInsert.size());

        List<VocabCard> cards = new ArrayList<>(toInsert.size());
        for (Checked c : toInsert) {
            cards.add(VocabCard.builder().deck(deck).groupId(groupId).word(c.word()).meaning(c.meaning())
                    .ipa(c.ipa()).partOfSpeech(c.partOfSpeech()).example(c.example()).exampleVi(c.exampleVi())
                    .build());
        }
        List<VocabCard> saved = cardRepository.saveAll(cards);
        if (deck.isNotebookDeck() && !saved.isEmpty()) {
            // Notebook rule (T13): each word is scheduled for review at once.
            OffsetDateTime now = OffsetDateTime.now();
            List<UserVocab> schedule = new ArrayList<>(saved.size());
            for (VocabCard card : saved) {
                schedule.add(UserVocab.builder().userId(deck.getOwnerId()).card(card).nextReviewAt(now)
                        .intervalDays(1).easeFactor(BigDecimal.valueOf(2.5)).repetitions(0).build());
            }
            userVocabRepository.saveAll(schedule);
        }
        cardRepository.flush();
        deck.setCardCount((int) cardRepository.countByDeck_Id(deckId));
        deckRepository.save(deck);
        cacheEvictor.deckChanged(deckId, deck.getOwnerId());
        return new ImportCommitResponse(deckId, groupId, rows.size(), saved.size(), skippedDuplicate, skippedInvalid,
                errors);
    }

    private List<String> existingWords(VocabDeck deck) {
        List<String> words = new ArrayList<>(cardRepository.normalizedWordsInDeck(deck.getId()));
        if (deck.isNotebookDeck()) {
            // The notebook holds one entry per word across every deck the user studies.
            words.addAll(cardRepository.normalizedNotebookWords(deck.getOwnerId()));
        }
        return words;
    }

    /** Personal decks: the free/premium card limit (notebook: its own cap). Public decks: none. */
    void enforceLimits(VocabDeck deck, int adding) {
        if (!deck.isPersonal() || adding == 0) return;
        if (deck.isNotebookDeck()) {
            long used = cardRepository.countByDeck_Id(deck.getId());
            if (used + adding > VocabLimits.NOTEBOOK_MAX_CARDS) {
                throw ApiException.badRequest("Sổ tay tối đa %s từ – còn trống %s từ".formatted(
                        VocabLimits.vn(VocabLimits.NOTEBOOK_MAX_CARDS),
                        VocabLimits.vn(Math.max(0, VocabLimits.NOTEBOOK_MAX_CARDS - used))));
            }
            return;
        }
        // The limit spans all of the user's decks: queue on the notebook row like the other personal writes.
        deckRepository.findByOwnerIdForUpdate(deck.getOwnerId());
        boolean premium = premiumClient.isPremium(deck.getOwnerId().toString());
        long used = cardRepository.countCustomCards(deck.getOwnerId());
        int max = VocabLimits.maxCards(premium);
        if (used + adding > max) {
            throw ApiException.badRequest(VocabLimits.cardLimitMessage(premium, max - used));
        }
    }

    static String fileTooLargeMessage() {
        return "File vượt quá 2 MB";
    }

    static String tooManyRowsMessage(int rows) {
        return "File có %s dòng – tối đa %s dòng mỗi lần nhập".formatted(VocabLimits.vn(rows),
                VocabLimits.vn(VocabLimits.IMPORT_MAX_ROWS));
    }
}
