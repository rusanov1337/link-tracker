package backend.academy.linktracker.bot.service.command;

public record CommandRequest(String command, String arguments, String rawText, long chatId, Long userId) {}
