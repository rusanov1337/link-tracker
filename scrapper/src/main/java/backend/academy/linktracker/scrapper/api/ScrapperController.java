package backend.academy.linktracker.scrapper.api;

import backend.academy.linktracker.scrapper.api.dto.AddLinkRequest;
import backend.academy.linktracker.scrapper.api.dto.LinkResponse;
import backend.academy.linktracker.scrapper.api.dto.ListLinksResponse;
import backend.academy.linktracker.scrapper.api.dto.RemoveLinkRequest;
import backend.academy.linktracker.scrapper.service.ScrapperLinkService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScrapperController implements ScrapperApi {

    private final ScrapperLinkService scrapperLinkService;
    private final backend.academy.linktracker.scrapper.service.ScrapperMetricsService scrapperMetricsService;

    public ScrapperController(
            ScrapperLinkService scrapperLinkService,
            backend.academy.linktracker.scrapper.service.ScrapperMetricsService scrapperMetricsService) {
        this.scrapperLinkService = scrapperLinkService;
        this.scrapperMetricsService = scrapperMetricsService;
    }

    @Override
    public ResponseEntity<Void> registerChat(Long chatId) {
        scrapperMetricsService.incrementApiRequests("http");
        scrapperLinkService.registerChat(chatId);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> deleteChat(Long chatId) {
        scrapperMetricsService.incrementApiRequests("http");
        scrapperLinkService.deleteChat(chatId);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<ListLinksResponse> getLinks(Long chatId) {
        scrapperMetricsService.incrementApiRequests("http");
        return ResponseEntity.ok(scrapperLinkService.getLinks(chatId));
    }

    @Override
    public ResponseEntity<LinkResponse> addLink(Long chatId, AddLinkRequest request) {
        scrapperMetricsService.incrementApiRequests("http");
        return ResponseEntity.ok(scrapperLinkService.addLink(chatId, request));
    }

    @Override
    public ResponseEntity<LinkResponse> removeLink(Long chatId, RemoveLinkRequest request) {
        scrapperMetricsService.incrementApiRequests("http");
        return ResponseEntity.ok(scrapperLinkService.removeLink(chatId, request));
    }
}
