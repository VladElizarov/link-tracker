package ru.tbank.scrapper.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import ru.tbank.common.dto.LinkUpdate;
import ru.tbank.scrapper.AbstractIntegrationTest;
import ru.tbank.scrapper.config.KafkaTopicConfig;
import ru.tbank.scrapper.config.OutboxConfig;
import ru.tbank.scrapper.repository.OutboxRepository;
import ru.tbank.scrapper.scheduler.OutboxPublisher;

class KafkaMessageSenderTest extends AbstractIntegrationTest {

    private static final String TOPIC = "link-updates";

    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.9.1");

    static {
        KAFKA.start();
    }

    @Autowired
    private MessageSender messageSender;
    @Autowired
    private OutboxRepository outboxRepository;
    @Autowired
    private KafkaTopicConfig topicConfig;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("app.message-transport", () -> "KAFKA");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.kafka.replicas", () -> "1");
        registry.add("app.kafka.min-insync-replicas", () -> "1");
    }

    private List<ConsumerRecord<String, String>> readFromTopic(String marker, int expectedCount) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        ))) {
            consumer.subscribe(List.of(TOPIC));
            List<ConsumerRecord<String, String>> found = new ArrayList<>();
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (record.value().contains(marker)) {
                        found.add(record);
                    }
                }
                return found.size() >= expectedCount;
            });
            return found;
        }
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    @Test
    void kafkaTransportShouldUseKafkaSender() {
        assertThat(messageSender).isInstanceOf(KafkaMessageSender.class);
    }

    @Test
    void notificationShouldBeSentToTopicAsJson() {
        String marker = "json-" + UUID.randomUUID();

        messageSender.send(42L, new LinkUpdate(7L, "https://github.com/user/repo", marker, List.of(1L, 2L)));

        ConsumerRecord<String, String> record = readFromTopic(marker, 1).get(0);
        assertThat(record.value()).isEqualTo(
            "{\"id\":7,\"url\":\"https://github.com/user/repo\",\"description\":\"" + marker + "\",\"tgChatIds\":[1,2]}");
        assertThat(record.key()).isEqualTo("7");
        assertThat(header(record, "message-id")).isEqualTo("42");
        assertThat(record.headers().lastHeader("__TypeId__")).isNull();
    }

    @Test
    void outboxPublisherShouldSendToKafkaAndMarkSent() {
        String marker = "outbox-" + UUID.randomUUID();
        outboxRepository.save(new LinkUpdate(8L, "https://github.com/user/repo", marker, List.of(1L)));
        outboxRepository.save(new LinkUpdate(8L, "https://github.com/user/repo", marker + "-2", List.of(1L)));
        OutboxPublisher publisher = new OutboxPublisher(outboxRepository, messageSender, outboxConfig());

        assertThat(publisher.publish()).isEqualTo(2);

        assertThat(outboxRepository.findNew(10)).isEmpty();
        List<ConsumerRecord<String, String>> records = readFromTopic(marker, 2);
        assertThat(records).extracting(ConsumerRecord::value)
            .satisfiesExactly(
                first -> assertThat(first).contains("\"" + marker + "\""),
                second -> assertThat(second).contains(marker + "-2"));
    }

    @Test
    void unavailableKafkaShouldKeepOutboxRecordNew() {
        KafkaTemplate<String, LinkUpdate> deadTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1",
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class,
            ProducerConfig.MAX_BLOCK_MS_CONFIG, 1000
        )));
        KafkaMessageSender deadSender = new KafkaMessageSender(deadTemplate, topicConfig);
        outboxRepository.save(new LinkUpdate(9L, "https://github.com/user/repo", "не дойдёт", List.of(1L)));
        OutboxPublisher publisher = new OutboxPublisher(outboxRepository, deadSender, outboxConfig());

        assertThat(publisher.publish()).isZero();

        assertThat(outboxRepository.findNew(10)).hasSize(1);

        assertThat(new OutboxPublisher(outboxRepository, messageSender, outboxConfig()).publish()).isEqualTo(1);
        assertThat(outboxRepository.findNew(10)).isEmpty();
        deadTemplate.destroy();
    }

    @Test
    void sendToUnavailableKafkaShouldThrow() {
        KafkaTemplate<String, LinkUpdate> deadTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1",
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class,
            ProducerConfig.MAX_BLOCK_MS_CONFIG, 1000
        )));
        KafkaMessageSender deadSender = new KafkaMessageSender(deadTemplate, topicConfig);

        assertThatThrownBy(() -> deadSender.send(1L, new LinkUpdate(1L, "https://github.com/a/b", "x", List.of(1L))))
            .isInstanceOf(IllegalStateException.class);
        deadTemplate.destroy();
    }

    private static OutboxConfig outboxConfig() {
        return new OutboxConfig(true, Duration.ofSeconds(1), 100);
    }
}
