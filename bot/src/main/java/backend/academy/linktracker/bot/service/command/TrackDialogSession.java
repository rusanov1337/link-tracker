package backend.academy.linktracker.bot.service.command;

import java.util.List;

public record TrackDialogSession(TrackDialogState state, String link, List<String> tags) {

    public static TrackDialogSession start() {
        return new TrackDialogSession(TrackDialogState.AWAIT_LINK, null, List.of());
    }

    public TrackDialogSession withLink(String nextLink) {
        return new TrackDialogSession(TrackDialogState.AWAIT_TAGS, nextLink, List.of());
    }

    public TrackDialogSession withTags(List<String> nextTags) {
        return new TrackDialogSession(TrackDialogState.AWAIT_FILTERS, link, List.copyOf(nextTags));
    }
}
