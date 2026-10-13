package com.parroto.content.service.storage;

import com.parroto.content.exception.ApiException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;

/** A single, satisfiable byte range (inclusive bounds) parsed from an HTTP {@code Range} header. */
public record ByteRange(long start, long end) {

    public long length() {
        return end - start + 1;
    }

    public String contentRange(long total) {
        return "bytes %d-%d/%d".formatted(start, end, total);
    }

    /**
     * Parses {@code bytes=S-E}, {@code bytes=S-} and {@code bytes=-N} (RFC 7233).
     *
     * @return {@code null} when the header is absent, malformed or multi-range (caller serves the full body)
     * @throws ApiException 416 when the range cannot be satisfied for a resource of {@code total} bytes
     */
    public static ByteRange parse(String header, long total) {
        if (header == null) {
            return null;
        }
        String value = header.trim();
        if (!value.startsWith("bytes=") || value.contains(",")) {
            return null;
        }
        String spec = value.substring("bytes=".length()).trim();
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return null;
        }
        String first = spec.substring(0, dash).trim();
        String last = spec.substring(dash + 1).trim();
        try {
            if (first.isEmpty()) {
                if (last.isEmpty()) {
                    return null;
                }
                long suffix = Long.parseLong(last);
                if (suffix <= 0 || total == 0) {
                    throw unsatisfiable();
                }
                return new ByteRange(Math.max(0, total - suffix), total - 1);
            }
            long start = Long.parseLong(first);
            if (start < 0) {
                return null;
            }
            if (last.isEmpty()) {
                if (start >= total) {
                    throw unsatisfiable();
                }
                return new ByteRange(start, total - 1);
            }
            long end = Long.parseLong(last);
            if (end < start) {
                return null;
            }
            if (start >= total) {
                throw unsatisfiable();
            }
            return new ByteRange(start, Math.min(end, total - 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Parses the header; on 416 also sets {@code Content-Range: bytes *&#47;total} before rethrowing. */
    public static ByteRange parse(String header, long total, HttpServletResponse response) {
        try {
            return parse(header, total);
        } catch (ApiException e) {
            response.setHeader("Content-Range", "bytes */" + total);
            throw e;
        }
    }

    private static ApiException unsatisfiable() {
        return new ApiException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "Range không hợp lệ");
    }
}
