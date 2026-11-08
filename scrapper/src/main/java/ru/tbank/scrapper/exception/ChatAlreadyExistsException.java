package ru.tbank.scrapper.exception;

import org.springframework.http.HttpStatus;

public class ChatAlreadyExistsException extends ScrapperException {

    public ChatAlreadyExistsException(long chatId) {
        super(HttpStatus.CONFLICT, "Чат " + chatId + " уже зарегистрирован");
    }
}
