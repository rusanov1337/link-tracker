package backend.academy.linktracker.scrapper.client.bot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class FallbackBotUpdatesClientTest {

    @Test
    void sendLinkUpdateUsesPrimaryHttpClientWhenItSucceeds() {
        var primaryClient = mock(HttpBotUpdatesClient.class);
        var fallbackClient = mock(KafkaBotUpdatesClient.class);
        var client = new FallbackBotUpdatesClient(primaryClient, fallbackClient);

        client.sendLinkUpdate(1L, URI.create("https://github.com/octocat/hello-world"), "Updated", List.of(1L));

        verify(primaryClient)
                .sendLinkUpdate(1L, URI.create("https://github.com/octocat/hello-world"), "Updated", List.of(1L));
        verifyNoInteractions(fallbackClient);
    }

    @Test
    void sendLinkUpdateFallsBackToKafkaWhenPrimaryHttpClientFails() {
        var primaryClient = mock(HttpBotUpdatesClient.class);
        var fallbackClient = mock(KafkaBotUpdatesClient.class);
        var client = new FallbackBotUpdatesClient(primaryClient, fallbackClient);
        doThrow(new BotUpdatesClientException("HTTP failed", new IllegalStateException()))
                .when(primaryClient)
                .sendLinkUpdate(1L, URI.create("https://github.com/octocat/hello-world"), "Updated", List.of(1L));

        assertDoesNotThrow(() -> client.sendLinkUpdate(
                1L, URI.create("https://github.com/octocat/hello-world"), "Updated", List.of(1L)));

        verify(fallbackClient)
                .sendLinkUpdate(1L, URI.create("https://github.com/octocat/hello-world"), "Updated", List.of(1L));
    }

    @Test
    void sendLinkUpdateRethrowsFallbackFailureWithPrimaryFailureSuppressed() {
        var primaryClient = mock(HttpBotUpdatesClient.class);
        var fallbackClient = mock(KafkaBotUpdatesClient.class);
        var client = new FallbackBotUpdatesClient(primaryClient, fallbackClient);
        var primaryException = new BotUpdatesClientException("HTTP failed", new IllegalStateException());
        var fallbackException = new BotUpdatesClientException("Kafka failed", new IllegalStateException());
        doThrow(primaryException)
                .when(primaryClient)
                .sendLinkUpdate(1L, URI.create("https://github.com/octocat/hello-world"), "Updated", List.of(1L));
        doThrow(fallbackException)
                .when(fallbackClient)
                .sendLinkUpdate(1L, URI.create("https://github.com/octocat/hello-world"), "Updated", List.of(1L));

        var thrown = assertThrows(
                BotUpdatesClientException.class,
                () -> client.sendLinkUpdate(
                        1L, URI.create("https://github.com/octocat/hello-world"), "Updated", List.of(1L)));

        assertSame(fallbackException, thrown);
        assertSame(primaryException, thrown.getSuppressed()[0]);
    }

    @Test
    void sendProcessingFailureReportFallsBackToKafkaWhenPrimaryHttpClientFails() {
        var primaryClient = mock(HttpBotUpdatesClient.class);
        var fallbackClient = mock(KafkaBotUpdatesClient.class);
        var client = new FallbackBotUpdatesClient(primaryClient, fallbackClient);
        doThrow(new BotUpdatesClientException("HTTP failed", new IllegalStateException()))
                .when(primaryClient)
                .sendProcessingFailureReport("Failed links", List.of(1L));

        assertDoesNotThrow(() -> client.sendProcessingFailureReport("Failed links", List.of(1L)));

        verify(fallbackClient).sendProcessingFailureReport("Failed links", List.of(1L));
    }
}
