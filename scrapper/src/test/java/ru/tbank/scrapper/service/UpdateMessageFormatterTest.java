package ru.tbank.scrapper.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import ru.tbank.scrapper.model.ContentUpdate;
import ru.tbank.scrapper.model.UpdateType;

class UpdateMessageFormatterTest {

    private static final Instant CREATED = Instant.parse("2026-11-07T10:05:00Z");

    private final UpdateMessageFormatter formatter = new UpdateMessageFormatter();

    @Test
    void githubIssueMessageShouldContainTitleAuthorTimeAndPreview() {
        ContentUpdate issue = new ContentUpdate(UpdateType.ISSUE, "Падает сборка", "octocat", CREATED,
            "После обновления\nне собирается");

        assertThat(formatter.format(issue)).isEqualTo("""
            Новый Issue
            Название: Падает сборка
            Автор: octocat
            Создан: 07.11.2026 10:05 (UTC)
            Описание: После обновления не собирается""");
    }

    @Test
    void githubPullRequestMessageShouldSayPullRequest() {
        ContentUpdate pr = new ContentUpdate(UpdateType.PULL_REQUEST, "Добавить тесты", "dev", CREATED, "Тесты");

        String message = formatter.format(pr);

        assertThat(message).startsWith("Новый Pull Request\n");
        assertThat(message).contains("Название: Добавить тесты", "Автор: dev", "Описание: Тесты");
    }

    @Test
    void stackOverflowAnswerMessageShouldContainQuestionTitle() {
        ContentUpdate answer = new ContentUpdate(UpdateType.ANSWER, "Как работает HashMap?", "Jon Skeet", CREATED,
            "Используйте equals и hashCode");

        assertThat(formatter.format(answer)).isEqualTo("""
            Новый ответ на вопрос
            Вопрос: Как работает HashMap?
            Автор: Jon Skeet
            Создан: 07.11.2026 10:05 (UTC)
            Ответ: Используйте equals и hashCode""");
    }

    @Test
    void stackOverflowCommentMessageShouldSayComment() {
        ContentUpdate comment = new ContentUpdate(UpdateType.COMMENT, "Как работает HashMap?", "user1", CREATED,
            "Уточните версию");

        String message = formatter.format(comment);

        assertThat(message).startsWith("Новый комментарий к вопросу\n");
        assertThat(message).contains("Вопрос: Как работает HashMap?", "Автор: user1", "Комментарий: Уточните версию");
    }

    @Test
    void longTextShouldBeCutTo200Characters() {
        String longText = "а".repeat(150) + "б".repeat(150);
        ContentUpdate issue = new ContentUpdate(UpdateType.ISSUE, "Issue", "a", CREATED, longText);

        String preview = UpdateMessageFormatter.makePreview(longText);

        assertThat(preview).hasSize(200).isEqualTo("а".repeat(150) + "б".repeat(50));
        assertThat(formatter.format(issue)).endsWith("Описание: " + preview);
    }

    @Test
    void textOf200CharactersShouldNotBeCut() {
        String text = "x".repeat(200);

        assertThat(UpdateMessageFormatter.makePreview(text)).isEqualTo(text);
    }

    @Test
    void emptyDescriptionShouldBeShownAsDash() {
        ContentUpdate pr = new ContentUpdate(UpdateType.PULL_REQUEST, "PR без описания", "dev", CREATED, null);

        assertThat(formatter.format(pr)).endsWith("Описание: -");
    }
}
