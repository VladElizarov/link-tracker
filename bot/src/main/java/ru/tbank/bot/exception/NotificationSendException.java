package ru.tbank.bot.exception;

public class NotificationSendException extends RuntimeException {

    public NotificationSendException(String message) {
        super(message);
    }
}
