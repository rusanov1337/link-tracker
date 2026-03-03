package backend.academy.linktracker.bot.service.command;

public interface CommandHandler {
    String command();

    String description();

    String handle(CommandRequest request, CommandContext context);

    default boolean isFallback() {
        return false;
    }

    default int order() {
        return 0;
    }
}
