package ru.tbank.bot.command;

import org.springframework.web.client.HttpClientErrorException;
import ru.tbank.bot.client.ScrapperClient;
import ru.tbank.bot.model.IncomingMessage;

public class StartCommand implements Command {

    public static final String WELCOME_MESSAGE =
        "Добро пожаловать! Используйте /help, чтобы посмотреть доступные команды.";
    public static final String ALREADY_REGISTERED_MESSAGE =
        "С возвращением! Вы уже зарегистрированы. Используйте /help, чтобы посмотреть доступные команды.";

    private final ScrapperClient scrapperClient;

    public StartCommand(ScrapperClient scrapperClient) {
        this.scrapperClient = scrapperClient;
    }

    @Override
    public String name() {
        return "/start";
    }

    @Override
    public String description() {
        return "начать работу с ботом";
    }

    @Override
    public String handle(IncomingMessage message) {
        try {
            scrapperClient.registerChat(message.chatId());
            return WELCOME_MESSAGE;
        } catch (HttpClientErrorException.Conflict e) {
            return ALREADY_REGISTERED_MESSAGE;
        }
    }
}
