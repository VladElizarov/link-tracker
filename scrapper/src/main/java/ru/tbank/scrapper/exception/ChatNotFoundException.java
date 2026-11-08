package ru.tbank.scrapper.exception;

import org.springframework.http.HttpStatus;

public class ChatNotFoundException extends ScrapperException {

    public ChatNotFoundException(long chatId) {
        super(HttpStatus.NOT_FOUND, "Чат " + chatId + " не зарегистрирован");
    }
}
