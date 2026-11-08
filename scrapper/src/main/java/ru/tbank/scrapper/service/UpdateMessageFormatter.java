package ru.tbank.scrapper.service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import ru.tbank.scrapper.model.ContentUpdate;

@Component
public class UpdateMessageFormatter {

    public static final int PREVIEW_LENGTH = 200;

    private static final DateTimeFormatter TIME_FORMAT =
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneOffset.UTC);

    public String format(ContentUpdate update) {
        String header;
        String titleLabel;
        String textLabel;
        switch (update.type()) {
            case ISSUE -> {
                header = "Новый Issue";
                titleLabel = "Название";
                textLabel = "Описание";
            }
            case PULL_REQUEST -> {
                header = "Новый Pull Request";
                titleLabel = "Название";
                textLabel = "Описание";
            }
            case ANSWER -> {
                header = "Новый ответ на вопрос";
                titleLabel = "Вопрос";
                textLabel = "Ответ";
            }
            default -> {
                header = "Новый комментарий к вопросу";
                titleLabel = "Вопрос";
                textLabel = "Комментарий";
            }
        }

        return header + "\n"
            + titleLabel + ": " + valueOrDash(update.title()) + "\n"
            + "Автор: " + valueOrDash(update.author()) + "\n"
            + "Создан: " + TIME_FORMAT.format(update.createdAt()) + " (UTC)\n"
            + textLabel + ": " + valueOrDash(makePreview(update.text()));
    }

    public static String makePreview(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        if (oneLine.length() <= PREVIEW_LENGTH) {
            return oneLine;
        }
        return oneLine.substring(0, PREVIEW_LENGTH);
    }

    private String valueOrDash(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value;
    }
}
