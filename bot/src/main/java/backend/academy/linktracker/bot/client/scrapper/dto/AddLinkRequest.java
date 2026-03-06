package backend.academy.linktracker.bot.client.scrapper.dto;

import java.util.List;

public record AddLinkRequest(String link, List<String> tags, List<String> filters) {}
