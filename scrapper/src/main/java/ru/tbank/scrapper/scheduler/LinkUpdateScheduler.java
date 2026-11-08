package ru.tbank.scrapper.scheduler;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.tbank.common.dto.LinkUpdate;
import ru.tbank.scrapper.config.ScrapperConfig;
import ru.tbank.scrapper.model.Link;
import ru.tbank.scrapper.repository.LinkRepository;
import ru.tbank.scrapper.repository.OutboxRepository;
import ru.tbank.scrapper.service.LinkCheckService;

@Component
@ConditionalOnProperty(prefix = "app.scheduler", name = "enable", havingValue = "true")
public class LinkUpdateScheduler {

    private static final Logger log = LoggerFactory.getLogger(LinkUpdateScheduler.class);

    private final LinkRepository linkRepository;
    private final LinkCheckService linkCheckService;
    private final OutboxRepository outboxRepository;
    private final int batchSize;
    private final int threads;

    private final Set<Long> reportedFailures = ConcurrentHashMap.newKeySet();

    public LinkUpdateScheduler(
        LinkRepository linkRepository,
        LinkCheckService linkCheckService,
        OutboxRepository outboxRepository,
        ScrapperConfig config
    ) {
        this.linkRepository = linkRepository;
        this.linkCheckService = linkCheckService;
        this.outboxRepository = outboxRepository;
        this.batchSize = config.scheduler().batchSize();
        this.threads = config.scheduler().threads();
    }

    public record FailedLink(Link link, String reason) {
    }

    @Scheduled(fixedDelayString = "${app.scheduler.interval}")
    public void update() {
        Instant startedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        List<FailedLink> failed = new ArrayList<>();
        int checked = 0;
        long lastId = 0;

        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Link> batch = linkRepository.findLinksToCheck(startedAt, lastId, batchSize);
            while (!batch.isEmpty()) {
                failed.addAll(processBatch(batch, executor));
                checked += batch.size();
                lastId = batch.get(batch.size() - 1).id();
                batch = linkRepository.findLinksToCheck(startedAt, lastId, batchSize);
            }
        }

        sendFailureReport(failed);
        log.atInfo()
            .addKeyValue("linksCount", checked)
            .addKeyValue("failedCount", failed.size())
            .log("Проверка ссылок завершена");
    }

    List<FailedLink> processBatch(List<Link> batch, ExecutorService executor) {
        List<Future<List<FailedLink>>> futures = new ArrayList<>();
        for (List<Link> part : splitIntoParts(batch, threads)) {
            futures.add(executor.submit(() -> processPart(part)));
        }

        List<FailedLink> failed = new ArrayList<>();
        for (Future<List<FailedLink>> future : futures) {
            try {
                failed.addAll(future.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.atWarn().log("Проверка ссылок прервана");
            } catch (ExecutionException e) {
                log.atError().addKeyValue("error", e.getMessage()).log("Ошибка в потоке проверки ссылок");
            }
        }
        return failed;
    }

    private List<FailedLink> processPart(List<Link> part) {
        List<FailedLink> failed = new ArrayList<>();
        for (Link link : part) {
            try {
                linkCheckService.checkLink(link);
                reportedFailures.remove(link.id());
            } catch (Exception e) {
                log.atWarn()
                    .addKeyValue("url", link.url())
                    .addKeyValue("error", e.getMessage())
                    .log("Не удалось проверить ссылку");
                failed.add(new FailedLink(link, e.getMessage()));
            }
        }
        return failed;
    }

    static List<List<Link>> splitIntoParts(List<Link> links, int partsCount) {
        List<List<Link>> parts = new ArrayList<>();
        int partSize = (links.size() + partsCount - 1) / partsCount;
        for (int start = 0; start < links.size(); start += partSize) {
            int end = Math.min(start + partSize, links.size());
            parts.add(links.subList(start, end));
        }
        return parts;
    }

    private void sendFailureReport(List<FailedLink> failed) {
        for (FailedLink failedLink : failed) {
            Link link = failedLink.link();
            if (!reportedFailures.add(link.id())) {
                continue;
            }
            try {
                List<Long> chatIds = linkRepository.findChatIdsByLinkId(link.id());
                if (chatIds.isEmpty()) {
                    continue;
                }
                String text = "Отчёт: не удалось проверить ссылку.\n"
                    + "Причина: " + failedLink.reason() + "\n"
                    + "Попробуем ещё раз при следующей проверке.";
                outboxRepository.save(new LinkUpdate(link.id(), link.url(), text, chatIds));
            } catch (Exception e) {
                reportedFailures.remove(link.id());
                log.atWarn()
                    .addKeyValue("url", link.url())
                    .addKeyValue("error", e.getMessage())
                    .log("Не удалось сохранить отчёт об ошибке");
            }
        }
    }
}
