package backend.academy.linktracker.scrapper.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.hibernate.validator.constraints.URL;

public record AddLinkRequest(@NotBlank @URL String link, List<@NotBlank String> tags, List<@NotBlank String> filters) {}
