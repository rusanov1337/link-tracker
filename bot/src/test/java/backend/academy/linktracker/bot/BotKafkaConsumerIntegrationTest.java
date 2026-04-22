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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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

    @Test
    void malformedJsonIsSentToDlqWithoutTelegramDelivery() {
        kafkaTemplate.send(kafkaProperties.getTopics().getLinkUpdates(), "bad-json", "{\"id\":");

        var dlqRecord = awaitDlqRecord(kafkaProperties.getTopics().getLinkUpdatesDlq(), "bad-json");

        org.junit.jupiter.api.Assertions.assertEquals("{\"id\":", dlqRecord.value());
        verify(0, postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
    }

    @Test
    void invalidPayloadIsSentToDlqWithoutTelegramDelivery() throws Exception {
        kafkaTemplate.send(
                kafkaProperties.getTopics().getLinkUpdates(),
                "invalid-payload",
                OBJECT_MAPPER.writeValueAsString(
                        Map.of("id", 42L, "url", "not-url", "description", "", "tgChatIds", List.of())));

        var dlqRecord = awaitDlqRecord(kafkaProperties.getTopics().getLinkUpdatesDlq(), "invalid-payload");

        org.junit.jupiter.api.Assertions.assertTrue(dlqRecord.value().contains("\"url\":\"not-url\""));
        verify(0, postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
    }

    @Test
    void processingFailureIsRetriedAndThenSentToDlq() throws Exception {
        stubTelegramFailure();

        kafkaTemplate.send(
                kafkaProperties.getTopics().getLinkUpdates(),
                "delivery-failure",
                OBJECT_MAPPER.writeValueAsString(Map.of(
                        "id",
                        42L,
                        "url",
                        "https://github.com/octocat/hello-world",
                        "description",
                        "Updated",
                        "tgChatIds",
                        List.of(11L))));

        var dlqRecord = awaitDlqRecord(kafkaProperties.getTopics().getLinkUpdatesDlq(), "delivery-failure");

        org.junit.jupiter.api.Assertions.assertTrue(dlqRecord.value().contains("\"id\":42"));
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            verify(3, postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
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

    private void stubTelegramFailure() {
        stubFor(post(urlMatching("/bot[^/]+/sendMessage"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "ok": false,
                                  "error_code": 500,
                                  "description": "telegram failure"
                                }
                                """)));
    }

    private ConsumerRecord<String, String> awaitDlqRecord(String topic, String key) {
        try (var consumer = createConsumer()) {
            consumer.subscribe(List.of(topic));
            return Awaitility.await()
                    .atMost(Duration.ofSeconds(10))
                    .until(() -> pollMatchingRecord(consumer, key), java.util.Objects::nonNull);
        }
    }

    private KafkaConsumer<String, String> createConsumer() {
        var properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_CONTAINER.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "bot-kafka-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(properties);
    }

    private ConsumerRecord<String, String> pollMatchingRecord(KafkaConsumer<String, String> consumer, String key) {
        var records = consumer.poll(Duration.ofMillis(250));
        for (var record : records) {
            if (key.equals(record.key())) {
                return record;
            }
        }
        return null;
    }
}
