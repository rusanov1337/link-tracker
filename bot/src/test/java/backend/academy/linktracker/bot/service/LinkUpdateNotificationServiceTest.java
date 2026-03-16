package backend.academy.linktracker.bot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.bot.api.dto.LinkUpdate;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.response.SendResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinkUpdateNotificationServiceTest {

    @Test
    void processStoresFailedDeliveriesForRetry() {
        var telegramBot = mock(TelegramBot.class);
        var pendingLinkUpdateStore = new PendingLinkUpdateStore();
        var service = new LinkUpdateNotificationService(telegramBot, pendingLinkUpdateStore);
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
        pendingLinkUpdateStore.saveAll(
                List.of(new PendingLinkUpdate(1L, 22L, "https://github.com/user/repo", "updated")));
        var service = new LinkUpdateNotificationService(telegramBot, pendingLinkUpdateStore);
        var successResponse = successResponse();

        when(telegramBot.execute(any())).thenReturn(successResponse);

        service.retryPendingUpdates();

        assertEquals(0, pendingLinkUpdateStore.size());
    }

    @Test
    void processStoresFailedDeliveriesWhenTelegramReturnsNullResponse() {
        var telegramBot = mock(TelegramBot.class);
        var pendingLinkUpdateStore = new PendingLinkUpdateStore();
        var service = new LinkUpdateNotificationService(telegramBot, pendingLinkUpdateStore);

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
        var service = new LinkUpdateNotificationService(telegramBot, pendingLinkUpdateStore);

        when(telegramBot.execute(any())).thenReturn(null);

        var update = new LinkUpdate(1L, "https://github.com/user/repo", "updated", List.of(22L));
        service.process(update);
        service.process(update);

        assertEquals(
                List.of(new PendingLinkUpdate(1L, 22L, "https://github.com/user/repo", "updated")),
                pendingLinkUpdateStore.findAll());
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
