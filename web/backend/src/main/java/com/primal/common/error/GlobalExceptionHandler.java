package com.primal.common.error;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Все ошибки API — в формате RFC 9457 {@code application/problem+json} с полем {@code code}
 * ({@code doc/api.md} §1.1). Ошибки валидации дополнительно содержат {@code errors: [{field, message}]}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException exception) {
        ProblemDetail problem = problem(exception.code(), exception.getMessage());
        exception.properties().forEach(problem::setProperty);
        HttpHeaders headers = new HttpHeaders();
        exception.headers().forEach(headers::add);
        return ResponseEntity.status(exception.code().status()).headers(headers).body(problem);
    }

    /** Две правки одной кампании одновременно: вторая не проходит (версия в БД уже другая). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException exception) {
        ProblemDetail problem = problem(ErrorCode.VERSION_CONFLICT,
                "Кампания только что изменена на другом устройстве. Обновите страницу и повторите.");
        return ResponseEntity.status(ErrorCode.VERSION_CONFLICT.status()).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Необработанная ошибка", exception);
        ProblemDetail problem = problem(ErrorCode.INTERNAL_ERROR, "Что-то пошло не так. Попробуйте ещё раз.");
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status()).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::fieldError)
                .toList();
        ProblemDetail problem = problem(ErrorCode.VALIDATION_FAILED, "Проверьте заполнение полей.");
        problem.setProperty("errors", errors);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status()).headers(headers).body(problem);
    }

    /** Ошибки, которые обрабатывает сам Spring MVC (404, 405, 415, неразборчивый JSON …), тоже получают {@code code}. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(exception, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            ErrorCode code = ErrorCode.forStatus(statusCode.value());
            problem.setType(code.type());
            problem.setTitle(code.title());
            problem.setProperty("code", code.name());
        }
        return response;
    }

    private static ProblemDetail problem(ErrorCode code, String detail) {
        return Problems.of(code, detail);
    }

    private static Map<String, String> fieldError(FieldError error) {
        return Map.of(
                "field", error.getField(),
                "message", error.getDefaultMessage() == null ? "Некорректное значение" : error.getDefaultMessage());
    }
}
