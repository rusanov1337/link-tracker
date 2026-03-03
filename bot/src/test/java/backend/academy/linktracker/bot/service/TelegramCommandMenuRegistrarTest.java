package backend.academy.linktracker.bot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.BotCommand;
import com.pengrad.telegrambot.request.SetMyCommands;
import com.pengrad.telegrambot.response.BaseResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelegramCommandMenuRegistrarTest {

    @Mock
    private TelegramBot telegramBot;

    @Mock
    private BotCommandService botCommandService;

    @Mock
    private BaseResponse baseResponse;

    @Test
    void registerCommandsSendsSetMyCommandsRequest() {
        var registrar = new TelegramCommandMenuRegistrar(telegramBot, botCommandService);
        when(botCommandService.supportedCommands())
                .thenReturn(List.of(
                        new BotCommandDefinition("/start", "Начать работу"),
                        new BotCommandDefinition("/help", "Список доступных команд")));
        when(baseResponse.isOk()).thenReturn(true);
        when(telegramBot.execute(org.mockito.ArgumentMatchers.any(SetMyCommands.class)))
                .thenReturn(baseResponse);

        registrar.registerCommands();

        var requestCaptor = ArgumentCaptor.forClass(SetMyCommands.class);
        verify(telegramBot).execute(requestCaptor.capture());

        Object commands = requestCaptor.getValue().getParameters().get("commands");
        var botCommands = assertInstanceOf(BotCommand[].class, commands);
        assertEquals(2, botCommands.length);
        assertEquals("/start", botCommands[0].command());
        assertEquals("/help", botCommands[1].command());
    }
}
