package backend.academy.linktracker.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.ai.dto.ProcessedLinkUpdateEvent;
import backend.academy.linktracker.ai.dto.UpdatePriority;
import backend.academy.linktracker.ai.kafka.ProcessedUpdateProducer;
import backend.academy.linktracker.ai.properties.AiAgentProperties;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class UpdateGroupingServiceTest {

    private ScheduledExecutorService scheduler;
    private ProcessedUpdateProducer producer;
    private UpdateGroupingService groupingService;

    @BeforeEach
    void setUp() {
        var properties = new AiAgentProperties();
        properties.getGrouping().setWindowMs(10);
        scheduler = Executors.newSingleThreadScheduledExecutor();
        producer = Mockito.mock(ProcessedUpdateProducer.class);
        when(producer.send(any())).thenReturn(CompletableFuture.completedFuture(null));
        groupingService = new UpdateGroupingService(properties, producer, scheduler);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void groupsMultipleUpdatesForSameChat() {
        groupingService.submit(update(1L, "Critical update", List.of(111L), UpdatePriority.HIGH));
        groupingService.submit(update(2L, "Fix typo", List.of(111L), UpdatePriority.LOW));

        var grouped = awaitSentUpdate();

        assertThat(grouped.id()).isEqualTo(1L);
        assertThat(grouped.tgChatIds()).containsExactly(111L);
        assertThat(grouped.description()).isEqualTo("1. Critical update" + System.lineSeparator() + "2. Fix typo");
        assertThat(grouped.priority()).isEqualTo(UpdatePriority.HIGH);
    }

    @Test
    void sendsSingleUpdateWithoutGroupingChanges() {
        groupingService.submit(update(1L, "Regular update", List.of(111L), UpdatePriority.MEDIUM));

        var sent = awaitSentUpdate();

        assertThat(sent.id()).isEqualTo(1L);
        assertThat(sent.tgChatIds()).containsExactly(111L);
        assertThat(sent.description()).isEqualTo("Regular update");
        assertThat(sent.priority()).isEqualTo(UpdatePriority.MEDIUM);
    }

    @Test
    void retriesGroupWhenProducerThrows() {
        when(producer.send(any()))
                .thenThrow(new IllegalStateException("Kafka unavailable"))
                .thenReturn(CompletableFuture.completedFuture(null));

        groupingService.submit(update(1L, "Regular update", List.of(111L), UpdatePriority.MEDIUM));

        awaitSendAttempts(2);
    }

    @Test
    void retriesGroupWhenProducerFutureFails() {
        when(producer.send(any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka unavailable")))
                .thenReturn(CompletableFuture.completedFuture(null));

        groupingService.submit(update(1L, "Regular update", List.of(111L), UpdatePriority.MEDIUM));

        awaitSendAttempts(2);
    }

    private ProcessedLinkUpdateEvent awaitSentUpdate() {
        var captor = ArgumentCaptor.forClass(ProcessedLinkUpdateEvent.class);
        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> verify(producer)
                .send(captor.capture()));
        return captor.getValue();
    }

    private void awaitSendAttempts(int expectedAttempts) {
        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> verify(producer, times(expectedAttempts))
                .send(any()));
    }

    private ProcessedLinkUpdateEvent update(
            long id, String description, List<Long> tgChatIds, UpdatePriority priority) {
        return new ProcessedLinkUpdateEvent(
                id, "https://github.com/octocat/hello-world", description, tgChatIds, priority);
    }
}
