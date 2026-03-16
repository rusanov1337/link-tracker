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
    void canonicalizesEquivalentSupportedUrls() {
        var github = parser.parseHttpUrl("https://github.com/User/Repo/?tab=readme#top");
        var stackoverflow = parser.parseHttpUrl("https://stackoverflow.com/q/12345/example?sort=votes#answer");

        assertTrue(github.isPresent());
        assertTrue(stackoverflow.isPresent());
        assertEquals("https://github.com/user/repo", github.orElseThrow());
        assertEquals("https://stackoverflow.com/questions/12345", stackoverflow.orElseThrow());
    }

    @Test
    void rejectsNonHttpSchemesAndMalformedLinks() {
        assertTrue(parser.parseHttpUrl("ftp://example.com").isEmpty());
        assertTrue(parser.parseHttpUrl("tbank://github.com/user/repo").isEmpty());
        assertTrue(parser.parseHttpUrl("not-a-url").isEmpty());
    }

    @Test
    void rejectsUnsupportedHosts() {
        assertTrue(parser.parseHttpUrl("https://example.com/article").isEmpty());
    }

    @Test
    void rejectsUnsupportedResourceTypesOnSupportedHosts() {
        assertTrue(parser.parseHttpUrl("https://github.com/user/repo/issues/1").isEmpty());
        assertTrue(parser.parseHttpUrl("https://stackoverflow.com/users/12345/example")
                .isEmpty());
    }
}
