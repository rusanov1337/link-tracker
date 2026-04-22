package backend.academy.linktracker.scrapper;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

public abstract class DatabaseCleanupSupport {

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("""
                truncate table notification_outbox, subscription_filters, subscription_tags, subscriptions, links, chats
                restart identity cascade
                """).update();
    }
}
