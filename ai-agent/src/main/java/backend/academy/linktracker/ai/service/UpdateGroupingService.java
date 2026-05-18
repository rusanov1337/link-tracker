package backend.academy.linktracker.ai.service;

import backend.academy.linktracker.ai.dto.ProcessedLinkUpdateEvent;
import backend.academy.linktracker.ai.dto.UpdatePriority;
import backend.academy.linktracker.ai.kafka.ProcessedUpdateProducer;
import backend.academy.linktracker.ai.properties.AiAgentProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class UpdateGroupingService {

    private final AiAgentProperties properties;
    private final ProcessedUpdateProducer producer;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<Long, PendingGroup> groups = new ConcurrentHashMap<>();

    public UpdateGroupingService(
            AiAgentProperties properties, ProcessedUpdateProducer producer, ScheduledExecutorService scheduler) {
        this.properties = properties;
        this.producer = producer;
        this.scheduler = scheduler;
    }

    public void submit(ProcessedLinkUpdateEvent update) {
        update.tgChatIds().forEach(chatId -> submitForChat(update, chatId));
    }

    private void submitForChat(ProcessedLinkUpdateEvent update, Long chatId) {
        groups.compute(chatId, (ignored, group) -> {
            var existingGroup = group;
            if (existingGroup == null) {
                existingGroup = new PendingGroup(chatId);
                scheduleFlush(chatId);
            }
            existingGroup.add(singleChatUpdate(update, chatId));
            return existingGroup;
        });
    }

    private void scheduleFlush(Long chatId) {
        scheduler.schedule(() -> flush(chatId), properties.getGrouping().getWindowMs(), TimeUnit.MILLISECONDS);
    }

    private void flush(Long chatId) {
        var group = groups.remove(chatId);
        if (group != null) {
            producer.send(group.toUpdate());
        }
    }

    private ProcessedLinkUpdateEvent singleChatUpdate(ProcessedLinkUpdateEvent update, Long chatId) {
        return new ProcessedLinkUpdateEvent(
                update.id(), update.url(), update.description(), List.of(chatId), update.priority());
    }

    private static final class PendingGroup {

        private final Long chatId;
        private final List<ProcessedLinkUpdateEvent> updates = new ArrayList<>();

        private PendingGroup(Long chatId) {
            this.chatId = chatId;
        }

        private void add(ProcessedLinkUpdateEvent update) {
            updates.add(update);
        }

        private ProcessedLinkUpdateEvent toUpdate() {
            if (updates.size() == 1) {
                return updates.getFirst();
            }

            var firstUpdate = updates.getFirst();
            return new ProcessedLinkUpdateEvent(
                    firstUpdate.id(), firstUpdate.url(), groupedDescription(), List.of(chatId), maxPriority());
        }

        private String groupedDescription() {
            var description = new StringBuilder();
            for (var index = 0; index < updates.size(); index++) {
                if (!description.isEmpty()) {
                    description.append(System.lineSeparator());
                }
                description
                        .append(index + 1)
                        .append(". ")
                        .append(updates.get(index).description());
            }
            return description.toString();
        }

        private UpdatePriority maxPriority() {
            if (updates.stream().anyMatch(update -> update.priority() == UpdatePriority.HIGH)) {
                return UpdatePriority.HIGH;
            }
            if (updates.stream().anyMatch(update -> update.priority() == UpdatePriority.MEDIUM)) {
                return UpdatePriority.MEDIUM;
            }
            return UpdatePriority.LOW;
        }
    }
}
