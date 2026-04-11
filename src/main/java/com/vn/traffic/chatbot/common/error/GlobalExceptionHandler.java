package com.vn.traffic.chatbot.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private final Environment env;

    /**
     * Primary constructor — used by Spring when the bean is wired from the application context.
     */
    public GlobalExceptionHandler(Environment env) {
        this.env = env;
    }

    /**
     * No-arg constructor for standalone MockMvc tests that do not need prod-profile masking.
     * When {@code env} is null, {@link #isProdProfile()} returns {@code false} (non-prod behaviour).
     */
    public GlobalExceptionHandler() {
        this.env = null;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex,
                                                    HttpServletRequest request) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (existing, replacement) -> existing
                ));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errors", fieldErrors);
        log.warn("Validation error at {}: {}", request.getRequestURI(), fieldErrors);
        return problem;
    }

    @ExceptionHandler(AppException.class)
    public ProblemDetail handleAppException(AppException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getErrorCode().getHttpStatus());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", ex.getErrorCode().name());
        log.warn("Application error [{}] at {}: {}", ex.getErrorCode(), request.getRequestURI(), ex.getMessage());
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatchException(MethodArgumentTypeMismatchException ex,
                                                     HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errors", Map.of(ex.getName(), "Invalid value"));
        log.warn("Type mismatch at {}: {}", request.getRequestURI(), ex.getMessage());
        return problem;
    }

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail handleDataAccessException(DataAccessException ex, HttpServletRequest request) {
        String detail = isProdProfile()
                ? "Failure during data access"
                : ex.getMessage();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        log.error("Data access error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolationException(ConstraintViolationException ex,
                                                            HttpServletRequest request) {
        Map<String, String> fieldErrors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        v -> v.getPropertyPath().toString(),
                        v -> v.getMessage() != null ? v.getMessage() : "Invalid value",
                        (existing, replacement) -> existing
                ));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errors", fieldErrors);
        log.warn("Constraint violation at {}: {}", request.getRequestURI(), fieldErrors);
        return problem;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFoundException(NoResourceFoundException ex,
                                                        HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "Resource not found");
        problem.setInstance(URI.create(request.getRequestURI()));
        log.warn("No resource found at {}: {}", request.getRequestURI(), ex.getMessage());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex, HttpServletRequest request) {
        String detail = isProdProfile()
                ? "Unexpected runtime exception"
                : ex.getMessage();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        log.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return problem;
    }

    private boolean isProdProfile() {
        return env != null && env.acceptsProfiles(Profiles.of("prod"));
    }
}
