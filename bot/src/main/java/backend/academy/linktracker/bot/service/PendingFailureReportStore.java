package backend.academy.linktracker.bot.service;

import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.stereotype.Component;

@Component
public class PendingFailureReportStore {

    private final Queue<PendingFailureReport> pendingReports = new ConcurrentLinkedQueue<>();
    private final Set<PendingFailureReport> pendingReportsIndex = ConcurrentHashMap.newKeySet();

    public void saveAll(List<PendingFailureReport> reports) {
        for (var report : reports) {
            if (pendingReportsIndex.add(report)) {
                pendingReports.add(report);
            }
        }
    }

    public List<PendingFailureReport> findAll() {
        return pendingReports.stream().toList();
    }

    public void remove(PendingFailureReport report) {
        if (pendingReports.remove(report)) {
            pendingReportsIndex.remove(report);
        }
    }

    public int size() {
        return pendingReportsIndex.size();
    }
}
