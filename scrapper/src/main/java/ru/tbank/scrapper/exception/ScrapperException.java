package ru.tbank.scrapper.exception;

import org.springframework.http.HttpStatus;

public class ScrapperException extends RuntimeException {

    private final HttpStatus status;

    public ScrapperException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
