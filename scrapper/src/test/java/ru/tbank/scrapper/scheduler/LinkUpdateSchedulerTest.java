package ru.tbank.scrapper.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.tbank.common.dto.LinkUpdate;
import ru.tbank.scrapper.config.ScrapperConfig;
import ru.tbank.scrapper.exception.LinkCheckException;
import ru.tbank.scrapper.model.Link;
import ru.tbank.scrapper.repository.LinkRepository;
import ru.tbank.scrapper.repository.OutboxRepository;
import ru.tbank.scrapper.service.LinkCheckService;

class LinkUpdateSchedulerTest {

    private final LinkRepository linkRepository = mock(LinkRepository.class);
    private final LinkCheckService linkCheckService = mock(LinkCheckService.class);
    private final OutboxRepository outboxRepository = mock(OutboxRepository.class);

    private LinkUpdateScheduler createScheduler(int batchSize, int threads) {
        ScrapperConfig config = new ScrapperConfig(
            "http://bot",
            new ScrapperConfig.GitHub("http://github", null),
            new ScrapperConfig.StackOverflow("http://so", null),
            new ScrapperConfig.Scheduler(true, Duration.ofMinutes(1), batchSize, threads),
            new ScrapperConfig.Database(ScrapperConfig.AccessType.SQL)
        );
        return new LinkUpdateScheduler(linkRepository, linkCheckService, outboxRepository, config);
    }

    private static List<Link> links(int fromId, int toId) {
        List<Link> result = new ArrayList<>();
        for (int id = fromId; id <= toId; id++) {
            result.add(new Link(id, "https://github.com/user/repo" + id, null));
        }
        return result;
    }

    @Test
    void splitIntoPartsShouldMakeEqualParts() {
        List<List<Link>> parts = LinkUpdateScheduler.splitIntoParts(links(1, 1000), 4);

        assertThat(parts).hasSize(4);
        assertThat(parts).allSatisfy(part -> assertThat(part).hasSize(250));
        assertThat(parts.get(0).get(0).id()).isEqualTo(1);
        assertThat(parts.get(3).get(249).id()).isEqualTo(1000);
    }

    @Test
    void splitIntoPartsShouldHandleUnevenAndSmallLists() {
        assertThat(LinkUpdateScheduler.splitIntoParts(links(1, 10), 4))
            .extracting(List::size).containsExactly(3, 3, 3, 1);
        assertThat(LinkUpdateScheduler.splitIntoParts(links(1, 2), 4))
            .extracting(List::size).containsExactly(1, 1);
        assertThat(LinkUpdateScheduler.splitIntoParts(List.of(), 4)).isEmpty();
    }

    @Test
    void batchesShouldBeRequestedOneAfterAnother() {
        when(linkRepository.findLinksToCheck(any(), eq(0L), eq(50))).thenReturn(links(1, 50));
        when(linkRepository.findLinksToCheck(any(), eq(50L), eq(50))).thenReturn(links(51, 100));
        when(linkRepository.findLinksToCheck(any(), eq(100L), eq(50))).thenReturn(links(101, 120));
        when(linkRepository.findLinksToCheck(any(), eq(120L), eq(50))).thenReturn(List.of());

        createScheduler(50, 4).update();

        verify(linkCheckService, times(120)).checkLink(any());
        verify(linkRepository).findLinksToCheck(any(), eq(120L), eq(50));
    }

    @Test
    void linksOfOneBatchShouldBeCheckedInParallel() throws InterruptedException {
        when(linkRepository.findLinksToCheck(any(), eq(0L), eq(50))).thenReturn(links(1, 8));
        when(linkRepository.findLinksToCheck(any(), eq(8L), eq(50))).thenReturn(List.of());

        CountDownLatch fourStarted = new CountDownLatch(4);
        Set<String> threadNames = ConcurrentHashMap.newKeySet();
        Set<Boolean> waitResults = ConcurrentHashMap.newKeySet();
        doAnswer(invocation -> {
            threadNames.add(Thread.currentThread().getName());
            fourStarted.countDown();
            waitResults.add(fourStarted.await(5, TimeUnit.SECONDS));
            return null;
        }).when(linkCheckService).checkLink(any());

        createScheduler(50, 4).update();

        assertThat(threadNames).hasSize(4);
        assertThat(waitResults).containsExactly(true);
    }

