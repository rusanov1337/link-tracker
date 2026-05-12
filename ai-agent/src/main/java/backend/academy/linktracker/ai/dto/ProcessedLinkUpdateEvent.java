package backend.academy.linktracker.ai.dto;

import java.util.List;

public record ProcessedLinkUpdateEvent(long id, String description, List<Long> tgChatIds, UpdatePriority priority) {}
