package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.api.dto.LinkUpdate;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class LinkUpdateNotificationService {

    private final TelegramBot telegramBot;
    private final PendingLinkUpdateStore pendingLinkUpdateStore;

    public void process(LinkUpdate linkUpdate) {
        var pendingUpdates = new ArrayList<PendingLinkUpdate>();
        for (var chatId : linkUpdate.tgChatIds()) {
            var pendingUpdate =
                    new PendingLinkUpdate(linkUpdate.id(), chatId, linkUpdate.url(), linkUpdate.description());
            if (!sendUpdate(pendingUpdate)) {
                pendingUpdates.add(pendingUpdate);
            }
        }

        if (!pendingUpdates.isEmpty()) {
            pendingLinkUpdateStore.saveAll(pendingUpdates);
            log.atWarn()
                    .addKeyValue("operation", "queuePendingLinkUpdates")
                    .addKeyValue("pendingUpdatesCount", pendingUpdates.size())
                    .log("Link updates queued for retry");
        }
    }

    public void retryPendingUpdates() {
        var pendingUpdates = pendingLinkUpdateStore.findAll();
        for (var pendingUpdate : pendingUpdates) {
            if (sendUpdate(pendingUpdate)) {
                pendingLinkUpdateStore.remove(pendingUpdate);
            }
        }
    }

    private String buildMessage(PendingLinkUpdate pendingUpdate) {
        return "Обновление по ссылке: " + pendingUpdate.url() + System.lineSeparator() + pendingUpdate.description();
    }

    @SuppressFBWarnings(
            value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
            justification =
                    "TelegramBot.execute may return null on invalid HTTP responses despite the static signature.")
    private boolean sendUpdate(PendingLinkUpdate pendingUpdate) {
        var text = buildMessage(pendingUpdate);
        try {
            var response = telegramBot.execute(new SendMessage(pendingUpdate.chatId(), text));
            if (response == null) {
                logNullResponse(pendingUpdate);
                return false;
            }
            if (response.isOk()) {
                log.atInfo()
                        .addKeyValue("operation", "sendUpdateNotification")
                        .addKeyValue("chatId", pendingUpdate.chatId())
                        .addKeyValue("updateId", pendingUpdate.updateId())
                        .addKeyValue("url", pendingUpdate.url())
                        .addKeyValue("success", true)
                        .log("Update notification sent");
                return true;
            }

            log.atWarn()
                    .addKeyValue("operation", "sendUpdateNotification")
                    .addKeyValue("chatId", pendingUpdate.chatId())
                    .addKeyValue("updateId", pendingUpdate.updateId())
                    .addKeyValue("url", pendingUpdate.url())
                    .addKeyValue("errorCode", response.errorCode())
                    .addKeyValue("errorDescription", response.description())
                    .addKeyValue("success", false)
                    .log("Update notification failed");
            return false;
        } catch (RuntimeException exception) {
            log.atWarn()
                    .addKeyValue("operation", "sendUpdateNotification")
                    .addKeyValue("chatId", pendingUpdate.chatId())
                    .addKeyValue("updateId", pendingUpdate.updateId())
                    .addKeyValue("url", pendingUpdate.url())
                    .addKeyValue("success", false)
                    .setCause(exception)
                    .log("Update notification failed with exception");
            return false;
        }
    }

    private void logNullResponse(PendingLinkUpdate pendingUpdate) {
        log.atWarn()
                .addKeyValue("operation", "sendUpdateNotification")
                .addKeyValue("chatId", pendingUpdate.chatId())
                .addKeyValue("updateId", pendingUpdate.updateId())
                .addKeyValue("url", pendingUpdate.url())
                .addKeyValue("success", false)
                .log("Update notification failed with null response");
    }
}
