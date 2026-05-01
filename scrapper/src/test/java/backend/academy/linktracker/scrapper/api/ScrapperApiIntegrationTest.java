package backend.academy.linktracker.scrapper.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.DatabaseCleanupSupport;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.scheduler.enabled=false",
            "app.cache.tracked-links.enabled=false",
            "springdoc.api-docs.enabled=false",
            "springdoc.swagger-ui.enabled=false"
        })
@Import(backend.academy.linktracker.scrapper.TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ScrapperApiIntegrationTest extends DatabaseCleanupSupport {

    private static final String TG_CHAT_HEADER = "Tg-Chat-Id";

    @LocalServerPort
    private int port;

    private HttpClient httpClient;

    @BeforeEach
    void setup() {
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Test
    void addAndGetLink() throws Exception {
        registerChat(1L);

        var addResponse = addLink(1L, "https://github.com/user/repo");
        assertEquals(200, addResponse.statusCode());

        var getResponse = send("GET", "/links", null, Map.of(TG_CHAT_HEADER, "1"));
        assertEquals(200, getResponse.statusCode());
        assertBodyContains(getResponse, "\"size\"\\s*:\\s*1");
        assertBodyContains(getResponse, "https://github\\.com/user/repo");
        assertBodyContains(getResponse, "\"tags\"\\s*:\\s*\\[\\s*\"work\"\\s*]");
    }

    @Test
    void duplicateChatRegistrationReturnsConflict() throws Exception {
        registerChat(1L);

        var duplicateResponse = send("POST", "/tg-chat/1", null, Map.of());
        assertEquals(409, duplicateResponse.statusCode());
        assertBodyContains(duplicateResponse, "\"code\"\\s*:\\s*\"409\"");
    }

    @Test
    void addThenDeleteLinkRemovesItFromList() throws Exception {
        registerChat(1L);
        assertEquals(200, addLink(1L, "https://stackoverflow.com/questions/123").statusCode());

        var removeResponse = removeLink(1L, "https://stackoverflow.com/questions/123");
        assertEquals(200, removeResponse.statusCode());

        var getResponse = send("GET", "/links", null, Map.of(TG_CHAT_HEADER, "1"));
        assertEquals(200, getResponse.statusCode());
        assertBodyContains(getResponse, "\"size\"\\s*:\\s*0");
        assertBodyContains(getResponse, "\"links\"\\s*:\\s*\\[\\s*]");
    }

    @Test
    void duplicateLinkTrackingReturnsConflict() throws Exception {
        registerChat(1L);
        assertEquals(200, addLink(1L, "https://github.com/user/repo").statusCode());

        var duplicateResponse = addLink(1L, "https://github.com/user/repo");
        assertEquals(409, duplicateResponse.statusCode());
        assertBodyContains(duplicateResponse, "\"code\"\\s*:\\s*\"409\"");
    }

    @Test
    void duplicateLinkTrackingIgnoresSchemeAndHostCase() throws Exception {
        registerChat(1L);
        assertEquals(200, addLink(1L, "https://github.com/user/repo").statusCode());

        var duplicateResponse = addLink(1L, "HTTPS://GITHUB.COM/user/repo");
        assertEquals(409, duplicateResponse.statusCode());
        assertBodyContains(duplicateResponse, "\"code\"\\s*:\\s*\"409\"");
    }

    @Test
    void duplicateLinkTrackingIgnoresGithubOwnerAndRepoCase() throws Exception {
        registerChat(1L);
        assertEquals(200, addLink(1L, "https://github.com/User/Repo").statusCode());

        var duplicateResponse = addLink(1L, "https://github.com/user/repo");
        assertEquals(409, duplicateResponse.statusCode());
        assertBodyContains(duplicateResponse, "\"code\"\\s*:\\s*\"409\"");
    }

    @Test
    void duplicateLinkTrackingIgnoresSupportedUrlDecorations() throws Exception {
        registerChat(1L);
        assertEquals(200, addLink(1L, "https://github.com/user/repo/").statusCode());

        var duplicateResponse = addLink(1L, "https://github.com/user/repo?tab=readme#top");
        assertEquals(409, duplicateResponse.statusCode());
        assertBodyContains(duplicateResponse, "\"code\"\\s*:\\s*\"409\"");
    }

    @Test
    void removingLinkForUnknownChatReturnsErrorAndKeepsData() throws Exception {
        registerChat(1L);
        assertEquals(200, addLink(1L, "https://github.com/user/project").statusCode());

        var removeResponse = removeLink(999L, "https://github.com/user/project");
        assertEquals(404, removeResponse.statusCode());
        assertBodyContains(removeResponse, "\"code\"\\s*:\\s*\"404\"");

        var getResponse = send("GET", "/links", null, Map.of(TG_CHAT_HEADER, "1"));
        assertEquals(200, getResponse.statusCode());
        assertBodyContains(getResponse, "\"size\"\\s*:\\s*1");
        assertBodyContains(getResponse, "https://github\\.com/user/project");
    }

    @Test
    void removeLinkMatchesRegardlessOfSchemeAndHostCase() throws Exception {
        registerChat(1L);
        assertEquals(200, addLink(1L, "https://github.com/user/project").statusCode());

        var removeResponse = removeLink(1L, "HTTPS://GITHUB.COM/user/project");
        assertEquals(200, removeResponse.statusCode());

        var getResponse = send("GET", "/links", null, Map.of(TG_CHAT_HEADER, "1"));
        assertEquals(200, getResponse.statusCode());
        assertBodyContains(getResponse, "\"size\"\\s*:\\s*0");
    }

    @Test
    void removeLinkMatchesGithubOwnerAndRepoCaseInsensitively() throws Exception {
        registerChat(1L);
        assertEquals(200, addLink(1L, "https://github.com/User/Project").statusCode());

        var removeResponse = removeLink(1L, "https://github.com/user/project");
        assertEquals(200, removeResponse.statusCode());

        var getResponse = send("GET", "/links", null, Map.of(TG_CHAT_HEADER, "1"));
        assertEquals(200, getResponse.statusCode());
        assertBodyContains(getResponse, "\"size\"\\s*:\\s*0");
    }

    @Test
    void removeLinkMatchesEquivalentSupportedUrl() throws Exception {
        registerChat(1L);
        assertEquals(200, addLink(1L, "https://stackoverflow.com/q/123").statusCode());

        var removeResponse = removeLink(1L, "https://stackoverflow.com/questions/123/example?sort=votes#answer");
        assertEquals(200, removeResponse.statusCode());

        var getResponse = send("GET", "/links", null, Map.of(TG_CHAT_HEADER, "1"));
        assertEquals(200, getResponse.statusCode());
        assertBodyContains(getResponse, "\"size\"\\s*:\\s*0");
    }

    @Test
    void removingNonTrackedLinkReturnsNotFound() throws Exception {
        registerChat(1L);

        var removeResponse = removeLink(1L, "https://github.com/user/unknown");
        assertEquals(404, removeResponse.statusCode());
        assertBodyContains(removeResponse, "\"code\"\\s*:\\s*\"404\"");
    }

    @Test
    void addingLinkForUnknownChatReturnsError() throws Exception {
        var response = addLink(2L, "https://github.com/ghost/repo");
        assertEquals(404, response.statusCode());
        assertBodyContains(response, "\"code\"\\s*:\\s*\"404\"");
    }

    @Test
    void addingLinkWithInvalidBodyReturnsBadRequest() throws Exception {
        registerChat(1L);

        var invalidBody = """
                {
                  "link": "not-url",
                  "tags": ["work"]
                }
                """;
        var response = send("POST", "/links", invalidBody, Map.of(TG_CHAT_HEADER, "1"));
        assertEquals(400, response.statusCode());
        assertBodyContains(response, "\"code\"\\s*:\\s*\"400\"");
    }

    @Test
    void addingLinkWithUnsupportedHostReturnsBadRequest() throws Exception {
        registerChat(1L);

        var response = addLink(1L, "https://example.com/page");
        assertEquals(400, response.statusCode());
        assertBodyContains(response, "\"code\"\\s*:\\s*\"400\"");
    }

    @Test
    void addingLinkWithUnsupportedResourceTypeReturnsBadRequest() throws Exception {
        registerChat(1L);

        var githubIssueResponse = addLink(1L, "https://github.com/user/repo/issues/1");
        assertEquals(400, githubIssueResponse.statusCode());
        assertBodyContains(githubIssueResponse, "\"code\"\\s*:\\s*\"400\"");

        var stackoverflowUserResponse = addLink(1L, "https://stackoverflow.com/users/12345/example");
        assertEquals(400, stackoverflowUserResponse.statusCode());
        assertBodyContains(stackoverflowUserResponse, "\"code\"\\s*:\\s*\"400\"");
    }

    @Test
    void missingChatHeaderReturnsBadRequest() throws Exception {
        var response = send("GET", "/links", null, Map.of());
        assertEquals(400, response.statusCode());
        assertBodyContains(response, "\"code\"\\s*:\\s*\"400\"");
    }

    @Test
    void removedChatCannotTrackLinks() throws Exception {
        registerChat(1L);
        assertEquals(200, send("DELETE", "/tg-chat/1", null, Map.of()).statusCode());

        var response = addLink(1L, "https://github.com/user/repo");
        assertEquals(404, response.statusCode());
        assertBodyContains(response, "\"code\"\\s*:\\s*\"404\"");
    }

    @Test
    void deletingUnknownChatReturnsNotFound() throws Exception {
        var response = send("DELETE", "/tg-chat/1", null, Map.of());
        assertEquals(404, response.statusCode());
        assertBodyContains(response, "\"code\"\\s*:\\s*\"404\"");
    }

    private void registerChat(long chatId) throws Exception {
        var response = send("POST", "/tg-chat/" + chatId, null, Map.of());
        assertEquals(200, response.statusCode());
    }

    private HttpResponse<String> addLink(long chatId, String link) throws Exception {
        var requestBody = """
                {
                  "link": "%s",
                  "tags": ["work"],
                  "filters": ["new"]
                }
                """.formatted(link);
        return send("POST", "/links", requestBody, Map.of(TG_CHAT_HEADER, String.valueOf(chatId)));
    }

    private HttpResponse<String> removeLink(long chatId, String link) throws Exception {
        var requestBody = """
                {
                  "link": "%s"
                }
                """.formatted(link);
        return send("DELETE", "/links", requestBody, Map.of(TG_CHAT_HEADER, String.valueOf(chatId)));
    }

    private HttpResponse<String> send(String method, String path, String body, Map<String, String> headers)
            throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10));
        headers.forEach(builder::header);
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertBodyContains(HttpResponse<String> response, String regex) {
        assertTrue(
                Pattern.compile(regex).matcher(response.body()).find(),
                "Expected pattern not found. pattern=" + regex + ", body=" + response.body());
    }
}
