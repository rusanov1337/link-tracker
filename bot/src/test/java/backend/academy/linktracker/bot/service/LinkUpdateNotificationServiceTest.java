package backend.academy.linktracker.bot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.bot.api.dto.LinkUpdate;
import backend.academy.linktracker.bot.api.dto.ProcessingFailureReport;
import backend.academy.linktracker.bot.kafka.KafkaNotificationDeliveryException;
import backend.academy.linktracker.bot.properties.TelegramProperties;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.response.SendResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinkUpdateNotificationServiceTest {

    @Test
    void processStoresFailedDeliveriesForRetry() {
        var telegramBot = mock(TelegramBot.class);
        var pendingLinkUpdateStore = new PendingLinkUpdateStore();
        var pendingFailureReportStore = new PendingFailureReportStore();
        var service = newService(telegramBot, pendingLinkUpdateStore, pendingFailureReportStore);
        var successResponse = successResponse();
        var failureResponse = failureResponse();

        when(telegramBot.execute(any())).thenReturn(successResponse).thenReturn(failureResponse);

        service.process(new LinkUpdate(1L, "https://github.com/user/repo", "updated", List.of(11L, 22L)));

        assertEquals(1, pendingLinkUpdateStore.size());
        assertEquals(
                new PendingLinkUpdate(1L, 22L, "https://github.com/user/repo", "updated"),
                pendingLinkUpdateStore.findAll().getFirst());
    }

    @Test
    void retryPendingUpdatesRemovesSuccessfullyDeliveredItems() {
        var telegramBot = mock(TelegramBot.class);
        var pendingLinkUpdateStore = new PendingLinkUpdateStore();
        var pendingFailureReportStore = new PendingFailureReportStore();
        pendingLinkUpdateStore.saveAll(
                List.of(new PendingLinkUpdate(1L, 22L, "https://github.com/user/repo", "updated")));
        var service = newService(telegramBot, pendingLinkUpdateStore, pendingFailureReportStore);
        var successResponse = successResponse();

        when(telegramBot.execute(any())).thenReturn(successResponse);

        service.retryPendingUpdates();

        assertEquals(0, pendingLinkUpdateStore.size());
    }

    @Test
    void processStoresFailedDeliveriesWhenTelegramReturnsNullResponse() {
        var telegramBot = mock(TelegramBot.class);
        var pendingLinkUpdateStore = new PendingLinkUpdateStore();
        var pendingFailureReportStore = new PendingFailureReportStore();
        var service = newService(telegramBot, pendingLinkUpdateStore, pendingFailureReportStore);

        when(telegramBot.execute(any())).thenReturn(null);

        service.process(new LinkUpdate(1L, "https://github.com/user/repo", "updated", List.of(22L)));

        assertEquals(
                List.of(new PendingLinkUpdate(1L, 22L, "https://github.com/user/repo", "updated")),
                pendingLinkUpdateStore.findAll());
    }

    @Test
    void processDoesNotQueueDuplicatePendingUpdates() {
        var telegramBot = mock(TelegramBot.class);
        var pendingLinkUpdateStore = new PendingLinkUpdateStore();
        var pendingFailureReportStore = new PendingFailureReportStore();
        var service = newService(telegramBot, pendingLinkUpdateStore, pendingFailureReportStore);

        when(telegramBot.execute(any())).thenReturn(null);

        var update = new LinkUpdate(1L, "https://github.com/user/repo", "updated", List.of(22L));
        service.process(update);
        service.process(update);

        assertEquals(
                List.of(new PendingLinkUpdate(1L, 22L, "https://github.com/user/repo", "updated")),
                pendingLinkUpdateStore.findAll());
    }

    @Test
    void processSkipsRecentlyDeliveredUpdates() {
        var telegramBot = mock(TelegramBot.class);
        var pendingLinkUpdateStore = new PendingLinkUpdateStore();
        var pendingFailureReportStore = new PendingFailureReportStore();
        var service = newService(telegramBot, pendingLinkUpdateStore, pendingFailureReportStore);
        var successResponse = successResponse();

        when(telegramBot.execute(any())).thenReturn(successResponse);

        var update = new LinkUpdate(1L, "https://github.com/user/repo", "updated", List.of(22L));
        service.process(update);
        service.process(update);

        verify(telegramBot).execute(any());
        assertEquals(0, pendingLinkUpdateStore.size());
    }

    @Test
    void processReportSendsMessageToAllChatsWithoutQueueingPendingItems() {
        var telegramBot = mock(TelegramBot.class);
        var pendingLinkUpdateStore = new PendingLinkUpdateStore();
        var pendingFailureReportStore = new PendingFailureReportStore();
        var service = newService(telegramBot, pendingLinkUpdateStore, pendingFailureReportStore);
        var successResponse = successResponse();

        when(telegramBot.execute(any())).thenReturn(successResponse);

        service.processReport(new ProcessingFailureReport("Failed links", List.of(11L, 22L)));

        verify(telegramBot, org.mockito.Mockito.times(2)).execute(any());
        assertEquals(0, pendingLinkUpdateStore.size());
        assertEquals(0, pendingFailureReportStore.size());
    }

    @Test
    void processReportQueuesFailedDeliveriesForRetry() {
        var telegramBot = mock(TelegramBot.class);
        var pendingLinkUpdateStore = new PendingLinkUpdateStore();
        var pendingFailureReportStore = new PendingFailureReportStore();
        var service = newService(telegramBot, pendingLinkUpdateStore, pendingFailureReportStore);
        var failureResponse = failureResponse();

        when(telegramBot.execute(any())).thenReturn(failureResponse);

        service.processReport(new ProcessingFailureReport("Failed links", List.of(11L, 22L)));

        assertEquals(
                List.of(new PendingFailureReport(11L, "Failed links"), new PendingFailureReport(22L, "Failed links")),
                pendingFailureReportStore.findAll());
    }

    @Test
    void retryPendingUpdatesRemovesSuccessfullyDeliveredReports() {
        var telegramBot = mock(TelegramBot.class);
        var pendingLinkUpdateStore = new PendingLinkUpdateStore();
        var pendingFailureReportStore = new PendingFailureReportStore();
        pendingFailureReportStore.saveAll(List.of(new PendingFailureReport(22L, "Failed links")));
        var service = newService(telegramBot, pendingLinkUpdateStore, pendingFailureReportStore);
        var successResponse = successResponse();

        when(telegramBot.execute(any())).thenReturn(successResponse);

        service.retryPendingUpdates();

        assertEquals(0, pendingFailureReportStore.size());
    }

    @Test
    void processStrictThrowsWithoutQueueingPendingUpdates() {
        var telegramBot = mock(TelegramBot.class);
        var pendingLinkUpdateStore = new PendingLinkUpdateStore();
        var pendingFailureReportStore = new PendingFailureReportStore();
        var service = newService(telegramBot, pendingLinkUpdateStore, pendingFailureReportStore);
        var failureResponse = failureResponse();

        when(telegramBot.execute(any())).thenReturn(failureResponse);

        assertThrows(
                KafkaNotificationDeliveryException.class,
                () -> service.processStrict(
                        new LinkUpdate(1L, "https://github.com/user/repo", "updated", List.of(22L))));
        assertEquals(0, pendingLinkUpdateStore.size());
    }

    @Test
    void processReportStrictThrowsWithoutQueueingPendingReports() {
        var telegramBot = mock(TelegramBot.class);
        var pendingLinkUpdateStore = new PendingLinkUpdateStore();
        var pendingFailureReportStore = new PendingFailureReportStore();
        var service = newService(telegramBot, pendingLinkUpdateStore, pendingFailureReportStore);
        var failureResponse = failureResponse();

        when(telegramBot.execute(any())).thenReturn(failureResponse);

        assertThrows(
                KafkaNotificationDeliveryException.class,
                () -> service.processReportStrict(new ProcessingFailureReport("Failed links", List.of(22L))));
        assertEquals(0, pendingFailureReportStore.size());
    }

    private RecentlyDeliveredLinkUpdateStore recentlyDeliveredLinkUpdateStore() {
        return new RecentlyDeliveredLinkUpdateStore(new TelegramProperties());
    }

    private LinkUpdateNotificationService newService(
            TelegramBot telegramBot,
            PendingLinkUpdateStore pendingLinkUpdateStore,
            PendingFailureReportStore pendingFailureReportStore) {
        return new LinkUpdateNotificationService(
                telegramBot,
                pendingLinkUpdateStore,
                pendingFailureReportStore,
                recentlyDeliveredLinkUpdateStore(),
                new BotMetricsService(new SimpleMeterRegistry()));
    }

    private SendResponse successResponse() {
        var response = mock(SendResponse.class);
        when(response.isOk()).thenReturn(true);
        return response;
    }

    private SendResponse failureResponse() {
        var response = mock(SendResponse.class);
        when(response.isOk()).thenReturn(false);
        when(response.errorCode()).thenReturn(500);
        when(response.description()).thenReturn("send failed");
        return response;
    }
}
