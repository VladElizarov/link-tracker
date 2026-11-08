package ru.tbank.bot.config;

import com.pengrad.telegrambot.TelegramBot;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.tbank.bot.client.ScrapperClient;
import ru.tbank.bot.command.CancelCommand;
import ru.tbank.bot.command.Command;
import ru.tbank.bot.command.HelpCommand;
import ru.tbank.bot.command.ListCommand;
import ru.tbank.bot.command.StartCommand;
import ru.tbank.bot.command.TrackCommand;
import ru.tbank.bot.command.UntrackCommand;
import ru.tbank.bot.repository.DialogRepository;

@Configuration
public class TelegramConfig {

    @Bean
    public TelegramBot telegramBot(
        BotConfig config,
        @Value("${app.telegram-api-url:https://api.telegram.org/bot}") String apiUrl
    ) {
        return new TelegramBot.Builder(config.telegramToken()).apiUrl(apiUrl).build();
    }

    @Bean
    public List<Command> commands(ScrapperClient scrapperClient, DialogRepository dialogRepository) {
        return createCommands(scrapperClient, dialogRepository);
    }

    public static List<Command> createCommands(ScrapperClient scrapperClient, DialogRepository dialogRepository) {
        List<Command> commands = new ArrayList<>();
        commands.add(new StartCommand(scrapperClient));
        commands.add(new HelpCommand(commands));
        commands.add(new TrackCommand(dialogRepository));
        commands.add(new UntrackCommand(scrapperClient));
        commands.add(new ListCommand(scrapperClient));
        commands.add(new CancelCommand(dialogRepository));
        return commands;
    }
}
