package backend.academy.linktracker.bot.service.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CommandParserTest {

    private final CommandParser commandParser = new CommandParser();

    @Test
    void parseCommandWithMentionAndArguments() {
        var parsed = commandParser.parse("/track@my_bot https://example.com", 1L, 2L);

        assertTrue(parsed.isPresent());
        assertEquals("/track", parsed.orElseThrow().command());
        assertEquals("https://example.com", parsed.orElseThrow().arguments());
        assertEquals(1L, parsed.orElseThrow().chatId());
        assertEquals(2L, parsed.orElseThrow().userId());
    }

    @Test
    void nonCommandTextIsIgnored() {
        var parsed = commandParser.parse("hello world", 1L, 2L);
        assertTrue(parsed.isEmpty());
    }

    @Test
    void slashWithoutCommandIsIgnored() {
        var parsed = commandParser.parse("/", 1L, 2L);
        assertTrue(parsed.isEmpty());
    }
}
