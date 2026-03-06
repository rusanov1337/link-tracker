package backend.academy.linktracker.bot.client.scrapper.dto;

import java.util.List;

public record LinkResponse(long id, String url, List<String> tags, List<String> filters) {}
