package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.domain.LinkCheckResult;
import backend.academy.linktracker.scrapper.domain.TrackedLink;

record FetchedLinkState(
        TrackedLink trackedLink, boolean hasSupportedClient, LinkCheckResult checkResult, RuntimeException failure) {

    static FetchedLinkState noSupportedClient(TrackedLink trackedLink) {
        return new FetchedLinkState(trackedLink, false, null, null);
    }

    static FetchedLinkState success(TrackedLink trackedLink, LinkCheckResult checkResult) {
        return new FetchedLinkState(trackedLink, true, checkResult, null);
    }

    static FetchedLinkState failure(TrackedLink trackedLink, RuntimeException failure) {
        return new FetchedLinkState(trackedLink, true, null, failure);
    }
}
