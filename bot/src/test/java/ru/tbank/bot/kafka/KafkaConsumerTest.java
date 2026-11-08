package ru.tbank.bot.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.kafka.KafkaContainer;
import ru.tbank.bot.exception.InvalidLinkUpdateException;
import ru.tbank.bot.exception.NotificationSendException;
import ru.tbank.bot.service.TelegramBotService;

@SpringBootTest(properties = {
    "app.telegram-token=test-token",
    "app.message-transport=KAFKA",
    "app.kafka.replicas=1",
    "app.kafka.min-insync-replicas=1",
    "app.kafka.retry.max-attempts=3",
    "app.kafka.retry.backoff=100ms"
})
class KafkaConsumerTest {

    private static final String TOPIC = "link-updates";
    private static final String DLQ_TOPIC = "link-updates-dlq";

    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.9.1");

    static {
        KAFKA.start();
    }

    private static final KafkaProducer<String, String> PRODUCER = new KafkaProducer<>(Map.of(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
    ));

    @MockitoBean
    private TelegramBotService telegramBotService;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @AfterAll
    static void closeProducer() {
        PRODUCER.close();
    }

    private static void send(String messageId, String json) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, "1", json);
        if (messageId != null) {
            record.headers().add("message-id", messageId.getBytes(StandardCharsets.UTF_8));
        }
        PRODUCER.send(record).get();
    }

    private static String validJson(long chatId, String description) {
        return """
            {"id": 1, "url": "https://github.com/user/repo", "description": "%s", "tgChatIds": [%d]}
            """.formatted(description, chatId);
    }

    private static ConsumerRecord<String, String> waitForDlqRecord(String marker) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        ))) {
            consumer.subscribe(List.of(DLQ_TOPIC));
            List<ConsumerRecord<String, String>> found = new ArrayList<>();
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (record.value() != null && record.value().contains(marker)) {
                        found.add(record);
                    }
                }
                return !found.isEmpty();
            });
            return found.get(0);
        }
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    @Test
    void validMessageShouldBeSentToUser() throws Exception {
        send("1", validJson(101L, "Новый Issue"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            verify(telegramBotService).sendNotification(eq(101L), contains("Новый Issue")));
    }

    @Test
    void brokenJsonShouldGoToDlqWithoutRetries() throws Exception {
        String marker = "broken-" + UUID.randomUUID();
        send("2", "{это не json " + marker);

        ConsumerRecord<String, String> dlqRecord = waitForDlqRecord(marker);

        assertThat(dlqRecord.value()).isEqualTo("{это не json " + marker);
        String exceptions = header(dlqRecord, "kafka_dlt-exception-fqcn") + " "
            + header(dlqRecord, "kafka_dlt-exception-cause-fqcn");
        assertThat(exceptions).contains("DeserializationException");
        verify(telegramBotService, never()).sendNotification(anyLong(), anyString());
    }

    @Test
    void messageWithInvalidDataShouldGoToDlqWithoutRetries() throws Exception {
        String marker = "no-url-" + UUID.randomUUID();
        send("3", """
            {"id": 1, "description": "%s", "tgChatIds": []}
            """.formatted(marker));

        ConsumerRecord<String, String> dlqRecord = waitForDlqRecord(marker);

        assertThat(header(dlqRecord, "kafka_dlt-exception-cause-fqcn"))
            .isEqualTo(InvalidLinkUpdateException.class.getName());
        verify(telegramBotService, never()).sendNotification(anyLong(), anyString());
    }

    @Test
    void processingErrorShouldBeRetriedAndThenSucceed() throws Exception {
        doThrow(new NotificationSendException("502"))
            .doThrow(new NotificationSendException("502"))
            .doNothing()
            .when(telegramBotService).sendNotification(eq(104L), anyString());

        send("4", validJson(104L, "Повтор помог"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            verify(telegramBotService, times(3)).sendNotification(eq(104L), anyString()));
    }

    @Test
    void processingErrorShouldGoToDlqAfterAllAttempts() throws Exception {
        String marker = "always-fails-" + UUID.randomUUID();
        doThrow(new NotificationSendException("Telegram недоступен"))
            .when(telegramBotService).sendNotification(eq(105L), anyString());

        send("5", validJson(105L, marker));

        ConsumerRecord<String, String> dlqRecord = waitForDlqRecord(marker);

        verify(telegramBotService, times(3)).sendNotification(eq(105L), anyString());
        assertThat(header(dlqRecord, "kafka_dlt-exception-cause-fqcn"))
            .isEqualTo(NotificationSendException.class.getName());
        assertThat(dlqRecord.value()).contains("\"url\":\"https://github.com/user/repo\"", marker);
    }

    @Test
    void duplicateMessageShouldBeSentOnlyOnce() throws Exception {
        doNothing().when(telegramBotService).sendNotification(anyLong(), anyString());

        send("6", validJson(106L, "Дубль"));
        send("6", validJson(106L, "Дубль"));
        send("7", validJson(106L, "Следующее"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            verify(telegramBotService).sendNotification(eq(106L), contains("Следующее")));
        verify(telegramBotService, times(1)).sendNotification(eq(106L), contains("Дубль"));
    }
}
