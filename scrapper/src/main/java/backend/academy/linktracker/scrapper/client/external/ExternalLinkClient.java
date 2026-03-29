package backend.academy.linktracker.scrapper.client.external;

import backend.academy.linktracker.scrapper.domain.LinkCheckResult;
import backend.academy.linktracker.scrapper.domain.TrackedLink;
import java.net.URI;

public interface ExternalLinkClient {

    boolean supports(URI url);

    LinkCheckResult fetchUpdates(TrackedLink trackedLink);
}
