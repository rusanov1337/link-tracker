package backend.academy.linktracker.scrapper.client.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import backend.academy.linktracker.kafka.avro.LinkUpdateEvent;
import backend.academy.linktracker.kafka.avro.ProcessingFailureReportEvent;
import backend.academy.linktracker.scrapper.properties.NotificationKafkaProperties;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class KafkaBotUpdatesClientIntegrationTest {

    @Container
    static final KafkaContainer KAFKA_CONTAINER =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Container
    static final GenericContainer<?> SCHEMA_REGISTRY_CONTAINER = new GenericContainer<>(
                    DockerImageName.parse("apicurio/apicurio-registry:3.2.1"))
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/apis").forPort(8080).forStatusCode(200));

    @Test
    void sendLinkUpdatePublishesJsonMessageToLinkUpdatesTopic() {
        var kafkaProperties = new NotificationKafkaProperties();
        var client = new KafkaBotUpdatesClient(createKafkaTemplate(), kafkaProperties);

        try (var consumer = createConsumer()) {
            consumer.subscribe(List.of(kafkaProperties.getTopics().getLinkUpdates()));

            client.sendLinkUpdate(
                    42L, URI.create("https://github.com/octocat/hello-world"), "Updated", List.of(1L, 2L));

            var record = pollSingleRecord(consumer);
            assertEquals(kafkaProperties.getTopics().getLinkUpdates(), record.topic());
            assertEquals("42", record.key());

            var payload = (LinkUpdateEvent) deserialize(record);
            assertEquals(42L, payload.getId());
            assertEquals("https://github.com/octocat/hello-world", payload.getUrl());
            assertEquals("Updated", payload.getDescription());
            assertEquals(List.of(1L, 2L), payload.getTgChatIds());
        }
    }

    @Test
    void sendProcessingFailureReportPublishesJsonMessageToReportsTopic() {
        var kafkaProperties = new NotificationKafkaProperties();
        var client = new KafkaBotUpdatesClient(createKafkaTemplate(), kafkaProperties);

        try (var consumer = createConsumer()) {
            consumer.subscribe(List.of(kafkaProperties.getTopics().getProcessingFailureReports()));

            client.sendProcessingFailureReport("Failed links", List.of(11L, 22L));

            var record = pollSingleRecord(consumer);
            assertEquals(kafkaProperties.getTopics().getProcessingFailureReports(), record.topic());
            assertEquals("processing-failure-report:11", record.key());

            var payload = (ProcessingFailureReportEvent) deserialize(record);
            assertEquals("Failed links", payload.getDescription());
            assertEquals(List.of(11L, 22L), payload.getTgChatIds());
        }
    }

    private KafkaTemplate<String, Object> createKafkaTemplate() {
        var producerFactory = new DefaultKafkaProducerFactory<String, Object>(Map.of(
                org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA_CONTAINER.getBootstrapServers(),
                org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class,
                org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                AvroKafkaSerializer.class,
                "apicurio.registry.url",
                getRegistryApiUrl(),
                "apicurio.registry.auto-register",
                true));
        return new KafkaTemplate<>(producerFactory);
    }

    private KafkaConsumer<String, byte[]> createConsumer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_CONTAINER.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "scrapper-kafka-client-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        return new KafkaConsumer<>(properties);
    }

    private ConsumerRecord<String, byte[]> pollSingleRecord(KafkaConsumer<String, byte[]> consumer) {
        ConsumerRecord<String, byte[]> record = null;
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

    private Object deserialize(ConsumerRecord<String, byte[]> record) {
        try (var deserializer = new AvroKafkaDeserializer<>()) {
            deserializer.configure(
                    Map.of(
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
