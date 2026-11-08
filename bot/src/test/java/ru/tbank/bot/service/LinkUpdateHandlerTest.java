package ru.tbank.bot.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import ru.tbank.bot.exception.NotificationSendException;
import ru.tbank.common.dto.LinkUpdate;

class LinkUpdateHandlerTest {

    private static final LinkUpdate UPDATE =
        new LinkUpdate(1L, "https://github.com/user/repo", "Новый Issue", List.of(10L, 20L));

    private final TelegramBotService telegram = mock(TelegramBotService.class);
    private final LinkUpdateHandler handler = new LinkUpdateHandler(telegram);

    @Test
    void updateShouldBeSentToEveryChat() {
        handler.handle(UPDATE, "100");

        String text = "Обновление по ссылке https://github.com/user/repo\nНовый Issue";
        verify(telegram).sendNotification(10L, text);
        verify(telegram).sendNotification(20L, text);
    }

    @Test
    void sameMessageIdShouldBeSentOnlyOnce() {
        handler.handle(UPDATE, "100");
        handler.handle(UPDATE, "100");

        verify(telegram, times(1)).sendNotification(eq(10L), anyString());
        verify(telegram, times(1)).sendNotification(eq(20L), anyString());
    }

    @Test
    void differentMessageIdsShouldBothBeSent() {
        handler.handle(UPDATE, "100");
        handler.handle(UPDATE, "101");

        verify(telegram, times(2)).sendNotification(eq(10L), anyString());
    }

    @Test
    void messagesWithoutIdAreNotDeduplicated() {
        handler.handle(UPDATE, null);
        handler.handle(UPDATE, null);

        verify(telegram, times(2)).sendNotification(eq(10L), anyString());
    }

    @Test
    void retryShouldSendOnlyToChatsThatDidNotGetMessage() {
        doThrow(new NotificationSendException("502")).when(telegram).sendNotification(eq(20L), anyString());
        assertThatThrownBy(() -> handler.handle(UPDATE, "100")).isInstanceOf(NotificationSendException.class);

        doNothing().when(telegram).sendNotification(anyLong(), anyString());
        handler.handle(UPDATE, "100");

        verify(telegram, times(1)).sendNotification(eq(10L), anyString());
        verify(telegram, times(2)).sendNotification(eq(20L), anyString());
    }
}
