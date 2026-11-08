package ru.tbank.bot.controller;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.tbank.common.dto.ApiErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
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
