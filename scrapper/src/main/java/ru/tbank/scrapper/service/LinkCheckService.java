package ru.tbank.scrapper.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.tbank.common.dto.LinkUpdate;
import ru.tbank.scrapper.exception.LinkCheckException;
import ru.tbank.scrapper.model.ContentUpdate;
import ru.tbank.scrapper.model.Link;
import ru.tbank.scrapper.repository.LinkRepository;
import ru.tbank.scrapper.repository.OutboxRepository;

@Service
public class LinkCheckService {

    private final LinkRepository linkRepository;
    private final OutboxRepository outboxRepository;
    private final LinkUpdateChecker updateChecker;
    private final UpdateMessageFormatter formatter;
    private final TransactionTemplate transactionTemplate;

    public LinkCheckService(
        LinkRepository linkRepository,
        OutboxRepository outboxRepository,
        LinkUpdateChecker updateChecker,
        UpdateMessageFormatter formatter,
        TransactionTemplate transactionTemplate
    ) {
        this.linkRepository = linkRepository;
        this.outboxRepository = outboxRepository;
        this.updateChecker = updateChecker;
        this.formatter = formatter;
        this.transactionTemplate = transactionTemplate;
    }

    public void checkLink(Link link) {
        Instant checkedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        List<ContentUpdate> updates;
        try {
            updates = updateChecker.getUpdates(link.url(), link.lastUpdate());
        } catch (LinkCheckException e) {
            linkRepository.updateCheckResult(link.id(), link.lastUpdate(), checkedAt);
            throw e;
        }

        if (link.lastUpdate() == null) {
            linkRepository.updateCheckResult(link.id(), checkedAt, checkedAt);
            return;
        }

        if (updates.isEmpty()) {
            linkRepository.updateCheckResult(link.id(), link.lastUpdate(), checkedAt);
            return;
        }

        Instant newest = link.lastUpdate();
        for (ContentUpdate update : updates) {
            if (update.createdAt().isAfter(newest)) {
                newest = update.createdAt();
            }
        }
        saveResultWithNotifications(link, updates, newest, checkedAt);
    }

    private void saveResultWithNotifications(Link link, List<ContentUpdate> updates, Instant newest, Instant checkedAt) {
        transactionTemplate.executeWithoutResult(status -> {
            linkRepository.updateCheckResult(link.id(), newest, checkedAt);

            List<Long> chatIds = new ArrayList<>(linkRepository.findChatIdsByLinkId(link.id()));
            if (chatIds.isEmpty()) {
                return;
            }
            for (ContentUpdate update : updates) {
                outboxRepository.save(new LinkUpdate(link.id(), link.url(), formatter.format(update), chatIds));
            }
        });
    }
}
