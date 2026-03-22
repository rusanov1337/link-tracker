package backend.academy.linktracker.scrapper.repository.orm;

import backend.academy.linktracker.scrapper.TestcontainersConfiguration;
import backend.academy.linktracker.scrapper.repository.ChatRepository;
import backend.academy.linktracker.scrapper.repository.LinkSubscriptionRepository;
import backend.academy.linktracker.scrapper.repository.RepositoryIntegrationTestSupport;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"app.scheduler.enabled=false", "app.database.access-type=ORM"})
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrmRepositoryIntegrationTest extends RepositoryIntegrationTestSupport {

    @Override
    protected Class<? extends ChatRepository> expectedChatRepositoryType() {
        return ormChatRepository();
    }

    @Override
    protected Class<? extends TrackedLinkRepository> expectedTrackedLinkRepositoryType() {
        return ormTrackedLinkRepository();
    }

    @Override
    protected Class<? extends LinkSubscriptionRepository> expectedLinkSubscriptionRepositoryType() {
        return ormLinkSubscriptionRepository();
    }
}
