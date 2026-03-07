package backend.academy.linktracker.bot.service;

public record PendingLinkUpdate(long updateId, long chatId, String url, String description) {}
