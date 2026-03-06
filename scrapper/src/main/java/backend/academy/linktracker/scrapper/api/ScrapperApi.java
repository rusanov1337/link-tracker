package backend.academy.linktracker.scrapper.api;

import backend.academy.linktracker.scrapper.api.dto.AddLinkRequest;
import backend.academy.linktracker.scrapper.api.dto.LinkResponse;
import backend.academy.linktracker.scrapper.api.dto.ListLinksResponse;
import backend.academy.linktracker.scrapper.api.dto.RemoveLinkRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

@Validated
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public interface ScrapperApi {

    @PostMapping(path = "/tg-chat/{id}")
    ResponseEntity<Void> registerChat(@PathVariable("id") @NotNull Long chatId);

    @DeleteMapping(path = "/tg-chat/{id}")
    ResponseEntity<Void> deleteChat(@PathVariable("id") @NotNull Long chatId);

    @GetMapping(path = "/links")
    ResponseEntity<ListLinksResponse> getLinks(@RequestHeader("Tg-Chat-Id") @NotNull Long chatId);

    @PostMapping(path = "/links", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<LinkResponse> addLink(
            @RequestHeader("Tg-Chat-Id") @NotNull Long chatId, @Valid @RequestBody AddLinkRequest request);

    @DeleteMapping(path = "/links", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<LinkResponse> removeLink(
            @RequestHeader("Tg-Chat-Id") @NotNull Long chatId, @Valid @RequestBody RemoveLinkRequest request);
}