    @Test
    void failedLinkShouldNotStopOtherLinks() {
        when(linkRepository.findLinksToCheck(any(), eq(0L), eq(50))).thenReturn(links(1, 10));
        when(linkRepository.findLinksToCheck(any(), eq(10L), eq(50))).thenReturn(List.of());
        doThrow(new LinkCheckException("500 Internal Server Error")).when(linkCheckService)
            .checkLink(new Link(3, "https://github.com/user/repo3", null));
        doThrow(new RuntimeException("неожиданная ошибка")).when(linkCheckService)
            .checkLink(new Link(7, "https://github.com/user/repo7", null));
        when(linkRepository.findChatIdsByLinkId(anyLong())).thenReturn(List.of(1L));

        assertThatCode(() -> createScheduler(50, 2).update()).doesNotThrowAnyException();

        verify(linkCheckService, times(10)).checkLink(any());
        ArgumentCaptor<LinkUpdate> captor = ArgumentCaptor.forClass(LinkUpdate.class);
        verify(outboxRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(LinkUpdate::url)
            .containsExactlyInAnyOrder("https://github.com/user/repo3", "https://github.com/user/repo7");
        assertThat(captor.getAllValues().get(0).description()).startsWith("Отчёт: не удалось проверить ссылку");
        assertThat(captor.getAllValues()).extracting(LinkUpdate::description)
            .anySatisfy(text -> assertThat(text).contains("500 Internal Server Error"));
    }

    @Test
    void failureReportShouldBeSentOnlyOnceUntilLinkWorksAgain() {
        Link broken = new Link(3, "https://github.com/user/repo3", null);
        when(linkRepository.findLinksToCheck(any(), eq(0L), eq(50))).thenReturn(List.of(broken));
        when(linkRepository.findLinksToCheck(any(), eq(3L), eq(50))).thenReturn(List.of());
        when(linkRepository.findChatIdsByLinkId(3L)).thenReturn(List.of(1L));
        doThrow(new LinkCheckException("404")).when(linkCheckService).checkLink(broken);
        LinkUpdateScheduler scheduler = createScheduler(50, 4);

        scheduler.update();
        scheduler.update();
        verify(outboxRepository, times(1)).save(any());

        doAnswer(invocation -> null).when(linkCheckService).checkLink(broken);
        scheduler.update();
        doThrow(new LinkCheckException("404")).when(linkCheckService).checkLink(broken);
        scheduler.update();
        verify(outboxRepository, times(2)).save(any());
    }

    @Test
    void failedReportSaveShouldNotBreakScheduler() {
        when(linkRepository.findLinksToCheck(any(), eq(0L), eq(50))).thenReturn(links(1, 1));
        when(linkRepository.findLinksToCheck(any(), eq(1L), eq(50))).thenReturn(List.of());
        doThrow(new LinkCheckException("500")).when(linkCheckService).checkLink(any());
        when(linkRepository.findChatIdsByLinkId(anyLong())).thenReturn(List.of(1L));
        doThrow(new IllegalStateException("БД недоступна")).when(outboxRepository).save(any());

        assertThatCode(() -> createScheduler(50, 4).update()).doesNotThrowAnyException();
    }

    @Test
    void emptyDatabaseShouldNotCheckAnything() {
        when(linkRepository.findLinksToCheck(any(), eq(0L), eq(50))).thenReturn(List.of());

        createScheduler(50, 4).update();

        verify(linkCheckService, never()).checkLink(any());
        verify(outboxRepository, never()).save(any());
    }
}
