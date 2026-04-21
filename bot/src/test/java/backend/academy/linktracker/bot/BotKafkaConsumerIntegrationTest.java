package backend.academy.linktracker.bot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

import backend.academy.linktracker.bot.properties.NotificationKafkaProperties;
import backend.academy.linktracker.bot.service.RecentlyDeliveredLinkUpdateStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest
@ActiveProfiles("test")
@EnableWireMock
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
        properties = {
            "app.kafka.consumer.enabled=true",
            "app.telegram.polling-enabled=false",
            "app.telegram.set-my-commands-enabled=false"
        })
class BotKafkaConsumerIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Container
    static final KafkaContainer KAFKA_CONTAINER =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA_CONTAINER::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private NotificationKafkaProperties kafkaProperties;

    @Autowired
    private RecentlyDeliveredLinkUpdateStore recentlyDeliveredLinkUpdateStore;

    @BeforeEach
    void setUp() {
        recentlyDeliveredLinkUpdateStore.clear();
    }

    @Test
    void validLinkUpdateMessageIsDeliveredToTelegram() throws Exception {
        stubTelegramSuccess();

        kafkaTemplate.send(
                kafkaProperties.getTopics().getLinkUpdates(),
                "42",
                OBJECT_MAPPER.writeValueAsString(Map.of(
                        "id",
                        42L,
                        "url",
                        "https://github.com/octocat/hello-world",
                        "description",
                        "Updated",
                        "tgChatIds",
                        java.util.List.of(11L, 22L))));

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            verify(2, postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
        });
    }

    @Test
    void validProcessingFailureReportMessageIsDeliveredToTelegram() throws Exception {
        stubTelegramSuccess();

        kafkaTemplate.send(
                kafkaProperties.getTopics().getProcessingFailureReports(),
                "report-11",
                OBJECT_MAPPER.writeValueAsString(
                        Map.of("description", "Failed links report", "tgChatIds", java.util.List.of(11L, 22L))));

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            verify(2, postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
        });
    }

    private void stubTelegramSuccess() {
        stubFor(post(urlMatching("/bot[^/]+/sendMessage"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "ok": true,
                                  "result": {
                                    "message_id": 100
                                  }
                                }
                                """)));
    }
}
