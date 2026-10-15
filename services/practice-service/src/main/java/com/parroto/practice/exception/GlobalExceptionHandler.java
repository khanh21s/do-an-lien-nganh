package com.parroto.practice.exception;

import com.parroto.common.exception.ApiException;
import com.parroto.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private String requestId(HttpServletRequest request) {
        Object id = request.getAttribute("requestId");
        return id != null ? id.toString() : "unknown";
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus())
                .body(ErrorResponse.of(ex.getMessage(), ex.getStatus().value(), requestId(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(message.isBlank() ? "Validation failed" : message,
                        HttpStatus.UNPROCESSABLE_ENTITY.value(), requestId(request)));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleBadInput(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("Tham số không hợp lệ", HttpStatus.BAD_REQUEST.value(), requestId(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        // Spring MVC's own errors (404 no handler, 405, 415, ...) keep their status instead of becoming 500.
        if (ex instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatusCode status = springError.getStatusCode();
            String detail = springError.getBody().getDetail();
            return ResponseEntity.status(status)
                    .body(ErrorResponse.of(detail != null ? detail : "Request failed", status.value(), requestId(request)));
        }
        log.error("Unhandled exception [requestId={}]", requestId(request), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR.value(), requestId(request)));
    }
}
