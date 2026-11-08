package ru.tbank.bot.kafka;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.tbank.bot.exception.InvalidLinkUpdateException;
import ru.tbank.bot.service.LinkUpdateHandler;
import ru.tbank.common.dto.LinkUpdate;

@Component
@ConditionalOnProperty(prefix = "app", name = "message-transport", havingValue = "KAFKA", matchIfMissing = true)
public class LinkUpdateListener {

    private static final Logger log = LoggerFactory.getLogger(LinkUpdateListener.class);

    public static final String MESSAGE_ID_HEADER = "message-id";

    private final LinkUpdateHandler linkUpdateHandler;
    private final Validator validator;

    public LinkUpdateListener(LinkUpdateHandler linkUpdateHandler, Validator validator) {
        this.linkUpdateHandler = linkUpdateHandler;
        this.validator = validator;
    }

    @KafkaListener(topics = "${app.kafka.topic}")
    public void listen(ConsumerRecord<String, LinkUpdate> record) {
        LinkUpdate update = record.value();
        String messageId = readMessageId(record);
        log.atInfo()
            .addKeyValue("messageId", messageId)
            .addKeyValue("partition", record.partition())
            .addKeyValue("offset", record.offset())
            .log("Получено обновление из Kafka");

        validate(update);
        linkUpdateHandler.handle(update, messageId);
    }

    private void validate(LinkUpdate update) {
        if (update == null) {
            throw new InvalidLinkUpdateException("пустое сообщение");
        }
        Set<ConstraintViolation<LinkUpdate>> violations = validator.validate(update);
        if (!violations.isEmpty()) {
            StringBuilder errors = new StringBuilder();
            for (ConstraintViolation<LinkUpdate> violation : violations) {
                errors.append(violation.getPropertyPath()).append(" ").append(violation.getMessage()).append("; ");
            }
            throw new InvalidLinkUpdateException("некорректное уведомление: " + errors);
        }
    }

    private String readMessageId(ConsumerRecord<String, LinkUpdate> record) {
        Header header = record.headers().lastHeader(MESSAGE_ID_HEADER);
        if (header == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
