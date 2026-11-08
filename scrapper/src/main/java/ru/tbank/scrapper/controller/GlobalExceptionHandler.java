package ru.tbank.scrapper.controller;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.tbank.common.dto.ApiErrorResponse;
import ru.tbank.scrapper.exception.ScrapperException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ScrapperException.class)
    public ResponseEntity<ApiErrorResponse> handleScrapperException(ScrapperException e) {
        log.atInfo()
            .addKeyValue("status", e.getStatus().value())
            .addKeyValue("reason", e.getMessage())
            .log("Запрос отклонён");
        return buildResponse(e.getStatus(), e.getMessage(), e);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
        log.atWarn()
            .addKeyValue("reason", e.getMostSpecificCause().getMessage())
            .log("Нарушено ограничение БД");
        return buildResponse(HttpStatus.CONFLICT, "Запись уже существует или нарушена связь данных", e);
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        MissingRequestHeaderException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception e) {
        log.atInfo()
            .addKeyValue("exception", e.getClass().getSimpleName())
            .log("Некорректный запрос");
        return buildResponse(HttpStatus.BAD_REQUEST, "Некорректные параметры запроса", e);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleOther(Exception e) {
        log.atError().setCause(e).log("Непредвиденная ошибка");
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера", e);
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(HttpStatus status, String description, Exception e) {
        List<String> stacktrace = new ArrayList<>();
        for (StackTraceElement element : e.getStackTrace()) {
            stacktrace.add(element.toString());
        }
        ApiErrorResponse body = new ApiErrorResponse(
            description,
            String.valueOf(status.value()),
            e.getClass().getSimpleName(),
            e.getMessage(),
            stacktrace
        );
        return ResponseEntity.status(status).body(body);
    }
}
