package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.api.dto.AddLinkRequest;
import backend.academy.linktracker.scrapper.api.dto.LinkResponse;
import backend.academy.linktracker.scrapper.api.dto.ListLinksResponse;
import backend.academy.linktracker.scrapper.api.dto.RemoveLinkRequest;
import backend.academy.linktracker.scrapper.domain.LinkSubscription;
import backend.academy.linktracker.scrapper.exception.ChatAlreadyExistsException;
import backend.academy.linktracker.scrapper.exception.ChatNotFoundException;
import backend.academy.linktracker.scrapper.exception.LinkAlreadyTrackedException;
import backend.academy.linktracker.scrapper.exception.LinkNotFoundException;
import backend.academy.linktracker.scrapper.repository.ChatRepository;
import backend.academy.linktracker.scrapper.repository.LinkSubscriptionRepository;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ScrapperLinkService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScrapperLinkService.class);

    private final ChatRepository chatRepository;
    private final TrackedLinkRepository trackedLinkRepository;
    private final LinkSubscriptionRepository linkSubscriptionRepository;

    public ScrapperLinkService(
            ChatRepository chatRepository,
            TrackedLinkRepository trackedLinkRepository,
            LinkSubscriptionRepository linkSubscriptionRepository) {
        this.chatRepository = chatRepository;
        this.trackedLinkRepository = trackedLinkRepository;
        this.linkSubscriptionRepository = linkSubscriptionRepository;
    }

    public void registerChat(long chatId) {
        if (!chatRepository.add(chatId)) {
            throw new ChatAlreadyExistsException(chatId);
        }

        LOGGER.atInfo()
                .addKeyValue("operation", "registerChat")
                .addKeyValue("chatId", chatId)
                .addKeyValue("success", true)
                .log("Chat registered");
    }

    public void deleteChat(long chatId) {
        if (!chatRepository.remove(chatId)) {
            throw new ChatNotFoundException(chatId);
        }

        var subscriptions = linkSubscriptionRepository.findByChatId(chatId);
        for (var subscription : subscriptions) {
            linkSubscriptionRepository.remove(chatId, subscription.linkId());
            removeLinkIfOrphan(subscription.linkId());
        }

        LOGGER.atInfo()
                .addKeyValue("operation", "deleteChat")
                .addKeyValue("chatId", chatId)
                .addKeyValue("removedSubscriptions", subscriptions.size())
                .addKeyValue("success", true)
                .log("Chat deleted");
    }

    public ListLinksResponse getLinks(long chatId) {
        ensureChatExists(chatId);

        var subscriptions = linkSubscriptionRepository.findByChatId(chatId);
        var links = new ArrayList<LinkResponse>(subscriptions.size());
        for (var subscription : subscriptions) {
            trackedLinkRepository
                    .findById(subscription.linkId())
                    .ifPresent(trackedLink -> links.add(new LinkResponse(
                            trackedLink.id(),
                            trackedLink.url().toString(),
                            subscription.tags(),
                            subscription.filters())));
        }

        LOGGER.atInfo()
                .addKeyValue("operation", "getLinks")
                .addKeyValue("chatId", chatId)
                .addKeyValue("linksCount", links.size())
                .log("Tracked links listed");
        return new ListLinksResponse(List.copyOf(links), links.size());
    }

    public LinkResponse addLink(long chatId, AddLinkRequest request) {
        ensureChatExists(chatId);

        var requestedUrl = URI.create(request.link()).normalize();
        var trackedLink = trackedLinkRepository
                .findByUrl(requestedUrl)
                .orElseGet(() -> trackedLinkRepository.create(requestedUrl, Instant.now()));

        if (linkSubscriptionRepository.exists(chatId, trackedLink.id())) {
            throw new LinkAlreadyTrackedException(chatId, requestedUrl.toString());
        }

        var subscription = new LinkSubscription(chatId, trackedLink.id(), request.tags(), request.filters());
        linkSubscriptionRepository.add(subscription);

        LOGGER.atInfo()
                .addKeyValue("operation", "addLink")
                .addKeyValue("chatId", chatId)
                .addKeyValue("linkId", trackedLink.id())
                .addKeyValue("url", trackedLink.url())
                .addKeyValue("tagsCount", subscription.tags().size())
                .addKeyValue("filtersCount", subscription.filters().size())
                .addKeyValue("success", true)
                .log("Link tracked");
        return new LinkResponse(
                trackedLink.id(), trackedLink.url().toString(), subscription.tags(), subscription.filters());
    }

    public LinkResponse removeLink(long chatId, RemoveLinkRequest request) {
        ensureChatExists(chatId);

        var requestedUrl = URI.create(request.link()).normalize();
        var trackedLink = trackedLinkRepository
                .findByUrl(requestedUrl)
                .orElseThrow(() -> new LinkNotFoundException(chatId, requestedUrl.toString()));
        var subscription = linkSubscriptionRepository
                .find(chatId, trackedLink.id())
                .orElseThrow(() -> new LinkNotFoundException(chatId, requestedUrl.toString()));

        linkSubscriptionRepository.remove(chatId, trackedLink.id());
        removeLinkIfOrphan(trackedLink.id());

        LOGGER.atInfo()
                .addKeyValue("operation", "removeLink")
                .addKeyValue("chatId", chatId)
                .addKeyValue("linkId", trackedLink.id())
                .addKeyValue("url", trackedLink.url())
                .addKeyValue("success", true)
                .log("Link untracked");
        return new LinkResponse(
                trackedLink.id(), trackedLink.url().toString(), subscription.tags(), subscription.filters());
    }

    private void ensureChatExists(long chatId) {
        if (!chatRepository.exists(chatId)) {
            throw new ChatNotFoundException(chatId);
        }
    }

    private void removeLinkIfOrphan(long linkId) {
        if (linkSubscriptionRepository.findByLinkId(linkId).isEmpty()) {
            trackedLinkRepository.delete(linkId);
        }
    }
}
