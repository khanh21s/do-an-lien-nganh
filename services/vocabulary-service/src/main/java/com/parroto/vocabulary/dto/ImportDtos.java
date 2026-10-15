package com.parroto.vocabulary.dto;

import java.util.List;
import java.util.UUID;

/** P2-C: vocabulary import (preview without saving, then commit of the previewed rows). */
public final class ImportDtos {

    private ImportDtos() {
    }

    /** One parsed row. {@code line} is 1-based in the source text (PDF: in the extracted text). */
    public record ImportRow(
            int line,
            String word,
            String meaning,
            String ipa,
            String partOfSpeech,
            String example,
            String exampleVi,
            /** Vietnamese messages; empty = the row can be imported. */
            List<String> errors,
            /** The same word appears earlier in the file (that earlier row wins). */
            boolean duplicateInFile,
            /** The word is already in the target deck (only when the preview names a deck). */
            boolean existsInDeck
    ) {
        public boolean valid() {
            return errors == null || errors.isEmpty();
        }
    }

    /** Response of POST .../import/preview. */
    public record ImportPreviewResponse(
            String format,
            String fileName,
            int totalRows,
            int validRows,
            int invalidRows,
            int duplicateRows,
            /** Lines that were not blank / comments but could not be read as an entry. */
            int skippedLines,
            List<ImportRow> rows,
            /** File-level notes, e.g. "PDF: kết quả đọc chỉ mang tính tương đối". */
            List<String> warnings
    ) {
    }

    /** A row sent back for commit (only the data fields; validation runs again on the server). */
    public record ImportRowInput(
            String word,
            String meaning,
            String ipa,
            String partOfSpeech,
            String example,
            String exampleVi
    ) {
    }

    /** Body of POST /vocabs/decks/{deckId}/import. {@code groupId} or {@code newGroupName} (or neither). */
    public record ImportCommitRequest(
            UUID groupId,
            String newGroupName,
            List<ImportRowInput> rows
    ) {
    }

    public record ImportRowError(int index, String word, String message) {
    }

    public record ImportCommitResponse(
            UUID deckId,
            UUID groupId,
            int received,
            int imported,
            int skippedDuplicate,
            int skippedInvalid,
            List<ImportRowError> errors
    ) {
    }
}
