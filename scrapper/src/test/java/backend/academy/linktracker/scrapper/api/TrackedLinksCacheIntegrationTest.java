package backend.academy.linktracker.scrapper.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import backend.academy.linktracker.scrapper.DatabaseCleanupSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.scheduler.enabled=false",
            "app.cache.tracked-links.enabled=true",
            "app.cache.tracked-links.ttl=1s",
            "app.cache.tracked-links.key-prefix=test-tracked-links",
            "springdoc.api-docs.enabled=false",
            "springdoc.swagger-ui.enabled=false"
        })
@Import(backend.academy.linktracker.scrapper.TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TrackedLinksCacheIntegrationTest extends DatabaseCleanupSupport {

    private static final String TG_CHAT_HEADER = "Tg-Chat-Id";
    private static final int VALKEY_PORT = 6379;

    @Container
    static final GenericContainer<?> VALKEY = new GenericContainer<>(DockerImageName.parse("valkey/valkey:8-alpine"))
            .withExposedPorts(VALKEY_PORT)
            .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", VALKEY::getHost);
        registry.add("spring.data.redis.port", () -> VALKEY.getMappedPort(VALKEY_PORT));
    }

    @LocalServerPort
    private int port;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcClient jdbcClient;

    private HttpClient httpClient;

    @BeforeEach
    void setupCacheTest() {
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var connectionFactory = Objects.requireNonNull(redisTemplate.getConnectionFactory());
        try (var connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @Test
    void getLinksCachesResponseJsonInValkey() throws Exception {
        registerChat(101L);
        assertEquals(200, addLink(101L, "https://github.com/user/repo").statusCode());

        var response = getLinks(101L);

        assertEquals(200, response.statusCode());
        assertThat(cachedJson(101L))
                .contains("\"size\":1")
                .contains("\"url\":\"https://github.com/user/repo\"")
                .contains("\"tags\":[\"work\"]")
                .contains("\"filters\":[\"new\"]");
    }

    @Test
    void getLinksUsesCachedResponseOnCacheHit() throws Exception {
        registerChat(102L);
        assertEquals(200, addLink(102L, "https://github.com/user/repo").statusCode());
        assertEquals(200, getLinks(102L).statusCode());

        jdbcClient
                .sql("delete from subscriptions where chat_id = :chatId")
                .param("chatId", 102L)
                .update();

        var cachedResponse = getLinks(102L);

        assertEquals(200, cachedResponse.statusCode());
        assertThat(cachedResponse.body()).contains("https://github.com/user/repo");
    }

    @Test
    void addLinkInvalidatesCachedResponse() throws Exception {
        registerChat(103L);
        assertEquals(200, getLinks(103L).statusCode());
        assertThat(cachedJson(103L)).contains("\"size\":0");

        assertEquals(200, addLink(103L, "https://github.com/user/repo").statusCode());

        assertThat(cachedJson(103L)).isNull();
        var response = getLinks(103L);
        assertEquals(200, response.statusCode());
        assertThat(response.body()).contains("https://github.com/user/repo");
    }

    @Test
    void removeLinkInvalidatesCachedResponse() throws Exception {
        registerChat(104L);
        assertEquals(200, addLink(104L, "https://github.com/user/repo").statusCode());
        assertEquals(200, getLinks(104L).statusCode());
        assertThat(cachedJson(104L)).contains("\"size\":1");

        assertEquals(200, removeLink(104L, "https://github.com/user/repo").statusCode());

        assertThat(cachedJson(104L)).isNull();
        var response = getLinks(104L);
        assertEquals(200, response.statusCode());
        assertThat(response.body()).contains("\"size\":0");
    }

    @Test
    void cachedResponseExpiresAfterConfiguredTtl() throws Exception {
        registerChat(105L);
        assertEquals(200, getLinks(105L).statusCode());
        assertThat(cachedJson(105L)).isNotNull();

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(cachedJson(105L))
                .isNull());
    }

    private String cachedJson(long chatId) {
        return redisTemplate.opsForValue().get(cacheKey(chatId));
    }

    private static String cacheKey(long chatId) {
        return "test-tracked-links:" + chatId;
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
        return send("POST", "/links", requestBody, Map.of(TG_CHAT_HEADER, Long.toString(chatId)));
    }

    private HttpResponse<String> removeLink(long chatId, String link) throws Exception {
        var requestBody = """
                {
                  "link": "%s"
                }
                """.formatted(link);
        return send("DELETE", "/links", requestBody, Map.of(TG_CHAT_HEADER, Long.toString(chatId)));
    }

    private HttpResponse<String> getLinks(long chatId) throws Exception {
        return send("GET", "/links", null, Map.of(TG_CHAT_HEADER, Long.toString(chatId)));
    }

    private HttpResponse<String> send(String method, String path, String body, Map<String, String> headers)
            throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10));
        headers.forEach(builder::header);
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
