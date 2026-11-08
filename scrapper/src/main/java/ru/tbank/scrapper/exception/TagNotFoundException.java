package ru.tbank.scrapper.exception;

import org.springframework.http.HttpStatus;

public class TagNotFoundException extends ScrapperException {

    public TagNotFoundException(String name) {
        super(HttpStatus.NOT_FOUND, "Тег " + name + " не найден");
    }
}
