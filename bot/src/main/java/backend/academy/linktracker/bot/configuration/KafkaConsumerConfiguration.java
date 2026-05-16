package backend.academy.linktracker.bot.configuration;

import backend.academy.linktracker.bot.kafka.ProcessedLinkUpdateEvent;
import backend.academy.linktracker.bot.properties.NotificationKafkaProperties;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfiguration {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(KafkaConsumerConfiguration.class);

    private final NotificationKafkaProperties kafkaProperties;

    @Bean
    KafkaTemplate<String, byte[]> dlqBytesKafkaTemplate(KafkaProperties kafkaConfigurationProperties) {
        return new KafkaTemplate<>(dlqBytesProducerFactory(kafkaConfigurationProperties));
    }

    @Bean
    ProducerFactory<Object, Object> avroProducerFactory(KafkaProperties kafkaConfigurationProperties) {
        var producerProperties = new LinkedHashMap<>(kafkaConfigurationProperties.buildProducerProperties());
        producerProperties.put(
                org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(
                org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                AvroKafkaSerializer.class);
        return new DefaultKafkaProducerFactory<>(producerProperties);
    }

    @Bean("avroKafkaTemplate")
    KafkaTemplate<Object, Object> avroKafkaTemplate(ProducerFactory<Object, Object> avroProducerFactory) {
        return new KafkaTemplate<>(avroProducerFactory);
    }

    @Bean
    ProducerFactory<String, byte[]> dlqBytesProducerFactory(KafkaProperties kafkaConfigurationProperties) {
        var producerProperties = new LinkedHashMap<>(kafkaConfigurationProperties.buildProducerProperties());
        producerProperties.put(
                org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(
                org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                ByteArraySerializer.class);
        return new DefaultKafkaProducerFactory<>(producerProperties);
    }

    @Bean
    CommonErrorHandler kafkaErrorHandler(
            @Qualifier("avroKafkaTemplate") KafkaOperations<Object, Object> kafkaTemplate,
            KafkaTemplate<String, byte[]> dlqBytesKafkaTemplate) {
        var templates = new LinkedHashMap<Class<?>, KafkaOperations<?, ?>>();
        templates.put(byte[].class, dlqBytesKafkaTemplate);
        templates.put(Object.class, kafkaTemplate);
        var recoverer = new DeadLetterPublishingRecoverer(
                templates,
                (record, exception) -> new TopicPartition(resolveDlqTopic(record.topic()), record.partition()));
        var retryAttempts = Math.max(0, kafkaProperties.getConsumer().getMaxAttempts() - 1L);
        var errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(kafkaProperties.getConsumer().getRetryBackoff().toMillis(), retryAttempts));
        errorHandler.addNotRetryableExceptions(
                IllegalArgumentException.class, ConstraintViolationException.class, DeserializationException.class);
        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory, CommonErrorHandler kafkaErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, ProcessedLinkUpdateEvent>
            processedUpdateKafkaListenerContainerFactory(KafkaProperties kafkaConfigurationProperties) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, ProcessedLinkUpdateEvent>();
        factory.setConsumerFactory(processedUpdateConsumerFactory(kafkaConfigurationProperties));
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                (record, exception) -> LOGGER.warn(
                        "Skipping processed update Kafka record topic={} partition={} offset={}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        exception),
                new FixedBackOff(0L, 0L)));
        return factory;
    }

    private ConsumerFactory<String, ProcessedLinkUpdateEvent> processedUpdateConsumerFactory(
            KafkaProperties kafkaConfigurationProperties) {
        var consumerProperties = new LinkedHashMap<>(kafkaConfigurationProperties.buildConsumerProperties());
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        consumerProperties.put(
                ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JacksonJsonDeserializer.class.getName());
        consumerProperties.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, ProcessedLinkUpdateEvent.class.getName());
        consumerProperties.put(
                JacksonJsonDeserializer.TRUSTED_PACKAGES, ProcessedLinkUpdateEvent.class.getPackageName());
        return new DefaultKafkaConsumerFactory<>(consumerProperties);
    }

    private String resolveDlqTopic(String topic) {
        if (kafkaProperties.getTopics().getLinkUpdates().equals(topic)) {
            return kafkaProperties.getTopics().getLinkUpdatesDlq();
        }
        if (kafkaProperties.getTopics().getProcessingFailureReports().equals(topic)) {
            return kafkaProperties.getTopics().getProcessingFailureReportsDlq();
        }

        throw new IllegalArgumentException("No DLQ is configured for topic " + topic);
    }
}
