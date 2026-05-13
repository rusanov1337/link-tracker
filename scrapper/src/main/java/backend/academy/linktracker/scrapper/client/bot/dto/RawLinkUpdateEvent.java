package backend.academy.linktracker.scrapper.client.bot.dto;

import java.util.List;

public record RawLinkUpdateEvent(long id, String url, String description, String author, List<Long> tgChatIds) {}
