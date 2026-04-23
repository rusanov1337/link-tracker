package backend.academy.linktracker.bot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

import backend.academy.linktracker.bot.properties.NotificationKafkaProperties;
import backend.academy.linktracker.bot.service.RecentlyDeliveredLinkUpdateStore;
import backend.academy.linktracker.kafka.avro.LinkUpdateEvent;
import backend.academy.linktracker.kafka.avro.ProcessingFailureReportEvent;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
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

    @Container
    static final KafkaContainer KAFKA_CONTAINER =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Container
    static final GenericContainer<?> SCHEMA_REGISTRY_CONTAINER = new GenericContainer<>(
                    DockerImageName.parse("apicurio/apicurio-registry:3.2.1"))
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/apis").forPort(8080).forStatusCode(200));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA_CONTAINER::getBootstrapServers);
        registry.add(
                "spring.kafka.properties.apicurio.registry.url", BotKafkaConsumerIntegrationTest::getRegistryApiUrl);
    }

    @Autowired
    @Qualifier("avroKafkaTemplate")
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private NotificationKafkaProperties kafkaProperties;

    @Autowired
    private RecentlyDeliveredLinkUpdateStore recentlyDeliveredLinkUpdateStore;

    @BeforeEach
    void setUp() {
        recentlyDeliveredLinkUpdateStore.clear();
    }

    @Test
    void validLinkUpdateMessageIsDeliveredToTelegram() {
        stubTelegramSuccess();

        kafkaTemplate.send(
                kafkaProperties.getTopics().getLinkUpdates(),
                "42",
                LinkUpdateEvent.newBuilder()
                        .setId(42L)
                        .setUrl("https://github.com/octocat/hello-world")
                        .setDescription("Updated")
                        .setTgChatIds(List.of(11L, 22L))
                        .build());

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(2, postRequestedFor(urlMatching("/bot[^/]+/sendMessage"))));
    }

    @Test
    void validProcessingFailureReportMessageIsDeliveredToTelegram() {
        stubTelegramSuccess();

        kafkaTemplate.send(
                kafkaProperties.getTopics().getProcessingFailureReports(),
                "report-11",
                ProcessingFailureReportEvent.newBuilder()
                        .setDescription("Failed links report")
                        .setTgChatIds(List.of(11L, 22L))
                        .build());

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(2, postRequestedFor(urlMatching("/bot[^/]+/sendMessage"))));
    }

    @Test
    void malformedPayloadIsSentToDlqWithoutTelegramDelivery() {
        createByteArrayKafkaTemplate()
                .send(kafkaProperties.getTopics().getLinkUpdates(), "bad-bytes", new byte[] {0x01, 0x02, 0x03});

        var dlqRecord = awaitDlqRecord(kafkaProperties.getTopics().getLinkUpdatesDlq(), "bad-bytes");

        org.junit.jupiter.api.Assertions.assertArrayEquals(new byte[] {0x01, 0x02, 0x03}, dlqRecord.value());
        verify(0, postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
    }

    @Test
    void invalidPayloadIsSentToDlqWithoutTelegramDelivery() {
        kafkaTemplate.send(
                kafkaProperties.getTopics().getLinkUpdates(),
                "invalid-payload",
                LinkUpdateEvent.newBuilder()
                        .setId(42L)
                        .setUrl("not-url")
                        .setDescription("")
                        .setTgChatIds(List.of())
                        .build());

        var dlqRecord = awaitDlqRecord(kafkaProperties.getTopics().getLinkUpdatesDlq(), "invalid-payload");
        var payload = (LinkUpdateEvent) deserialize(dlqRecord);

        org.junit.jupiter.api.Assertions.assertEquals("not-url", payload.getUrl());
        verify(0, postRequestedFor(urlMatching("/bot[^/]+/sendMessage")));
    }

    @Test
    void processingFailureIsRetriedAndThenSentToDlq() {
        stubTelegramFailure();

        kafkaTemplate.send(
                kafkaProperties.getTopics().getLinkUpdates(),
                "delivery-failure",
                LinkUpdateEvent.newBuilder()
                        .setId(42L)
                        .setUrl("https://github.com/octocat/hello-world")
                        .setDescription("Updated")
                        .setTgChatIds(List.of(11L))
                        .build());

        var dlqRecord = awaitDlqRecord(kafkaProperties.getTopics().getLinkUpdatesDlq(), "delivery-failure");
        var payload = (LinkUpdateEvent) deserialize(dlqRecord);

        org.junit.jupiter.api.Assertions.assertEquals(42L, payload.getId());
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(3, postRequestedFor(urlMatching("/bot[^/]+/sendMessage"))));
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

    private ConsumerRecord<String, byte[]> awaitDlqRecord(String topic, String key) {
        try (var consumer = createConsumer()) {
            consumer.subscribe(List.of(topic));
            return Awaitility.await()
                    .atMost(Duration.ofSeconds(10))
                    .until(() -> pollMatchingRecord(consumer, key), java.util.Objects::nonNull);
        }
    }

    private KafkaConsumer<String, byte[]> createConsumer() {
        var properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_CONTAINER.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "bot-kafka-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        return new KafkaConsumer<>(properties);
    }

    private ConsumerRecord<String, byte[]> pollMatchingRecord(KafkaConsumer<String, byte[]> consumer, String key) {
        var records = consumer.poll(Duration.ofMillis(250));
        for (var record : records) {
            if (key.equals(record.key())) {
                return record;
            }
        }
        return null;
    }

    private KafkaTemplate<String, byte[]> createByteArrayKafkaTemplate() {
        var producerFactory =
                new org.springframework.kafka.core.DefaultKafkaProducerFactory<String, byte[]>(java.util.Map.of(
                        org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                        KAFKA_CONTAINER.getBootstrapServers(),
                        org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                        org.apache.kafka.common.serialization.StringSerializer.class,
                        org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                        org.apache.kafka.common.serialization.ByteArraySerializer.class));
        return new KafkaTemplate<>(producerFactory);
    }

    private Object deserialize(ConsumerRecord<String, byte[]> record) {
        try (var deserializer = new AvroKafkaDeserializer<>()) {
            deserializer.configure(
                    java.util.Map.of(
                            "apicurio.registry.url",
                            getRegistryApiUrl(),
                            "apicurio.registry.use-specific-avro-reader",
                            true),
                    false);
            return deserializer.deserialize(record.topic(), record.headers(), record.value());
        }
    }

    private static String getRegistryApiUrl() {
        return "http://" + SCHEMA_REGISTRY_CONTAINER.getHost() + ":" + SCHEMA_REGISTRY_CONTAINER.getMappedPort(8080);
    }
}
