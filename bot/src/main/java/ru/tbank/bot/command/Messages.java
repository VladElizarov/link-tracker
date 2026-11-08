package ru.tbank.bot.command;

public final class Messages {

    public static final String CHAT_NOT_REGISTERED =
        "Вы ещё не зарегистрированы. Отправьте /start.";
    public static final String SERVICE_UNAVAILABLE =
        "Сервис временно недоступен, попробуйте позже.";
    public static final String LINK_ALREADY_TRACKED =
        "Ссылка уже отслеживается";
    public static final String INVALID_LINK =
        "Ссылка некорректна. Поддерживаются репозитории GitHub (https://github.com/owner/repo) "
            + "и вопросы StackOverflow (https://stackoverflow.com/questions/123). "
            + "Попробуйте ещё раз или отправьте /cancel.";
    public static final String TRACK_CANCELLED =
        "Добавление ссылки отменено.";

    private Messages() {
    }
}
