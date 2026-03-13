package backend.academy.linktracker.bot.service.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                        .tag("command", "/abracadabra")
                        .tag("status", "failure")
                        .counter()
                        .count());
    }

    private CommandExecutionService commandExecutionService(SimpleMeterRegistry meterRegistry) {
        var unknownCommandHandler = new UnknownCommandHandler();
        var commandRegistry = new CommandRegistry(
                List.of(new StartCommandHandler(), new HelpCommandHandler(), unknownCommandHandler),
                unknownCommandHandler);
        return new CommandExecutionService(commandRegistry, new BotMetricsService(meterRegistry));
    }
}
