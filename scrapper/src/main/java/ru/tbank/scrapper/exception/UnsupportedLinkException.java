package ru.tbank.scrapper.exception;

import org.springframework.http.HttpStatus;

public class UnsupportedLinkException extends ScrapperException {

    public UnsupportedLinkException(String url) {
        super(HttpStatus.BAD_REQUEST,
            "Ссылка " + url + " не поддерживается (нужен репозиторий GitHub или вопрос StackOverflow)");
    }
}
