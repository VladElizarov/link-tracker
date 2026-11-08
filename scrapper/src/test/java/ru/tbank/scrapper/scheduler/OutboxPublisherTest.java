package ru.tbank.scrapper.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import ru.tbank.common.dto.LinkUpdate;
import ru.tbank.scrapper.config.OutboxConfig;
import ru.tbank.scrapper.model.OutboxMessage;
import ru.tbank.scrapper.notification.MessageSender;
import ru.tbank.scrapper.repository.OutboxRepository;

class OutboxPublisherTest {

    private static final OutboxMessage FIRST =
        new OutboxMessage(1L, new LinkUpdate(10L, "https://github.com/user/a", "первое", List.of(1L)));
    private static final OutboxMessage SECOND =
        new OutboxMessage(2L, new LinkUpdate(10L, "https://github.com/user/a", "второе", List.of(1L)));

    private final OutboxRepository outboxRepository = mock(OutboxRepository.class);
    private final MessageSender messageSender = mock(MessageSender.class);
    private final OutboxPublisher publisher =
        new OutboxPublisher(outboxRepository, messageSender, new OutboxConfig(true, Duration.ofSeconds(1), 100));

    @Test
    void sentMessagesShouldBeMarkedAfterSending() {
        when(outboxRepository.findNew(100)).thenReturn(List.of(FIRST, SECOND));

        assertThat(publisher.publish()).isEqualTo(2);

        InOrder order = inOrder(messageSender, outboxRepository);
        order.verify(messageSender).send(1L, FIRST.update());
        order.verify(outboxRepository).markSent(1L);
        order.verify(messageSender).send(2L, SECOND.update());
        order.verify(outboxRepository).markSent(2L);
    }

    @Test
    void failedSendShouldKeepMessageNew() {
        when(outboxRepository.findNew(100)).thenReturn(List.of(FIRST, SECOND));
        doThrow(new IllegalStateException("Kafka недоступна")).when(messageSender).send(eq(1L), any());

        assertThat(publisher.publish()).isZero();

        verify(outboxRepository, never()).markSent(anyLong());
        verify(messageSender, never()).send(eq(2L), any());
    }

    @Test
    void failureInTheMiddleShouldMarkOnlySentMessages() {
        when(outboxRepository.findNew(100)).thenReturn(List.of(FIRST, SECOND));
        doThrow(new IllegalStateException("сеть пропала")).when(messageSender).send(eq(2L), any());

        assertThat(publisher.publish()).isEqualTo(1);

        verify(outboxRepository).markSent(1L);
        verify(outboxRepository, never()).markSent(2L);
    }

    @Test
    void emptyOutboxShouldSendNothing() {
        when(outboxRepository.findNew(100)).thenReturn(List.of());

        assertThat(publisher.publish()).isZero();
        verify(messageSender, never()).send(anyLong(), any());
    }
}
