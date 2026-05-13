package backend.academy.linktracker.bot.kafka;

import java.util.List;

public record ProcessedLinkUpdateEvent(
        long id, String url, String description, List<Long> tgChatIds, String priority) {}
