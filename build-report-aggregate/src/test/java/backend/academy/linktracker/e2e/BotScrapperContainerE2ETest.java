package backend.academy.linktracker.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@org.testcontainers.junit.jupiter.Testcontainers(disabledWithoutDocker = true)
class BotScrapperContainerE2ETest {

    private static final String TELEGRAM_TOKEN = "test-token";
    private static final String KAFKA_NETWORK_BOOTSTRAP_SERVERS = "kafka:19092";
    private static final String SCHEMA_REGISTRY_URL = "http://schema-registry:8080";
    private static final int BOT_INTERNAL_PORT = 8080;
    private static final int SCRAPPER_INTERNAL_PORT = 8081;
    private static final int POSTGRES_INTERNAL_PORT = 5432;
    private static final String DB_NAME = "link_tracker";
    private static final String DB_USERNAME = "postgres";
    private static final String DB_PASSWORD = "postgres";
    private static final String LINK_UPDATES_TOPIC = "link-updates";
    private static final String LINK_UPDATES_DLQ_TOPIC = "link-updates-dlq";
    private static final String PROCESSING_FAILURE_REPORTS_TOPIC = "processing-failure-reports";
    private static final String PROCESSING_FAILURE_REPORTS_DLQ_TOPIC = "processing-failure-reports-dlq";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @Test
    void schedulerSendsNotificationFromScrapperToKafkaBotAndTelegram() throws Exception {
        var telegramBodies = new CopyOnWriteArrayList<String>();
        HttpServer telegramMockServer = null;
        HttpServer githubMockServer = null;

        try (var network = Network.newNetwork()) {
            telegramMockServer = HttpServer.create(new InetSocketAddress(0), 0);
            githubMockServer = HttpServer.create(new InetSocketAddress(0), 0);
            configureTelegramMock(telegramMockServer, telegramBodies);
            configureGithubMock(githubMockServer);
            telegramMockServer.start();
            githubMockServer.start();

            Testcontainers.exposeHostPorts(
                    telegramMockServer.getAddress().getPort(),
                    githubMockServer.getAddress().getPort());

            var postgresContainer = createPostgresContainer(network);
            var kafkaContainer = createKafkaContainer(network);
            var schemaRegistryContainer = createSchemaRegistryContainer(network);
            var botContainer =
                    createBotContainer(network, telegramMockServer.getAddress().getPort());
            var scrapperContainer = createScrapperContainer(
                    network, githubMockServer.getAddress().getPort());

            try {
                postgresContainer.start();
                kafkaContainer.start();
                schemaRegistryContainer.start();
                createNotificationTopics(kafkaContainer);
                botContainer.start();
                scrapperContainer.start();

                var scrapperPort = scrapperContainer.getMappedPort(SCRAPPER_INTERNAL_PORT);
                registerChat(scrapperPort, 1L);
                trackGithubLink(scrapperPort, 1L);

                Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                    assertThat(telegramBodies).isNotEmpty();
                    var body = telegramBodies.getFirst();
                    var decodedBody = URLDecoder.decode(body, StandardCharsets.UTF_8);
                    assertThat(decodedBody).contains("chat_id=1");
                    assertThat(decodedBody).contains("https://github.com/user/repo");
                });
            } finally {
                scrapperContainer.stop();
                botContainer.stop();
                schemaRegistryContainer.stop();
                kafkaContainer.stop();
                postgresContainer.stop();
            }
        } finally {
            if (telegramMockServer != null) {
                telegramMockServer.stop(0);
            }
            if (githubMockServer != null) {
                githubMockServer.stop(0);
            }
        }
    }

    private static void configureTelegramMock(HttpServer server, CopyOnWriteArrayList<String> bodies) {
        server.createContext("/bot" + TELEGRAM_TOKEN + "/sendMessage", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            bodies.add(body);
            writeJsonResponse(exchange, 200, "{\"ok\":true,\"result\":{\"message_id\":1}}");
        });
    }

    private static void configureGithubMock(HttpServer server) {
        server.createContext("/repos/user/repo", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            writeJsonResponse(exchange, 200, "{\"updated_at\":\"2099-01-01T00:00:00Z\"}");
        });
    }

    private static void writeJsonResponse(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        var response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static GenericContainer<?> createPostgresContainer(Network network) {
        return new GenericContainer<>(DockerImageName.parse("postgres:17-alpine"))
                .withNetwork(network)
                .withNetworkAliases("postgres")
                .withExposedPorts(POSTGRES_INTERNAL_PORT)
                .withEnv("POSTGRES_DB", DB_NAME)
                .withEnv("POSTGRES_USER", DB_USERNAME)
                .withEnv("POSTGRES_PASSWORD", DB_PASSWORD)
                .waitingFor(Wait.forListeningPort());
    }

    private static GenericContainer<?> createBotContainer(Network network, int telegramMockPort) throws IOException {
        return new GenericContainer<>(DockerImageName.parse("eclipse-temurin:25-jdk-alpine"))
                .withNetwork(network)
                .withNetworkAliases("bot")
                .withExposedPorts(BOT_INTERNAL_PORT)
                .withEnv("SERVER_PORT", Integer.toString(BOT_INTERNAL_PORT))
                .withEnv("APP_TELEGRAM_URL", "http://host.testcontainers.internal:" + telegramMockPort + "/bot")
                .withEnv("APP_TELEGRAM_TOKEN", TELEGRAM_TOKEN)
                .withEnv("APP_TELEGRAM_POLLING_ENABLED", "false")
                .withEnv("APP_TELEGRAM_SET_MY_COMMANDS_ENABLED", "false")
                .withEnv("APP_SCRAPPER_BASE_URL", "http://scrapper:" + SCRAPPER_INTERNAL_PORT)
                .withEnv("KAFKA_BOOTSTRAP_SERVERS", KAFKA_NETWORK_BOOTSTRAP_SERVERS)
                .withEnv("KAFKA_SCHEMA_REGISTRY_URL", SCHEMA_REGISTRY_URL)
                .withCopyFileToContainer(
                        MountableFile.forHostPath(findRepackagedJar("bot", "bot", "e2e")), "/app/bot.jar")
                .withCommand("java", "-jar", "/app/bot.jar")
                .waitingFor(Wait.forHttp("/actuator/health")
                        .forPort(BOT_INTERNAL_PORT)
                        .forStatusCode(200))
                .withStartupTimeout(Duration.ofMinutes(2));
    }

    private static GenericContainer<?> createScrapperContainer(Network network, int githubMockPort) throws IOException {
        return new GenericContainer<>(DockerImageName.parse("eclipse-temurin:25-jdk-alpine"))
                .withNetwork(network)
                .withNetworkAliases("scrapper")
                .withExposedPorts(SCRAPPER_INTERNAL_PORT)
                .withEnv("SERVER_PORT", Integer.toString(SCRAPPER_INTERNAL_PORT))
                .withEnv("SCRAPPER_DB_URL", "jdbc:postgresql://postgres:5432/" + DB_NAME)
                .withEnv("SCRAPPER_DB_USERNAME", DB_USERNAME)
                .withEnv("SCRAPPER_DB_PASSWORD", DB_PASSWORD)
                .withEnv("APP_BOT_BASE_URL", "http://bot:" + BOT_INTERNAL_PORT)
                .withEnv("APP_SCHEDULER_ENABLED", "true")
                .withEnv("APP_SCHEDULER_INTERVAL", "500")
                .withEnv("APP_GITHUB_BASE_URL", "http://host.testcontainers.internal:" + githubMockPort)
                .withEnv("KAFKA_BOOTSTRAP_SERVERS", KAFKA_NETWORK_BOOTSTRAP_SERVERS)
                .withEnv("KAFKA_SCHEMA_REGISTRY_URL", SCHEMA_REGISTRY_URL)
                .withCopyFileToContainer(
                        MountableFile.forHostPath(findRepackagedJar("scrapper", "scrapper", "e2e")),
                        "/app/scrapper.jar")
                .withCommand("java", "-jar", "/app/scrapper.jar")
                .waitingFor(Wait.forHttp("/actuator/health")
                        .forPort(SCRAPPER_INTERNAL_PORT)
                        .forStatusCode(200))
                .withStartupTimeout(Duration.ofMinutes(2));
    }

    private static KafkaContainer createKafkaContainer(Network network) {
        return new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"))
                .withNetwork(network)
                .withNetworkAliases("kafka")
                .withListener(KAFKA_NETWORK_BOOTSTRAP_SERVERS);
    }

    private static GenericContainer<?> createSchemaRegistryContainer(Network network) {
        return new GenericContainer<>(DockerImageName.parse("apicurio/apicurio-registry:3.2.1"))
                .withNetwork(network)
                .withNetworkAliases("schema-registry")
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/apis").forPort(8080).forStatusCode(200))
                .withStartupTimeout(Duration.ofMinutes(2));
    }

    private static void createNotificationTopics(KafkaContainer kafkaContainer) throws Exception {
        try (var adminClient = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers()))) {
            adminClient
                    .createTopics(List.of(
                            new NewTopic(LINK_UPDATES_TOPIC, 1, (short) 1),
                            new NewTopic(LINK_UPDATES_DLQ_TOPIC, 1, (short) 1),
                            new NewTopic(PROCESSING_FAILURE_REPORTS_TOPIC, 1, (short) 1),
                            new NewTopic(PROCESSING_FAILURE_REPORTS_DLQ_TOPIC, 1, (short) 1)))
                    .all()
                    .get();
        }
    }

    private static Path findRepackagedJar(String module, String prefix, String classifier) throws IOException {
        var currentDir = Path.of("").toAbsolutePath().normalize();
        var root = Files.exists(currentDir.resolve("bot")) && Files.exists(currentDir.resolve("scrapper"))
                ? currentDir
                : currentDir.getParent();
        if (root == null) {
            throw new IllegalStateException("Cannot resolve project root for E2E test");
        }
        var targetDir = root.resolve(module).resolve("target");

        try (var files = Files.list(targetDir)) {
            return files.filter(path -> path.getFileName().toString().startsWith(prefix + "-"))
                    .filter(path -> path.getFileName().toString().contains("-" + classifier + ".jar"))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> !path.getFileName().toString().endsWith(".jar.original"))
                    .max(Comparator.comparing(path -> path.getFileName().toString()))
                    .orElseThrow(() -> new IllegalStateException("Repackaged jar not found in " + targetDir));
        }
    }

    private static void registerChat(int scrapperPort, long chatId) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + scrapperPort + "/tg-chat/" + chatId))
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();
        var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }

    private static void trackGithubLink(int scrapperPort, long chatId) throws IOException, InterruptedException {
        var requestBody = """
                {
                  "link": "https://github.com/user/repo",
                  "tags": [],
                  "filters": []
                }
                """;
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + scrapperPort + "/links"))
                .header("Content-Type", "application/json")
                .header("Tg-Chat-Id", Long.toString(chatId))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(10))
                .build();
        var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }
}
