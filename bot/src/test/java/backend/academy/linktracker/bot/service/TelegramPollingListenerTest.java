package backend.academy.linktracker.bot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.bot.properties.TelegramProperties;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelegramPollingListenerTest {

    @Mock
    private TelegramBot telegramBot;

    @Mock
    private BotCommandService botCommandService;

    @Test
    void startPollingRegistersUpdatesListener() {
        var pollingListener = new TelegramPollingListener(
                telegramBot, botCommandService, metrics(new SimpleMeterRegistry()), telegramProperties());

        pollingListener.startPolling();

        verify(telegramBot).setUpdatesListener(org.mockito.ArgumentMatchers.any(UpdatesListener.class));
    }

    @Test
    void processRetriesSendMessageAfterExecuteFailure() {
        var meterRegistry = new SimpleMeterRegistry();
        var pollingListener = new TelegramPollingListener(
                telegramBot, botCommandService, metrics(meterRegistry), telegramProperties());

        pollingListener.startPolling();

        var listenerCaptor = ArgumentCaptor.forClass(UpdatesListener.class);
        verify(telegramBot).setUpdatesListener(listenerCaptor.capture());
        var updatesListener = listenerCaptor.getValue();

        var update = org.mockito.Mockito.mock(Update.class);
        when(update.updateId()).thenReturn(123);

        var sendMessage = new SendMessage(999L, "response");
        when(botCommandService.createResponse(update)).thenReturn(Optional.of(sendMessage));

        var okResponse = org.mockito.Mockito.mock(SendResponse.class);
        when(okResponse.isOk()).thenReturn(true);
        when(telegramBot.execute(org.mockito.ArgumentMatchers.any(SendMessage.class)))
                .thenThrow(new RuntimeException("network glitch"))
                .thenReturn(okResponse);

        int result = updatesListener.process(List.of(update));

        assertEquals(UpdatesListener.CONFIRMED_UPDATES_ALL, result);
        verify(telegramBot, org.mockito.Mockito.times(2)).execute(org.mockito.ArgumentMatchers.any(SendMessage.class));
        assertEquals(1.0, meterRegistry.find("send_failures_total").counter().count());
    }

    private BotMetricsService metrics(SimpleMeterRegistry meterRegistry) {
        return new BotMetricsService(meterRegistry);
    }

    private TelegramProperties telegramProperties() {
        var properties = new TelegramProperties();
        properties.setMaxSendAttempts(3);
        return properties;
    }
}
