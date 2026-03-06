package backend.academy.linktracker.bot.service.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LinkInputParserTest {

    private final LinkInputParser parser = new LinkInputParser();

    @Test
    void parsesAndNormalizesHttpUrl() {
        var parsed = parser.parseHttpUrl(" https://github.com/user/repo/../repo ");

        assertTrue(parsed.isPresent());
        assertEquals("https://github.com/user/repo", parsed.orElseThrow());
    }

    @Test
    void rejectsNonHttpSchemesAndMalformedLinks() {
        assertTrue(parser.parseHttpUrl("ftp://example.com").isEmpty());
        assertTrue(parser.parseHttpUrl("tbank://github.com/user/repo").isEmpty());
        assertTrue(parser.parseHttpUrl("not-a-url").isEmpty());
    }
}
