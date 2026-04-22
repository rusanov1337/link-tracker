package backend.academy.linktracker.bot.configuration;

import backend.academy.linktracker.bot.properties.NotificationKafkaProperties;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfiguration {

    private final NotificationKafkaProperties kafkaProperties;

    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(resolveDlqTopic(record.topic()), record.partition()));
        var retryAttempts = Math.max(0, kafkaProperties.getConsumer().getMaxAttempts() - 1L);
        var errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(kafkaProperties.getConsumer().getRetryBackoff().toMillis(), retryAttempts));
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class, ConstraintViolationException.class);
        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory, CommonErrorHandler kafkaErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
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
