package backend.academy.linktracker.bot.configuration;

import backend.academy.linktracker.bot.properties.NotificationKafkaProperties;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfiguration {

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
