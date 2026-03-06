package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.api.dto.LinkUpdate;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LinkUpdateNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkUpdateNotificationService.class);

    private final TelegramBot telegramBot;

    public LinkUpdateNotificationService(TelegramBot telegramBot) {
        this.telegramBot = telegramBot;
    }

    public void process(LinkUpdate linkUpdate) {
        var text = buildMessage(linkUpdate);
        for (var chatId : linkUpdate.tgChatIds()) {
            sendUpdate(chatId, text, linkUpdate.id(), linkUpdate.url());
        }
    }

    private String buildMessage(LinkUpdate linkUpdate) {
        return "Обновление по ссылке: " + linkUpdate.url() + System.lineSeparator() + linkUpdate.description();
    }

    private void sendUpdate(long chatId, String text, long updateId, String url) {
        try {
            var response = telegramBot.execute(new SendMessage(chatId, text));
            if (response.isOk()) {
                LOGGER.atInfo()
                        .addKeyValue("operation", "sendUpdateNotification")
                        .addKeyValue("chatId", chatId)
                        .addKeyValue("updateId", updateId)
                        .addKeyValue("url", url)
                        .addKeyValue("success", true)
                        .log("Update notification sent");
                return;
            }

            LOGGER.atWarn()
                    .addKeyValue("operation", "sendUpdateNotification")
                    .addKeyValue("chatId", chatId)
                    .addKeyValue("updateId", updateId)
                    .addKeyValue("url", url)
                    .addKeyValue("errorCode", response.errorCode())
                    .addKeyValue("errorDescription", response.description())
                    .addKeyValue("success", false)
                    .log("Update notification failed");
        } catch (RuntimeException exception) {
            LOGGER.atWarn()
                    .addKeyValue("operation", "sendUpdateNotification")
                    .addKeyValue("chatId", chatId)
                    .addKeyValue("updateId", updateId)
                    .addKeyValue("url", url)
                    .addKeyValue("success", false)
                    .setCause(exception)
                    .log("Update notification failed with exception");
        }
    }
}
