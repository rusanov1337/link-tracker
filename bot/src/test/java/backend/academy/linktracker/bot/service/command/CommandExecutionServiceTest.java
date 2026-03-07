package backend.academy.linktracker.bot.service.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import backend.academy.linktracker.bot.client.scrapper.ScrapperClient;
import backend.academy.linktracker.bot.service.BotMetricsService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandExecutionServiceTest {

    @Test
    void knownAndUnknownCommandsIncrementMetricsAndReturnResponses() {
        var meterRegistry = new SimpleMeterRegistry();
        var service = commandExecutionService(meterRegistry);

        var knownResponse = service.handle(new CommandRequest("/start", "", "/start", 1001L, 77L));
        var unknownResponse = service.handle(new CommandRequest("/abracadabra", "", "/abracadabra", 1001L, 77L));

        assertEquals(StartCommandHandler.RESPONSE, knownResponse);
        assertEquals(UnknownCommandHandler.RESPONSE, unknownResponse);
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
                        .tag("command", "unknown")
                        .tag("status", "success")
                        .counter()
                        .count());
    }

    @Test
    void failingHandlerIncrementsFailureMetric() {
        var meterRegistry = new SimpleMeterRegistry();
        var unknownCommandHandler = new UnknownCommandHandler();
        var commandRegistry =
                new CommandRegistry(List.of(new FailingCommandHandler(), unknownCommandHandler), unknownCommandHandler);
        var service = new CommandExecutionService(commandRegistry, new BotMetricsService(meterRegistry));

        assertThrows(
                IllegalStateException.class,
                () -> service.handle(new CommandRequest("/boom", "", "/boom", 1001L, 77L)));
        assertEquals(
                1.0,
                meterRegistry
                        .find("commands_total")
                        .tag("command", "/boom")
                        .tag("status", "failure")
                        .counter()
                        .count());
    }

    private CommandExecutionService commandExecutionService(SimpleMeterRegistry meterRegistry) {
        var scrapperClient = mock(ScrapperClient.class);
        var unknownCommandHandler = new UnknownCommandHandler();
        var commandRegistry = new CommandRegistry(
                List.of(new StartCommandHandler(scrapperClient), new HelpCommandHandler(), unknownCommandHandler),
                unknownCommandHandler);
        return new CommandExecutionService(commandRegistry, new BotMetricsService(meterRegistry));
    }

    private static final class FailingCommandHandler implements CommandHandler {

        @Override
        public String command() {
            return "/boom";
        }

        @Override
        public String description() {
            return "Failing command";
        }

        @Override
        public String handle(
                CommandRequest request,
                List<backend.academy.linktracker.bot.service.BotCommandDefinition> supportedCommands) {
            throw new IllegalStateException("boom");
        }
    }
}
