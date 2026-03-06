package backend.academy.linktracker.bot.client.scrapper.dto;

import java.util.List;

public record ListLinksResponse(List<LinkResponse> links, int size) {}
