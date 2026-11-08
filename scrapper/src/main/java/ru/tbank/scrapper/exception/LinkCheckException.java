package ru.tbank.scrapper.exception;

public class LinkCheckException extends RuntimeException {

    public LinkCheckException(String message) {
        super(message);
    }

    public LinkCheckException(String message, Throwable cause) {
        super(message, cause);
    }
}
