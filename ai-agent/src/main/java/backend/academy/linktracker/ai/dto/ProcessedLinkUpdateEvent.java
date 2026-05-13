package backend.academy.linktracker.ai.dto;

import java.util.List;

public record ProcessedLinkUpdateEvent(
        long id, String url, String description, List<Long> tgChatIds, UpdatePriority priority) {}
