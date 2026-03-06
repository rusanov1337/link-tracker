package backend.academy.linktracker.bot.api;

import backend.academy.linktracker.bot.api.dto.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class BotApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BotApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        var fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .toList();
        var description = "Validation failed: " + String.join("; ", fieldErrors);
        return badRequest(description, exception);
    }

    @ExceptionHandler({
        ConstraintViolationException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class
    })
    ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception) {
        return badRequest("Invalid request parameters", exception);
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }

    private ResponseEntity<ApiErrorResponse> badRequest(String description, Exception exception) {
        LOGGER.atWarn()
                .addKeyValue("status", HttpStatus.BAD_REQUEST.value())
                .addKeyValue("description", description)
                .addKeyValue("exception", exception.getClass().getSimpleName())
                .log("Request failed");

        var body = new ApiErrorResponse(
                description,
                String.valueOf(HttpStatus.BAD_REQUEST.value()),
                exception.getClass().getSimpleName(),
                exception.getMessage(),
                List.of());
        return ResponseEntity.badRequest().body(body);
    }
}
