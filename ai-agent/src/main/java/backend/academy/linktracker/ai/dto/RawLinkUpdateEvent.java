package backend.academy.linktracker.ai.dto;

import java.util.List;

public record RawLinkUpdateEvent(long id, String description, String author, List<Long> tgChatIds) {}
