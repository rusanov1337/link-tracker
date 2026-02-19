package backend.academy.linktracker.bot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.verify;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.BotCommand;
import com.pengrad.telegrambot.request.SetMyCommands;
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
    void startPollingRegistersBotCommandsOnStartup() {
        var pollingListener = new TelegramPollingListener(telegramBot, botCommandService);

        pollingListener.startPolling();

        var requestCaptor = ArgumentCaptor.forClass(SetMyCommands.class);
        verify(telegramBot).execute(requestCaptor.capture());
        verify(telegramBot).setUpdatesListener(org.mockito.ArgumentMatchers.any(UpdatesListener.class));

        Object commands = requestCaptor.getValue().getParameters().get("commands");
        var botCommands = assertInstanceOf(BotCommand[].class, commands);

        assertEquals(2, botCommands.length);
        assertEquals("/start", botCommands[0].command());
        assertEquals("/help", botCommands[1].command());
    }
}
