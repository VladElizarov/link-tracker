package ru.tbank.scrapper.scheduler;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.tbank.scrapper.config.OutboxConfig;
import ru.tbank.scrapper.model.OutboxMessage;
import ru.tbank.scrapper.notification.MessageSender;
import ru.tbank.scrapper.repository.OutboxRepository;

@Component
@ConditionalOnProperty(prefix = "app.outbox", name = "enable", havingValue = "true")
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final MessageSender messageSender;
    private final int batchSize;

    public OutboxPublisher(OutboxRepository outboxRepository, MessageSender messageSender, OutboxConfig config) {
        this.outboxRepository = outboxRepository;
        this.messageSender = messageSender;
        this.batchSize = config.batchSize();
    }

    @Scheduled(fixedDelayString = "${app.outbox.interval}")
    public int publish() {
        List<OutboxMessage> messages = outboxRepository.findNew(batchSize);
        int sent = 0;
        for (OutboxMessage message : messages) {
            try {
                messageSender.send(message.id(), message.update());
            } catch (Exception e) {
                log.atWarn()
                    .addKeyValue("outboxId", message.id())
                    .addKeyValue("error", e.getMessage())
                    .log("Не удалось отправить уведомление, попробуем позже");
                break;
            }
            outboxRepository.markSent(message.id());
            sent++;
        }

        if (sent > 0) {
            log.atInfo().addKeyValue("count", sent).log("Уведомления из outbox отправлены");
        }
        return sent;
    }
}
