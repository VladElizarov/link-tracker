package ru.tbank.scrapper.exception;

import org.springframework.http.HttpStatus;

public class LinkAlreadyExistsException extends ScrapperException {

    public LinkAlreadyExistsException(String url) {
        super(HttpStatus.CONFLICT, "Ссылка " + url + " уже отслеживается");
    }
}
