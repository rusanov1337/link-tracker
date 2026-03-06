package backend.academy.linktracker.scrapper.api;

import backend.academy.linktracker.scrapper.api.dto.ApiErrorResponse;
import backend.academy.linktracker.scrapper.exception.ChatAlreadyExistsException;
import backend.academy.linktracker.scrapper.exception.ChatNotFoundException;
import backend.academy.linktracker.scrapper.exception.LinkAlreadyTrackedException;
import backend.academy.linktracker.scrapper.exception.LinkNotFoundException;
import backend.academy.linktracker.scrapper.exception.UnsupportedLinkException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ScrapperApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScrapperApiExceptionHandler.class);

    @ExceptionHandler(ChatAlreadyExistsException.class)
    ResponseEntity<ApiErrorResponse> handleChatAlreadyExists(ChatAlreadyExistsException exception) {
        return errorResponse(HttpStatus.CONFLICT, exception.getMessage(), exception);
    }

    @ExceptionHandler(ChatNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleChatNotFound(ChatNotFoundException exception) {
        return errorResponse(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
    }

    @ExceptionHandler(LinkAlreadyTrackedException.class)
    ResponseEntity<ApiErrorResponse> handleLinkAlreadyTracked(LinkAlreadyTrackedException exception) {
        return errorResponse(HttpStatus.CONFLICT, exception.getMessage(), exception);
    }

    @ExceptionHandler(LinkNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleLinkNotFound(LinkNotFoundException exception) {
        return errorResponse(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
    }

    @ExceptionHandler(UnsupportedLinkException.class)
    ResponseEntity<ApiErrorResponse> handleUnsupportedLink(UnsupportedLinkException exception) {
        return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        var fields = exception.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .toList();
        var description = "Validation failed: " + String.join("; ", fields);
        return errorResponse(HttpStatus.BAD_REQUEST, description, exception);
    }

    @ExceptionHandler({
        ConstraintViolationException.class,
        MethodArgumentTypeMismatchException.class,
        MissingRequestHeaderException.class,
        HttpMessageNotReadableException.class
    })
    ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception) {
        return errorResponse(HttpStatus.BAD_REQUEST, "Invalid request parameters", exception);
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }

    private ResponseEntity<ApiErrorResponse> errorResponse(HttpStatus status, String description, Exception exception) {
        LOGGER.atWarn()
                .addKeyValue("status", status.value())
                .addKeyValue("description", description)
                .addKeyValue("exception", exception.getClass().getSimpleName())
                .log("Request failed");

        var body = new ApiErrorResponse(
                description,
                String.valueOf(status.value()),
                exception.getClass().getSimpleName(),
                exception.getMessage(),
                List.of());
        return ResponseEntity.status(status).body(body);
    }
}
