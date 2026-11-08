package ru.tbank.scrapper.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;
import ru.tbank.common.dto.LinkUpdate;
import ru.tbank.scrapper.AbstractIntegrationTest;
import ru.tbank.scrapper.config.ScrapperConfig;
import ru.tbank.scrapper.exception.LinkAlreadyExistsException;
import ru.tbank.scrapper.exception.TagAlreadyExistsException;
import ru.tbank.scrapper.model.ContentUpdate;
import ru.tbank.scrapper.model.Link;
import ru.tbank.scrapper.model.OutboxMessage;
import ru.tbank.scrapper.model.TrackedLink;
import ru.tbank.scrapper.model.UpdateType;
import ru.tbank.scrapper.notification.HttpMessageSender;
import ru.tbank.scrapper.notification.MessageSender;
import ru.tbank.scrapper.scheduler.LinkUpdateScheduler;
import ru.tbank.scrapper.service.LinkCheckService;
import ru.tbank.scrapper.service.LinkService;
import ru.tbank.scrapper.service.LinkUpdateChecker;
import ru.tbank.scrapper.service.TagService;
import ru.tbank.scrapper.service.UpdateMessageFormatter;

public abstract class AbstractDatabaseTest extends AbstractIntegrationTest {

    private static final String GITHUB = "https://github.com/user/repo";
    private static final String STACKOVERFLOW = "https://stackoverflow.com/questions/123";
    private static final Instant OLD = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant NEW = Instant.parse("2026-02-01T00:00:00Z");

    @Autowired
    protected ChatRepository chatRepository;
    @Autowired
    protected LinkRepository linkRepository;
    @Autowired
    protected TagRepository tagRepository;
    @Autowired
    private LinkService linkService;
    @Autowired
    private TagService tagService;
    @Autowired
    private ScrapperConfig config;
    @Autowired
    private OutboxRepository outboxRepository;
    @Autowired
    private MessageSender messageSender;
    @Autowired
    private TransactionTemplate transactionTemplate;

    protected abstract List<Class<?>> expectedImplementations();

    @Test
    void accessTypeShouldSelectCorrectImplementation() {
        assertThat(List.of(
            AopUtils.getTargetClass(chatRepository),
            AopUtils.getTargetClass(linkRepository),
            AopUtils.getTargetClass(tagRepository)
        )).isEqualTo(expectedImplementations());
    }

    @Test
    void chatShouldBeSavedAndDeleted() {
        chatRepository.save(1L);
        assertThat(chatRepository.exists(1L)).isTrue();
        assertThat(count("chats")).isEqualTo(1);

        chatRepository.delete(1L);
        assertThat(chatRepository.exists(1L)).isFalse();
        assertThat(count("chats")).isZero();
    }

    @Test
    void deletingChatShouldDeleteItsSubscriptionsAndTags() {
        chatRepository.save(1L);
        linkRepository.addSubscription(1L, GITHUB, List.of("работа"), List.of("user:dummy"));

        chatRepository.delete(1L);

        assertThat(count("subscriptions")).isZero();
        assertThat(count("tags")).isZero();
        assertThat(count("subscription_filters")).isZero();
    }

    @Test
    void addedLinkShouldBeSavedInDatabase() {
        chatRepository.save(1L);

        TrackedLink link = linkRepository.addSubscription(1L, GITHUB, List.of("работа", "баг"), List.of("user:dummy"));

        assertThat(link.url()).isEqualTo(GITHUB);
        assertThat(link.tags()).containsExactly("баг", "работа");
        assertThat(link.filters()).containsExactly("user:dummy");
        assertThat(count("links")).isEqualTo(1);
        assertThat(count("subscriptions")).isEqualTo(1);
        assertThat(count("tags")).isEqualTo(2);
        assertThat(count("subscription_tags")).isEqualTo(2);
        assertThat(count("subscription_filters")).isEqualTo(1);
        assertThat(linkRepository.existsSubscription(1L, GITHUB)).isTrue();
        assertThat(linkRepository.findSubscriptionsByChatId(1L)).containsExactly(link);
    }

