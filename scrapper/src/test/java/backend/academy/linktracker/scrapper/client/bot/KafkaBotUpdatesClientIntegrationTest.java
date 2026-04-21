package backend.academy.linktracker.scrapper.client.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import backend.academy.linktracker.scrapper.properties.NotificationKafkaProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class KafkaBotUpdatesClientIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Container
    static final KafkaContainer KAFKA_CONTAINER =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Test
    void sendLinkUpdatePublishesJsonMessageToLinkUpdatesTopic() throws Exception {
        var kafkaProperties = new NotificationKafkaProperties();
        var client = new KafkaBotUpdatesClient(createKafkaTemplate(), kafkaProperties);

        try (var consumer = createConsumer()) {
            consumer.subscribe(List.of(kafkaProperties.getTopics().getLinkUpdates()));

            client.sendLinkUpdate(
                    42L, URI.create("https://github.com/octocat/hello-world"), "Updated", List.of(1L, 2L));

            var record = pollSingleRecord(consumer);
            assertEquals(kafkaProperties.getTopics().getLinkUpdates(), record.topic());
            assertEquals("42", record.key());

            var payload = OBJECT_MAPPER.readValue(record.value(), new TypeReference<Map<String, Object>>() {});
            assertEquals(42, ((Number) payload.get("id")).intValue());
            assertEquals("https://github.com/octocat/hello-world", payload.get("url"));
            assertEquals("Updated", payload.get("description"));
            assertEquals(List.of(1, 2), payload.get("tgChatIds"));
        }
    }

    @Test
    void sendProcessingFailureReportPublishesJsonMessageToReportsTopic() throws Exception {
        var kafkaProperties = new NotificationKafkaProperties();
        var client = new KafkaBotUpdatesClient(createKafkaTemplate(), kafkaProperties);

        try (var consumer = createConsumer()) {
            consumer.subscribe(List.of(kafkaProperties.getTopics().getProcessingFailureReports()));

            client.sendProcessingFailureReport("Failed links", List.of(11L, 22L));

            var record = pollSingleRecord(consumer);
            assertEquals(kafkaProperties.getTopics().getProcessingFailureReports(), record.topic());
            assertEquals("processing-failure-report:11", record.key());

            var payload = OBJECT_MAPPER.readValue(record.value(), new TypeReference<Map<String, Object>>() {});
            assertEquals("Failed links", payload.get("description"));
            assertEquals(List.of(11, 22), payload.get("tgChatIds"));
        }
    }

    private KafkaTemplate<String, String> createKafkaTemplate() {
        var producerFactory = new DefaultKafkaProducerFactory<String, String>(Map.of(
                org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA_CONTAINER.getBootstrapServers(),
                org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class,
                org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class));
        return new KafkaTemplate<>(producerFactory);
    }

    private KafkaConsumer<String, String> createConsumer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_CONTAINER.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "scrapper-kafka-client-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(properties);
    }

    private ConsumerRecord<String, String> pollSingleRecord(KafkaConsumer<String, String> consumer) {
        ConsumerRecord<String, String> record = null;
        var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (record == null && System.nanoTime() < deadline) {
            var records = consumer.poll(Duration.ofMillis(250));
            if (!records.isEmpty()) {
                record = records.iterator().next();
            }
        }

        assertNotNull(record, "Expected Kafka message to be published");
        return record;
    }
}
