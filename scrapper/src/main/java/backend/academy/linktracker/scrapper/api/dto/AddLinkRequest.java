package backend.academy.linktracker.scrapper.api.dto;

import static backend.academy.linktracker.scrapper.validation.ValidationPatterns.HTTP_URL;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record AddLinkRequest(
        @NotBlank @Pattern(regexp = HTTP_URL) String link,
        List<@NotBlank String> tags,
        List<@NotBlank String> filters) {}
