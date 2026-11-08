package ru.tbank.scrapper.exception;

import org.springframework.http.HttpStatus;

public class LinkNotFoundException extends ScrapperException {

    public LinkNotFoundException(String url) {
        super(HttpStatus.NOT_FOUND, "Ссылка " + url + " не отслеживается");
    }
}
