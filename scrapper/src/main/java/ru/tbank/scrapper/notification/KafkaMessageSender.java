package ru.tbank.scrapper.notification;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import ru.tbank.common.dto.LinkUpdate;
import ru.tbank.scrapper.config.KafkaTopicConfig;

@Component
@ConditionalOnProperty(prefix = "app", name = "message-transport", havingValue = "KAFKA", matchIfMissing = true)
public class KafkaMessageSender implements MessageSender {

    public static final String MESSAGE_ID_HEADER = "message-id";

    private final KafkaTemplate<String, LinkUpdate> kafkaTemplate;
    private final KafkaTopicConfig config;

    public KafkaMessageSender(KafkaTemplate<String, LinkUpdate> kafkaTemplate, KafkaTopicConfig config) {
        this.kafkaTemplate = kafkaTemplate;
        this.config = config;
    }

    @Override
    public void send(long messageId, LinkUpdate update) {
        ProducerRecord<String, LinkUpdate> record =
            new ProducerRecord<>(config.topic(), String.valueOf(update.id()), update);
        record.headers().add(MESSAGE_ID_HEADER, String.valueOf(messageId).getBytes(StandardCharsets.UTF_8));

        try {
            kafkaTemplate.send(record).get(config.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Отправка в Kafka прервана", e);
        } catch (ExecutionException | TimeoutException | KafkaException e) {
            throw new IllegalStateException("Kafka не подтвердила запись: " + e.getMessage(), e);
        }
    }
}
