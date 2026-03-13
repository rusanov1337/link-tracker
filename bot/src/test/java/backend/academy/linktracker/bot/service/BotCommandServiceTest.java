package backend.academy.linktracker.bot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.bot.service.command.CommandExecutionService;
import backend.academy.linktracker.bot.service.command.CommandParser;
import backend.academy.linktracker.bot.service.command.CommandRegistry;
import backend.academy.linktracker.bot.service.command.HelpCommandHandler;
import backend.academy.linktracker.bot.service.command.StartCommandHandler;
import backend.academy.linktracker.bot.service.command.UnknownCommandHandler;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class BotCommandServiceTest {

    private final BotCommandService botCommandService = createService();

    @Test
    void startCommandWithPayloadIsHandled() {
        var response = botCommandService.createResponse(updateWithText("/start extra payload"));

        assertTrue(response.isPresent());
        assertEquals(12345L, response.orElseThrow().getChatId());
        assertEquals(
                BotCommandService.START_MESSAGE,
                response.orElseThrow().getParameters().get("text"));
    }

    @Test
    void startCommandWithMentionIsHandled() {
        var response = botCommandService.createResponse(updateWithText("/start@my_bot"));

        assertTrue(response.isPresent());
        assertEquals(
                BotCommandService.START_MESSAGE,
                response.orElseThrow().getParameters().get("text"));
    }

    @Test
    void unknownSlashCommandReturnsUnknownMessage() {
        var response = botCommandService.createResponse(updateWithText("/abracadabra"));

        assertTrue(response.isPresent());
        assertEquals(
                BotCommandService.UNKNOWN_COMMAND_MESSAGE,
                response.orElseThrow().getParameters().get("text"));
    }

    @Test
    void plainTextIsIgnored() {
        var response = botCommandService.createResponse(updateWithText("hello"));
        assertTrue(response.isEmpty());
    }

    @Test
    void helpMessageIsBuiltFromSupportedCommands() {
        var response = botCommandService.createResponse(updateWithText("/help"));

        assertTrue(response.isPresent());
        var helpText = String.valueOf(response.orElseThrow().getParameters().get("text"));
        assertTrue(helpText.startsWith("Доступные команды:"));
        assertTrue(helpText.contains("/start - Начать работу"));
        assertTrue(helpText.contains("/help - Список доступных команд"));
    }

    @Test
    void unsupportedUpdateIsIgnored() {
        var update = mock(Update.class);
        when(update.message()).thenReturn(null);
        when(update.updateId()).thenReturn(1);

        var response = botCommandService.createResponse(update);

        assertTrue(response.isEmpty());
    }

    @Test
    void knownAndUnknownCommandsIncrementMetrics() {
        var meterRegistry = new SimpleMeterRegistry();
        var unknownCommandHandler = new UnknownCommandHandler();
        var commandRegistry = new CommandRegistry(
                List.of(new StartCommandHandler(), new HelpCommandHandler(), unknownCommandHandler),
                unknownCommandHandler);
        var metricsService = new BotMetricsService(meterRegistry);
        var executionService = new CommandExecutionService(commandRegistry, metricsService);
        var service = new BotCommandService(new CommandParser(), executionService);

        service.createResponse(updateWithText("/start"));
        service.createResponse(updateWithText("/abracadabra"));

        assertEquals(
                1.0,
                meterRegistry
                        .find("commands_total")
                        .tag("command", "/start")
                        .tag("status", "success")
                        .counter()
                        .count());
        assertEquals(
                1.0,
                meterRegistry
                        .find("commands_total")
                        .tag("command", "/abracadabra")
                        .tag("status", "failure")
                        .counter()
                        .count());
    }

    private Update updateWithText(String text) {
        var user = mock(User.class);
        when(user.id()).thenReturn(77L);

        var chat = mock(Chat.class);
        when(chat.id()).thenReturn(12345L);

        var message = mock(Message.class);
        when(message.chat()).thenReturn(chat);
        when(message.from()).thenReturn(user);
        when(message.text()).thenReturn(text);

        var update = mock(Update.class);
        when(update.message()).thenReturn(message);
        return update;
    }

    private BotCommandService createService() {
        var unknownCommandHandler = new UnknownCommandHandler();
        var commandRegistry = new CommandRegistry(
                List.of(new StartCommandHandler(), new HelpCommandHandler(), unknownCommandHandler),
                unknownCommandHandler);
        var executionService =
                new CommandExecutionService(commandRegistry, new BotMetricsService(new SimpleMeterRegistry()));
        return new BotCommandService(new CommandParser(), executionService);
    }
}