    @Test
    void removedLinkShouldBeAbsentInDatabase() {
        chatRepository.save(1L);
        linkRepository.addSubscription(1L, GITHUB, List.of("работа"), List.of("user:dummy"));

        TrackedLink removed = linkRepository.removeSubscription(1L, GITHUB);

        assertThat(removed.url()).isEqualTo(GITHUB);
        assertThat(linkRepository.existsSubscription(1L, GITHUB)).isFalse();
        assertThat(linkRepository.findSubscriptionsByChatId(1L)).isEmpty();
        assertThat(count("subscriptions")).isZero();
        assertThat(count("subscription_tags")).isZero();
        assertThat(count("subscription_filters")).isZero();
        assertThat(linkRepository.removeSubscription(1L, GITHUB)).isNull();
    }

    @Test
    void duplicateSubscriptionShouldFailInRepository() {
        chatRepository.save(1L);
        linkRepository.addSubscription(1L, GITHUB, List.of(), List.of());

        assertThatThrownBy(() -> linkRepository.addSubscription(1L, GITHUB, List.of(), List.of()))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("subscriptions")).isEqualTo(1);
    }

    @Test
    void duplicateLinkShouldFailInService() {
        chatRepository.save(1L);
        linkService.add(1L, GITHUB, List.of(), List.of());

        assertThatThrownBy(() -> linkService.add(1L, GITHUB, List.of(), List.of()))
            .isInstanceOf(LinkAlreadyExistsException.class);
    }

    @Test
    void sameLinkForTwoChatsShouldBeStoredOnce() {
        chatRepository.save(1L);
        chatRepository.save(2L);

        TrackedLink first = linkRepository.addSubscription(1L, GITHUB, List.of(), List.of());
        TrackedLink second = linkRepository.addSubscription(2L, GITHUB, List.of(), List.of());

        assertThat(first.id()).isEqualTo(second.id());
        assertThat(count("links")).isEqualTo(1);
        assertThat(count("subscriptions")).isEqualTo(2);
        assertThat(linkRepository.findChatIdsByLinkId(first.id())).containsExactly(1L, 2L);
    }

    @Test
    void failedAddShouldBeRolledBackCompletely() {
        assertThatThrownBy(() -> linkRepository.addSubscription(999L, GITHUB, List.of("тег"), List.of()))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(count("links")).isZero();
        assertThat(count("tags")).isZero();
    }

    @Test
    void findLinksToCheckShouldReturnBatchesOfNotCheckedLinks() {
        chatRepository.save(1L);
        linkRepository.addSubscription(1L, "https://github.com/user/a", List.of(), List.of());
        linkRepository.addSubscription(1L, "https://github.com/user/b", List.of(), List.of());
        linkRepository.addSubscription(1L, "https://github.com/user/c", List.of(), List.of());
        linkRepository.addSubscription(1L, "https://github.com/user/orphan", List.of(), List.of());
        linkRepository.removeSubscription(1L, "https://github.com/user/orphan");
        Instant now = Instant.now().plusSeconds(1);

        List<Link> firstBatch = linkRepository.findLinksToCheck(now, 0, 2);
        assertThat(firstBatch).extracting(Link::url)
            .containsExactly("https://github.com/user/a", "https://github.com/user/b");
        long lastId = firstBatch.get(1).id();
        assertThat(linkRepository.findLinksToCheck(now, lastId, 2)).extracting(Link::url)
            .containsExactly("https://github.com/user/c");

        linkRepository.updateCheckResult(firstBatch.get(0).id(), OLD, now);
        assertThat(linkRepository.findLinksToCheck(now, 0, 10)).extracting(Link::url)
            .containsExactly("https://github.com/user/b", "https://github.com/user/c");
    }

    @Test
    void updateCheckResultShouldSaveLastUpdate() {
        chatRepository.save(1L);
        TrackedLink tracked = linkRepository.addSubscription(1L, GITHUB, List.of(), List.of());

        linkRepository.updateCheckResult(tracked.id(), NEW, Instant.parse("2026-03-01T00:00:00Z"));

        List<Link> links = linkRepository.findLinksToCheck(Instant.parse("2030-01-01T00:00:00Z"), 0, 10);
        assertThat(links).containsExactly(new Link(tracked.id(), GITHUB, NEW));
    }

    @Test
    void schedulerShouldNotifyOnlySubscribersOfUpdatedLink() {
        chatRepository.save(1L);
        chatRepository.save(2L);
        chatRepository.save(3L);
        linkRepository.addSubscription(1L, GITHUB, List.of(), List.of());
        linkRepository.addSubscription(2L, STACKOVERFLOW, List.of(), List.of());
        linkRepository.addSubscription(3L, GITHUB, List.of(), List.of());

        LinkUpdateChecker checker = mock(LinkUpdateChecker.class);
        LinkUpdateScheduler scheduler = createScheduler(checker, outboxRepository);
        when(checker.getUpdates(any(), any())).thenReturn(List.of());

        scheduler.update();
        assertThat(count("outbox")).isZero();

        when(checker.getUpdates(eq(GITHUB), any())).thenReturn(List.of(
            new ContentUpdate(UpdateType.ISSUE, "Новый баг", "octocat", Instant.now().plusSeconds(60), "текст")
        ));
        scheduler.update();

        List<OutboxMessage> outbox = outboxRepository.findNew(10);
        assertThat(outbox).hasSize(1);
        LinkUpdate update = outbox.get(0).update();
        assertThat(update.url()).isEqualTo(GITHUB);
        assertThat(update.tgChatIds()).containsExactly(1L, 3L);
        assertThat(update.description()).contains("Новый Issue", "Новый баг", "octocat");
    }

    @Test
    void outboxShouldStoreJsonAndChangeStatusOnlyWhenMarkedSent() {
        outboxRepository.save(new LinkUpdate(5L, GITHUB, "текст", List.of(1L, 2L)));

        String payload = jdbcClient.sql("SELECT payload FROM outbox").query(String.class).single();
        assertThat(payload).contains("\"id\":5", "\"url\":\"" + GITHUB + "\"", "\"tgChatIds\":[1,2]");
        List<OutboxMessage> fresh = outboxRepository.findNew(10);
        assertThat(fresh).hasSize(1);

        outboxRepository.markSent(fresh.get(0).id());

        assertThat(outboxRepository.findNew(10)).isEmpty();
        String status = jdbcClient.sql("SELECT status FROM outbox").query(String.class).single();
        assertThat(status).isEqualTo("SENT");
    }

    @Test
    void linkUpdateAndOutboxShouldBeSavedInOneTransaction() {
        chatRepository.save(1L);
        TrackedLink tracked = linkRepository.addSubscription(1L, GITHUB, List.of(), List.of());
        linkRepository.updateCheckResult(tracked.id(), OLD, OLD);
        LinkUpdateChecker checker = mock(LinkUpdateChecker.class);
        when(checker.getUpdates(any(), any())).thenReturn(List.of(
            new ContentUpdate(UpdateType.ISSUE, "Issue", "a", NEW, "текст")
        ));
        OutboxRepository brokenOutbox = mock(OutboxRepository.class);
        doThrow(new IllegalStateException("диск переполнен")).when(brokenOutbox).save(any());
        LinkCheckService service = new LinkCheckService(
            linkRepository, brokenOutbox, checker, new UpdateMessageFormatter(), transactionTemplate);

        assertThatThrownBy(() -> service.checkLink(new Link(tracked.id(), GITHUB, OLD)))
            .isInstanceOf(IllegalStateException.class);

        assertThat(linkRepository.findLinksToCheck(Instant.parse("2030-01-01T00:00:00Z"), 0, 10))
            .containsExactly(new Link(tracked.id(), GITHUB, OLD));
    }

    @Test
    void httpTransportShouldUseHttpSender() {
        assertThat(messageSender).isInstanceOf(HttpMessageSender.class);
    }

    @Test
    void schedulerShouldCheckAllLinksInSeveralBatches() {
        chatRepository.save(1L);
        for (int i = 0; i < 120; i++) {
            linkRepository.addSubscription(1L, "https://github.com/user/repo" + i, List.of(), List.of());
        }
        LinkUpdateChecker checker = mock(LinkUpdateChecker.class);
        when(checker.getUpdates(any(), any())).thenReturn(List.of());

        createScheduler(checker, outboxRepository).update();

        verify(checker, times(120)).getUpdates(any(), any());
        Long notChecked = jdbcClient.sql("SELECT count(*) FROM links WHERE last_checked_at IS NULL OR last_update IS NULL")
            .query(Long.class).single();
        assertThat(notChecked).isZero();
        assertThat(linkRepository.findLinksToCheck(Instant.now().minus(Duration.ofMinutes(1)), 0, 10)).isEmpty();
    }

    private LinkUpdateScheduler createScheduler(LinkUpdateChecker checker, OutboxRepository outbox) {
        LinkCheckService checkService = new LinkCheckService(
            linkRepository, outbox, checker, new UpdateMessageFormatter(), transactionTemplate);
        return new LinkUpdateScheduler(linkRepository, checkService, outbox, config);
    }

    @Test
    void tagsShouldSupportCrud() {
        chatRepository.save(1L);

        tagRepository.create(1L, "работа");
        tagRepository.create(1L, "java");
        assertThat(tagRepository.findNamesByChatId(1L)).containsExactly("java", "работа");
        assertThat(tagRepository.exists(1L, "java")).isTrue();
        assertThat(tagRepository.rename(1L, "java", "kotlin")).isTrue();
        assertThat(tagRepository.findNamesByChatId(1L)).containsExactly("kotlin", "работа");
        assertThat(tagRepository.rename(1L, "нет-такого", "x")).isFalse();
        assertThat(tagRepository.delete(1L, "kotlin")).isTrue();
        assertThat(tagRepository.findNamesByChatId(1L)).containsExactly("работа");
        assertThat(tagRepository.delete(1L, "kotlin")).isFalse();
    }

    @Test
    void tagsOfDifferentChatsShouldBeIndependent() {
        chatRepository.save(1L);
        chatRepository.save(2L);
        tagRepository.create(1L, "работа");
        tagRepository.create(2L, "работа");

        tagRepository.rename(1L, "работа", "учёба");

        assertThat(tagRepository.findNamesByChatId(1L)).containsExactly("учёба");
        assertThat(tagRepository.findNamesByChatId(2L)).containsExactly("работа");
    }

    @Test
    void tagCanBeAttachedAndDetachedFromLink() {
        chatRepository.save(1L);
        linkRepository.addSubscription(1L, GITHUB, List.of("работа"), List.of());

        assertThat(tagRepository.addToSubscription(1L, GITHUB, "java")).isTrue();
        assertThat(linkRepository.findSubscriptionsByChatId(1L).get(0).tags()).containsExactly("java", "работа");

        assertThat(tagRepository.removeFromSubscription(1L, GITHUB, "работа")).isTrue();
        assertThat(linkRepository.findSubscriptionsByChatId(1L).get(0).tags()).containsExactly("java");
        assertThat(tagRepository.exists(1L, "работа")).isTrue();

        assertThat(tagRepository.addToSubscription(1L, STACKOVERFLOW, "java")).isFalse();
    }

    @Test
    void deletedTagShouldDisappearFromLinks() {
        chatRepository.save(1L);
        linkRepository.addSubscription(1L, GITHUB, List.of("работа", "java"), List.of());

        tagRepository.delete(1L, "работа");

        assertThat(linkRepository.findSubscriptionsByChatId(1L).get(0).tags()).containsExactly("java");
    }

    @Test
    void renamingTagToExistingNameShouldFailInService() {
        chatRepository.save(1L);
        tagService.create(1L, "работа");
        tagService.create(1L, "java");

        assertThatThrownBy(() -> tagService.rename(1L, "java", "работа"))
            .isInstanceOf(TagAlreadyExistsException.class);
    }
}
