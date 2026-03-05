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
                        .tag("type", "known")
                        .counter()
                        .count());
        assertEquals(
                1.0,
                meterRegistry
                        .find("commands_total")
                        .tag("type", "unknown")
                        .counter()
                        .count());
    }

    @Test
    void supportedCommandsDoesNotIncludeFallbackAndKeepsOrder() {
        var service = commandExecutionService(new SimpleMeterRegistry());

        var supportedCommands = service.supportedCommands();

        assertEquals(2, supportedCommands.size());
        assertEquals(StartCommandHandler.COMMAND, supportedCommands.getFirst().command());
        assertEquals(
                StartCommandHandler.DESCRIPTION, supportedCommands.getFirst().description());
        assertEquals(HelpCommandHandler.COMMAND, supportedCommands.get(1).command());
        assertEquals(HelpCommandHandler.DESCRIPTION, supportedCommands.get(1).description());
    }

    private CommandExecutionService commandExecutionService(SimpleMeterRegistry meterRegistry) {
        var commandRegistry = new CommandRegistry(
                List.of(new StartCommandHandler(), new HelpCommandHandler(), new UnknownCommandHandler()));
        return new CommandExecutionService(commandRegistry, new BotMetricsService(meterRegistry));
    }
}
