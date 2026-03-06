package backend.academy.linktracker.scrapper.client.external;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

public interface ExternalLinkClient {

    boolean supports(URI url);

    Optional<Instant> fetchLastUpdated(URI url);
}
