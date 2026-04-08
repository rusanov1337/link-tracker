package backend.academy.linktracker.bot.service;

public record PendingFailureReport(long chatId, String description) {}
