package backend.academy.linktracker.ai.configuration;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConfiguration.class);

    @Bean
    CommonErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(KafkaConfiguration::logSkippedRecord, new FixedBackOff(0L, 0L));
    }

    private static void logSkippedRecord(ConsumerRecord<?, ?> record, Exception exception) {
        LOGGER.warn(
                "Skipping malformed Kafka record topic={} partition={} offset={}",
                record.topic(),
                record.partition(),
                record.offset(),
                exception);
    }
}
