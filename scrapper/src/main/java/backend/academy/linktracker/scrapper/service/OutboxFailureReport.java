package backend.academy.linktracker.scrapper.service;

import java.util.List;

public record OutboxFailureReport(long id, String description, List<Long> chatIds) {

    public OutboxFailureReport {
        chatIds = List.copyOf(chatIds);
    }
}
