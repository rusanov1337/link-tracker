package backend.academy.linktracker.bot.kafka;

import java.io.Serial;

public class KafkaNotificationDeliveryException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public KafkaNotificationDeliveryException(String message) {
        super(message);
    }
}
