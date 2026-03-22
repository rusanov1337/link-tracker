package backend.academy.linktracker.scrapper.repository.sql;

import backend.academy.linktracker.scrapper.TestcontainersConfiguration;
import backend.academy.linktracker.scrapper.repository.ChatRepository;
import backend.academy.linktracker.scrapper.repository.LinkSubscriptionRepository;
import backend.academy.linktracker.scrapper.repository.RepositoryIntegrationTestSupport;
import backend.academy.linktracker.scrapper.repository.SubscriptionTagRepository;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        properties = {
            "app.scheduler.enabled=false",
            "app.database.access-type=SQL",
            "springdoc.api-docs.enabled=false",
            "springdoc.swagger-ui.enabled=false"
        })
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SqlRepositoryIntegrationTest extends RepositoryIntegrationTestSupport {

    @Override
    protected Class<? extends ChatRepository> expectedChatRepositoryType() {
        return sqlChatRepository();
    }

    @Override
    protected Class<? extends TrackedLinkRepository> expectedTrackedLinkRepositoryType() {
        return sqlTrackedLinkRepository();
    }

    @Override
    protected Class<? extends LinkSubscriptionRepository> expectedLinkSubscriptionRepositoryType() {
        return sqlLinkSubscriptionRepository();
    }

    @Override
    protected Class<? extends SubscriptionTagRepository> expectedSubscriptionTagRepositoryType() {
        return sqlSubscriptionTagRepository();
    }
}
