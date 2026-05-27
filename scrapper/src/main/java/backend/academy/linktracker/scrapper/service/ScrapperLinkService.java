package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.api.dto.AddLinkRequest;
import backend.academy.linktracker.scrapper.api.dto.LinkResponse;
import backend.academy.linktracker.scrapper.api.dto.ListLinksResponse;
import backend.academy.linktracker.scrapper.api.dto.RemoveLinkRequest;
import backend.academy.linktracker.scrapper.client.external.ExternalLinkClient;
import backend.academy.linktracker.scrapper.domain.LinkSubscription;
import backend.academy.linktracker.scrapper.domain.TrackedLink;
import backend.academy.linktracker.scrapper.exception.ChatAlreadyExistsException;
import backend.academy.linktracker.scrapper.exception.ChatNotFoundException;
import backend.academy.linktracker.scrapper.exception.LinkAlreadyTrackedException;
import backend.academy.linktracker.scrapper.exception.LinkNotFoundException;
import backend.academy.linktracker.scrapper.exception.UnsupportedLinkException;
import backend.academy.linktracker.scrapper.properties.DatabaseProperties;
import backend.academy.linktracker.scrapper.repository.ChatRepository;
import backend.academy.linktracker.scrapper.repository.LinkSubscriptionRepository;
import backend.academy.linktracker.scrapper.repository.TrackedLinkRepository;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScrapperLinkService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScrapperLinkService.class);

    private final ChatRepository chatRepository;
    private final TrackedLinkRepository trackedLinkRepository;
    private final LinkSubscriptionRepository linkSubscriptionRepository;
    private final List<ExternalLinkClient> externalLinkClients;
    private final DatabaseProperties databaseProperties;
    private final TrackedLinksCacheService trackedLinksCacheService;

    public ScrapperLinkService(
            ChatRepository chatRepository,
            TrackedLinkRepository trackedLinkRepository,
            LinkSubscriptionRepository linkSubscriptionRepository,
            List<ExternalLinkClient> externalLinkClients,
            DatabaseProperties databaseProperties,
            TrackedLinksCacheService trackedLinksCacheService) {
        this.chatRepository = chatRepository;
        this.trackedLinkRepository = trackedLinkRepository;
        this.linkSubscriptionRepository = linkSubscriptionRepository;
        this.externalLinkClients = externalLinkClients;
        this.databaseProperties = databaseProperties;
        this.trackedLinksCacheService = trackedLinksCacheService;
    }

    @Transactional
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

    @Transactional
    public void deleteChat(long chatId) {
        var trackedLinkIdsToCheck = loadTrackedLinkIdsByChatId(chatId);
        if (!chatRepository.remove(chatId)) {
            throw new ChatNotFoundException(chatId);
        }

        for (var linkId : trackedLinkIdsToCheck) {
            removeLinkIfOrphan(linkId);
        }

        LOGGER.atInfo()
                .addKeyValue("operation", "deleteChat")
                .addKeyValue("chatId", chatId)
                .addKeyValue("orphanLinksToCheck", trackedLinkIdsToCheck.size())
                .addKeyValue("success", true)
                .log("Chat deleted");
        trackedLinksCacheService.evict(chatId);
    }

    @Transactional(readOnly = true)
    public ListLinksResponse getLinks(long chatId) {
        return trackedLinksCacheService
                .get(chatId)
                .map(response -> {
                    LOGGER.atInfo()
                            .addKeyValue("operation", "getLinks")
                            .addKeyValue("chatId", chatId)
                            .addKeyValue("cacheHit", true)
                            .addKeyValue("linksCount", response.size())
                            .log("Tracked links listed from cache");
                    return response;
                })
                .orElseGet(() -> {
                    ensureChatExists(chatId);
                    var response = loadLinks(chatId);
                    trackedLinksCacheService.put(chatId, response);
                    return response;
                });
    }

    private ListLinksResponse loadLinks(long chatId) {
        var links = new ArrayList<LinkResponse>();
        var offset = 0;
        while (true) {
            var subscriptions =
                    linkSubscriptionRepository.findByChatId(chatId, databaseProperties.getPageSize(), offset);
            if (subscriptions.isEmpty()) {
                break;
            }

            var trackedLinksById =
                    trackedLinkRepository
                            .findByIds(subscriptions.stream()
                                    .map(LinkSubscription::linkId)
                                    .distinct()
                                    .toList())
                            .stream()
                            .collect(Collectors.toMap(TrackedLink::id, Function.identity()));

            for (var subscription : subscriptions) {
                var trackedLink = trackedLinksById.get(subscription.linkId());
                if (trackedLink == null) {
                    continue;
                }

                links.add(new LinkResponse(
                        trackedLink.id(), trackedLink.url().toString(), subscription.tags(), subscription.filters()));
            }
            offset += subscriptions.size();
        }

        LOGGER.atInfo()
                .addKeyValue("operation", "getLinks")
                .addKeyValue("chatId", chatId)
                .addKeyValue("cacheHit", false)
                .addKeyValue("linksCount", links.size())
                .log("Tracked links listed");
        return new ListLinksResponse(List.copyOf(links), links.size());
    }

    @Transactional
    public LinkResponse addLink(long chatId, AddLinkRequest request) {
        ensureChatExists(chatId);

        var requestedUrl = URI.create(request.link()).normalize();
        ensureSupportedLink(requestedUrl);
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
        trackedLinksCacheService.evict(chatId);
        return new LinkResponse(
                trackedLink.id(), trackedLink.url().toString(), subscription.tags(), subscription.filters());
    }

    @Transactional
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
        trackedLinksCacheService.evict(chatId);
        return new LinkResponse(
                trackedLink.id(), trackedLink.url().toString(), subscription.tags(), subscription.filters());
    }

    private void ensureChatExists(long chatId) {
        if (!chatRepository.exists(chatId)) {
            throw new ChatNotFoundException(chatId);
        }
    }

    private void ensureSupportedLink(URI link) {
        var supported = externalLinkClients.stream().anyMatch(client -> client.supports(link));
        if (!supported) {
            throw new UnsupportedLinkException(link.toString());
        }
    }

    private void removeLinkIfOrphan(long linkId) {
        if (!linkSubscriptionRepository.existsByLinkId(linkId)) {
            trackedLinkRepository.deleteIfNoSubscriptions(linkId);
        }
    }

    private List<Long> loadTrackedLinkIdsByChatId(long chatId) {
        var linkIds = new LinkedHashSet<Long>();
        var offset = 0;
        while (true) {
            var subscriptions =
                    linkSubscriptionRepository.findByChatId(chatId, databaseProperties.getPageSize(), offset);
            if (subscriptions.isEmpty()) {
                return List.copyOf(linkIds);
            }

            subscriptions.stream().map(LinkSubscription::linkId).forEach(linkIds::add);
            offset += subscriptions.size();
        }
    }
}
