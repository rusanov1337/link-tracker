package backend.academy.linktracker.bot.api;

import backend.academy.linktracker.bot.api.dto.LinkUpdate;
import backend.academy.linktracker.bot.service.LinkUpdateNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BotController implements BotApi {

    private final LinkUpdateNotificationService linkUpdateNotificationService;

    @Override
    public ResponseEntity<Void> processUpdate(LinkUpdate linkUpdate) {
        linkUpdateNotificationService.process(linkUpdate);
        return ResponseEntity.ok().build();
    }
}
