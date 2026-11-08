package ru.tbank.bot.command;

import ru.tbank.bot.model.IncomingMessage;

public interface Command {

    String name();

    String description();

    String handle(IncomingMessage message);
}
