package backend.academy.linktracker.ai;

import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.ai.dto.ProcessedLinkUpdateEvent;
import backend.academy.linktracker.ai.dto.RawLinkUpdateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AiAgentKafkaIntegrationTest {

    private static final String RAW_TOPIC = "test.link.raw-updates";
    private static final String PROCESSED_TOPIC = "test.link.processed-updates";

    @Container
    static final KafkaContainer KAFKA_CONTAINER =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA_CONTAINER::getBootstrapServers);
        registry.add("ai-agent.kafka.topics.raw-updates", () -> RAW_TOPIC);
        registry.add("ai-agent.kafka.topics.processed-updates", () -> PROCESSED_TOPIC);
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void consumesRawUpdateAndPublishesProcessedUpdate() throws Exception {
        kafkaTemplate.send(RAW_TOPIC, "12345", validUpdate()).get();

        var processed = awaitProcessedUpdate("12345");

        assertThat(processed.id()).isEqualTo(12345L);
        assertThat(processed.url()).isEqualTo("https://github.com/octocat/hello-world");
        assertThat(processed.description()).isEqualTo("Regular update text with enough length");
        assertThat(processed.tgChatIds()).containsExactly(111L, 222L);
        assertThat(processed.priority().name()).isEqualTo("HIGH");
    }

    @Test
    void malformedMessageIsSkippedWithoutStoppingConsumer() throws Exception {
        kafkaTemplate.send(RAW_TOPIC, "bad", "not-json").get();
        kafkaTemplate.send(RAW_TOPIC, "12345", validUpdate()).get();

        var processed = awaitProcessedUpdate("12345");

        assertThat(processed.id()).isEqualTo(12345L);
    }

    private RawLinkUpdateEvent validUpdate() {
        return new RawLinkUpdateEvent(
                12345L,
                "https://github.com/octocat/hello-world",
                "Regular update text with enough length",
                "alice",
                List.of(111L, 222L));
    }

    private ProcessedLinkUpdateEvent awaitProcessedUpdate(String key) {
        try (var consumer = createProcessedUpdatesConsumer()) {
            consumer.subscribe(List.of(PROCESSED_TOPIC));
            var record = Awaitility.await()
                    .atMost(Duration.ofSeconds(10))
                    .until(() -> pollMatchingRecord(consumer, key), java.util.Objects::nonNull);
            try {
                return objectMapper.readValue(record.value(), ProcessedLinkUpdateEvent.class);
            } catch (java.io.IOException exception) {
                throw new AssertionError("Unable to parse processed update", exception);
            }
        }
    }

    private KafkaConsumer<String, String> createProcessedUpdatesConsumer() {
        var properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_CONTAINER.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "ai-agent-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return new KafkaConsumer<>(properties);
    }

    private ConsumerRecord<String, String> pollMatchingRecord(KafkaConsumer<String, String> consumer, String key) {
        var records = consumer.poll(Duration.ofMillis(200));
        for (var record : records) {
            if (key.equals(record.key())) {
                return record;
            }
        }
        return null;
    }
}
