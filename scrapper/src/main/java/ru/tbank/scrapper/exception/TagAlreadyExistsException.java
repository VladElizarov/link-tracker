package ru.tbank.scrapper.exception;

import org.springframework.http.HttpStatus;

public class TagAlreadyExistsException extends ScrapperException {

    public TagAlreadyExistsException(String name) {
        super(HttpStatus.CONFLICT, "Тег " + name + " уже существует");
    }
}
