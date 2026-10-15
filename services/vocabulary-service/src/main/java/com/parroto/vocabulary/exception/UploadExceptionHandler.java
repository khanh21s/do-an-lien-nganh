package com.parroto.vocabulary.exception;

import com.parroto.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * P2-C import uploads: multipart failures become 400 with a Vietnamese message instead of the
 * generic handler's 500/413. Ordered before {@link GlobalExceptionHandler}.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> tooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return bad("File vượt quá 2 MB", request);
    }

    @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ErrorResponse> badMultipart(Exception ex, HttpServletRequest request) {
        return bad("Vui lòng chọn file để nhập", request);
    }

    private static ResponseEntity<ErrorResponse> bad(String message, HttpServletRequest request) {
        Object id = request.getAttribute("requestId");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(message, HttpStatus.BAD_REQUEST.value(), id != null ? id.toString() : "unknown"));
    }
}
