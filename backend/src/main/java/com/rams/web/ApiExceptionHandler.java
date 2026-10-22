package com.rams.web;

import jakarta.persistence.PersistenceException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException exception) {
        return error(exception.getStatus(), exception.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(fieldError ->
                fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage())
        );
        return error(HttpStatus.BAD_REQUEST, "Check the highlighted fields", fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException exception) {
        return error(HttpStatus.BAD_REQUEST, "Request body is invalid", null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return error(HttpStatus.BAD_REQUEST, "Request parameter is invalid", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException exception) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "That method is not supported", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleMissing(NoResourceFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "Not found", null);
    }

    @ExceptionHandler({DataAccessException.class, TransactionSystemException.class, PersistenceException.class})
    ResponseEntity<ApiError> handleDatabase(Exception exception, HttpServletRequest request) {
        String state = sqlState(exception);
        String message = databaseMessage(exception);
        if ("23505".equals(state)) {
            return error(HttpStatus.CONFLICT, friendlyUnique(message), null);
        }
        if (state.startsWith("22") || state.startsWith("23") || "P0001".equals(state)) {
            if (message != null && !message.isBlank()) {
                return error(HttpStatus.BAD_REQUEST, message, null);
            }
            return error(HttpStatus.BAD_REQUEST, "The request conflicts with a database rule", null);
        }
        log.error("Database error on {} {}", request.getMethod(), request.getRequestURI(), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong", null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong", null);
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), status.getReasonPhrase(), message, fields));
    }

    private static String sqlState(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
            current = current.getCause();
        }
        return "";
    }

    private static String databaseMessage(Throwable exception) {
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root.getMessage() == null) {
            return null;
        }
        String message = root.getMessage();
        int newline = message.indexOf('\n');
        if (newline >= 0) {
            message = message.substring(0, newline);
        }
        return message.replaceFirst("^ERROR:\\s*", "").trim();
    }

    private static String friendlyUnique(String message) {
        if (message == null) {
            return "That value is already in use";
        }
        String lower = message.toLowerCase();
        if (lower.contains("users_email") || lower.contains("email")) {
            return "An account with this email already exists";
        }
        if (lower.contains("project_assistants")) {
            return "This assistant is already assigned to the project";
        }
        if (lower.contains("weekly_reports")) {
            return "A weekly report already exists for that project week";
        }
        return "That value is already in use";
    }
}
