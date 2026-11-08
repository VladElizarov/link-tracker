package ru.tbank.scrapper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.tbank.common.dto.LinkUpdate;
import ru.tbank.scrapper.exception.LinkCheckException;
import ru.tbank.scrapper.model.ContentUpdate;
import ru.tbank.scrapper.model.Link;
import ru.tbank.scrapper.model.UpdateType;
import ru.tbank.scrapper.repository.LinkRepository;
import ru.tbank.scrapper.repository.OutboxRepository;

class LinkCheckServiceTest {

    private static final Instant OLD = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-02T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-03T00:00:00Z");
    private static final Link LINK = new Link(7L, "https://github.com/user/a", OLD);

    private LinkRepository linkRepository;
    private OutboxRepository outboxRepository;
    private LinkUpdateChecker checker;
    private LinkCheckService service;

    @BeforeEach
    void setUp() {
        linkRepository = mock(LinkRepository.class);
        outboxRepository = mock(OutboxRepository.class);
        checker = mock(LinkUpdateChecker.class);
        TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        service = new LinkCheckService(
            linkRepository, outboxRepository, checker, new UpdateMessageFormatter(), transactionTemplate);
        when(linkRepository.findChatIdsByLinkId(7L)).thenReturn(List.of(1L, 3L));
    }

    @Test
    void firstCheckShouldOnlyRememberTime() {
        Link neverChecked = new Link(7L, LINK.url(), null);
        when(checker.getUpdates(LINK.url(), null)).thenReturn(List.of());

        service.checkLink(neverChecked);

        ArgumentCaptor<Instant> lastUpdate = ArgumentCaptor.forClass(Instant.class);
        verify(linkRepository).updateCheckResult(eq(7L), lastUpdate.capture(), any());
        assertThat(lastUpdate.getValue()).isNotNull();
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void newUpdatesShouldBeSavedToOutboxForAllSubscribers() {
        when(checker.getUpdates(LINK.url(), OLD)).thenReturn(List.of(
            new ContentUpdate(UpdateType.ISSUE, "Первый", "a", T1, "текст 1"),
            new ContentUpdate(UpdateType.PULL_REQUEST, "Второй", "b", T2, "текст 2")
        ));

        service.checkLink(LINK);

        verify(linkRepository).updateCheckResult(eq(7L), eq(T2), any());
        ArgumentCaptor<LinkUpdate> captor = ArgumentCaptor.forClass(LinkUpdate.class);
        verify(outboxRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(update -> {
            assertThat(update.id()).isEqualTo(7L);
            assertThat(update.url()).isEqualTo(LINK.url());
            assertThat(update.tgChatIds()).containsExactly(1L, 3L);
        });
        assertThat(captor.getAllValues().get(0).description()).contains("Новый Issue", "Первый", "Автор: a");
        assertThat(captor.getAllValues().get(1).description()).contains("Новый Pull Request", "Второй", "Автор: b");
    }

    @Test
    void noUpdatesShouldKeepOldTimeAndSaveNothing() {
        when(checker.getUpdates(LINK.url(), OLD)).thenReturn(List.of());

        service.checkLink(LINK);

        verify(linkRepository).updateCheckResult(eq(7L), eq(OLD), any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void apiErrorShouldMarkLinkCheckedAndRethrow() {
        when(checker.getUpdates(LINK.url(), OLD)).thenThrow(new LinkCheckException("500"));

        assertThatThrownBy(() -> service.checkLink(LINK)).isInstanceOf(LinkCheckException.class);

        verify(linkRepository).updateCheckResult(eq(7L), eq(OLD), any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void linkWithoutSubscribersShouldNotSaveAnything() {
        when(linkRepository.findChatIdsByLinkId(7L)).thenReturn(List.of());
        when(checker.getUpdates(LINK.url(), OLD)).thenReturn(List.of(
            new ContentUpdate(UpdateType.COMMENT, "Вопрос", "a", T1, "коммент")
        ));

        service.checkLink(LINK);

        verify(outboxRepository, never()).save(any());
    }
}
