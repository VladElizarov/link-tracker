package ru.tbank.bot.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;
import ru.tbank.bot.exception.InvalidLinkUpdateException;

@Configuration
@ConditionalOnProperty(prefix = "app", name = "message-transport", havingValue = "KAFKA", matchIfMissing = true)
public class BotKafkaConfig {

    @Bean
    public NewTopic linkUpdatesTopic(BotKafkaProperties config) {
        return TopicBuilder.name(config.topic())
            .partitions(config.partitions())
            .replicas(config.replicas())
            .config("min.insync.replicas", String.valueOf(config.minInsyncReplicas()))
            .build();
    }

    @Bean
    public NewTopic deadLetterTopic(BotKafkaProperties config) {
        return TopicBuilder.name(config.dlqTopic())
            .partitions(1)
            .replicas(config.replicas())
            .config("min.insync.replicas", String.valueOf(config.minInsyncReplicas()))
            .config("retention.ms", String.valueOf(30L * 24 * 60 * 60 * 1000))
            .build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(BotKafkaProperties config, KafkaProperties kafkaProperties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            dlqTemplates(kafkaProperties),
            (record, exception) -> new TopicPartition(config.dlqTopic(), -1)
        );

        FixedBackOff backOff = new FixedBackOff(config.retry().backoff().toMillis(), config.retry().maxAttempts() - 1);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(InvalidLinkUpdateException.class);
        return errorHandler;
    }

    private Map<Class<?>, KafkaOperations<?, ?>> dlqTemplates(KafkaProperties kafkaProperties) {
        Map<String, Object> producerProps = kafkaProperties.buildProducerProperties(null);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        Map<String, Object> bytesProps = new LinkedHashMap<>(producerProps);
        bytesProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

        Map<String, Object> jsonProps = new LinkedHashMap<>(producerProps);
        jsonProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        jsonProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);

        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(bytesProps)));
        templates.put(Object.class, new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(jsonProps)));
        return templates;
    }
}
