package backend.academy.linktracker.scrapper.client.bot.dto;

import java.util.List;

public record ProcessingFailureReportRequest(String description, List<Long> tgChatIds) {}
