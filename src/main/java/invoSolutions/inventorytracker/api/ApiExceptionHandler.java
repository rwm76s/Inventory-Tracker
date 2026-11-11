package invoSolutions.inventorytracker.api;

import invoSolutions.inventorytracker.exceptions.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

// Scoped to this package so page controllers later can render their own error views.
@RestControllerAdvice(basePackages = "invoSolutions.inventorytracker.api")
public class ApiExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    ProblemDetail notFound(ProductNotFoundException ex) { return problem(HttpStatus.NOT_FOUND, ex.getMessage()); }

    @ExceptionHandler(InvalidMovementException.class)
    ProblemDetail invalid(InvalidMovementException ex) { return problem(HttpStatus.BAD_REQUEST, ex.getMessage()); }

    // 409, not 400: the request is well-formed, it just conflicts with the current state of the ledger.
    @ExceptionHandler(InsufficientStockException.class)
    ProblemDetail insufficient(InsufficientStockException ex) { return problem(HttpStatus.CONFLICT, ex.getMessage()); }

    @ExceptionHandler(DuplicateSkuException.class)
    ProblemDetail duplicate(DuplicateSkuException ex) { return problem(HttpStatus.CONFLICT, ex.getMessage()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Request validation failed");
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        pd.setProperty("errors", errors); // field -> message, so a form can mark the right inputs
        return pd;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed or unreadable request body");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail badParam(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'");
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail); // RFC 9457 JSON, built into Spring
    }
}
