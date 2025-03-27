package com.example.application.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for the application.
 * This class centralizes exception handling across all controllers
 * and provides consistent error responses.  It also includes
 * handling for security-related exceptions and uses the modern
 * Date/Time API.  A utility method is used to reduce repetition
 * in creating ErrorDetails objects.
 *
 * To add new exception mappings, simply add a new @ExceptionHandler
 * method to this class, specifying the exception type to handle.
 * Ensure the documentation is updated accordingly.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Utility method to create an ErrorDetails object.
     *
     * @param timestamp   The timestamp of the error.
     * @param message     The error message.
     * @param description The request description.
     * @param errorCode   The error code.
     * @return An ErrorDetails object.
     */
    private ErrorDetails buildErrorDetails(Instant timestamp, String message, String description, String errorCode) {
        return new ErrorDetails(timestamp, message, description, errorCode);
    }

    /**
     * Handles general exceptions that are not specifically handled elsewhere.
     *
     * @param ex      The exception that was thrown.
     * @param request The web request during which the exception was thrown.
     * @return ResponseEntity with error details.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDetails> handleGlobalException(Exception ex, WebRequest request) {
        logger.error("Unhandled exception occurred", ex);
        ErrorDetails errorDetails = buildErrorDetails(
                Instant.now(),
                ex.getMessage(),
                request.getDescription(false),
                "INTERNAL_SERVER_ERROR");
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handles SQL exceptions, which typically indicate database issues.
     *
     * @param ex      The SQL exception that was thrown.
     * @param request The web request during which the exception was thrown.
     * @return ResponseEntity with error details.
     */
    @ExceptionHandler(SQLException.class)
    public ResponseEntity<ErrorDetails> handleSQLException(SQLException ex, WebRequest request) {
        logger.error("SQL exception occurred", ex);

        String errorMessage = ex.getMessage();
        String errorCode = "SQL_ERROR";

        // Map specific Oracle error codes to user-friendly messages.  Consider externalizing these mappings
        // to a configuration file or database if they are likely to change frequently or need to be localized.
        if (errorMessage.contains("ORA-03113")) {
            errorMessage = "Se ha perdido la conexión con la base de datos.";
            errorCode = "DB_CONNECTION_LOST";
        } else if (errorMessage.contains("ORA-03114")) {
            errorMessage = "Se han perdido sus credenciales inicie sesión nuevamente.";
            errorCode = "CREDENTIALS_LOST";
        } else if (errorMessage.contains("ORA-04068") || errorMessage.contains("ORA-04061")) {
            errorMessage = "El paquete ha sufrido cambios, vuelva iniciar sesión nuevamente.";
            errorCode = "PACKAGE_CHANGED";
        }

        ErrorDetails errorDetails = buildErrorDetails(
                Instant.now(),
                errorMessage,
                request.getDescription(false),
                errorCode);
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handles ResourceNotFoundException, which is thrown when a requested resource is not found.
     *
     * @param ex      The ResourceNotFoundException that was thrown.
     * @param request The web request during which the exception was thrown.
     * @return ResponseEntity with error details.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorDetails> handleResourceNotFoundException(ResourceNotFoundException ex, WebRequest request) {
        logger.warn("Resource not found", ex);
        ErrorDetails errorDetails = buildErrorDetails(
                Instant.now(),
                ex.getMessage(),
                request.getDescription(false),
                "RESOURCE_NOT_FOUND");
        return new ResponseEntity<>(errorDetails, HttpStatus.NOT_FOUND);
    }

    /**
     * Handles BusinessException, which is thrown when a business rule is violated.
     *
     * @param ex      The BusinessException that was thrown.
     * @param request The web request during which the exception was thrown.
     * @return ResponseEntity with error details.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorDetails> handleBusinessException(BusinessException ex, WebRequest request) {
        logger.warn("Business rule violation", ex);
        ErrorDetails errorDetails = buildErrorDetails(
                Instant.now(),
                ex.getMessage(),
                request.getDescription(false),
                ex.getErrorCode());
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles validation exceptions, which occur when @Valid validation fails.
     *
     * @param ex      The MethodArgumentNotValidException that was thrown.
     * @param request The web request during which the exception was thrown.
     * @return ResponseEntity with validation error details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorDetails> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        logger.warn("Validation error", ex);

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ValidationErrorDetails validationErrorDetails = new ValidationErrorDetails(
                Instant.now(),
                "Validation failed",
                request.getDescription(false),
                "VALIDATION_ERROR",
                errors);

        return new ResponseEntity<>(validationErrorDetails, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles ReservaExceedsSumaAseguradaException, which is thrown when a reserve exceeds the insured amount.
     * This is a specific business rule from the original Oracle Forms application.
     *
     * @param ex      The ReservaExceedsSumaAseguradaException that was thrown.
     * @param request The web request during which the exception was thrown.
     * @return ResponseEntity with error details.
     */
    @ExceptionHandler(ReservaExceedsSumaAseguradaException.class)
    public ResponseEntity<ErrorDetails> handleReservaExceedsSumaAseguradaException(
            ReservaExceedsSumaAseguradaException ex, WebRequest request) {
        logger.warn("Reserva exceeds suma asegurada", ex);
        ErrorDetails errorDetails = buildErrorDetails(
                Instant.now(),
                "La Reserva que trata de crear para este siniestro excede la Suma Asegurada para esta cobertura",
                request.getDescription(false),
                "RESERVA_EXCEEDS_SUMA_ASEGURADA");
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles AjusteReservaException, which is thrown when there's an issue with adjusting a reserve.
     *
     * @param ex      The AjusteReservaException that was thrown.
     * @param request The web request during which the exception was thrown.
     * @return ResponseEntity with error details.
     */
    @ExceptionHandler(AjusteReservaException.class)
    public ResponseEntity<ErrorDetails> handleAjusteReservaException(
            AjusteReservaException ex, WebRequest request) {
        logger.warn("Ajuste reserva error", ex);
        ErrorDetails errorDetails = buildErrorDetails(
                Instant.now(),
                ex.getMessage(),
                request.getDescription(false),
                "AJUSTE_RESERVA_ERROR");
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles AccessDeniedException, which is thrown when a user tries to access a resource they don't have permission for.
     *
     * @param ex      The AccessDeniedException that was thrown.
     * @param request The web request during which the exception was thrown.
     * @return ResponseEntity with error details.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorDetails> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        logger.warn("Access denied", ex);
        ErrorDetails errorDetails = buildErrorDetails(
                Instant.now(),
                "Acceso denegado: No tiene permisos para acceder a este recurso.",
                request.getDescription(false),
                "ACCESS_DENIED");
        return new ResponseEntity<>(errorDetails, HttpStatus.FORBIDDEN);
    }

    /**
     * Handles custom AuthenticationException (if you have one), which is thrown when authentication fails.
     * Example:  @ExceptionHandler(CustomAuthenticationException.class)
     * Replace CustomAuthenticationException with your actual custom exception class if you have one.
     * If you are using Spring Security's built-in authentication, you might not need this.
     *
     * @param ex      The CustomAuthenticationException that was thrown.
     * @param request The web request during which the exception was thrown.
     * @return ResponseEntity with error details.
     */
    // Example:
    /*
    @ExceptionHandler(CustomAuthenticationException.class)
    public ResponseEntity<ErrorDetails> handleAuthenticationException(CustomAuthenticationException ex, WebRequest request) {
        logger.warn("Authentication failed", ex);
        ErrorDetails errorDetails = buildErrorDetails(
                Instant.now(),
                "Authentication failed: Invalid username or password.",
                request.getDescription(false),
                "AUTHENTICATION_FAILED");
        return new ResponseEntity<>(errorDetails, HttpStatus.UNAUTHORIZED);
    }
    */
}