package backend.academy.linktracker.scrapper.scheduler;

import backend.academy.linktracker.scrapper.service.LinkUpdatePollingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LinkUpdateScheduler {

    private final LinkUpdatePollingService linkUpdatePollingService;

    public LinkUpdateScheduler(LinkUpdatePollingService linkUpdatePollingService) {
        this.linkUpdatePollingService = linkUpdatePollingService;
    }

    @Scheduled(fixedDelayString = "${app.scheduler.interval:60000}")
    public void scheduleLinkUpdatesCheck() {
        linkUpdatePollingService.checkUpdates();
    }
}
