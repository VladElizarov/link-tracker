package ru.tbank.bot.command;

import java.util.List;
import ru.tbank.bot.model.IncomingMessage;

public class HelpCommand implements Command {

    private final List<Command> commands;

    public HelpCommand(List<Command> commands) {
        this.commands = commands;
    }

    @Override
    public String name() {
        return "/help";
    }

    @Override
    public String description() {
        return "показать список доступных команд";
    }

    @Override
    public String handle(IncomingMessage message) {
        StringBuilder text = new StringBuilder("Доступные команды:\n");
        for (Command command : commands) {
            text.append(command.name())
                .append(" - ")
                .append(command.description())
                .append("\n");
        }
        return text.toString().trim();
    }
}
