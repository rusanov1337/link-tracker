package backend.academy.linktracker.scrapper.api.dto;

import static backend.academy.linktracker.scrapper.validation.ValidationPatterns.HTTP_URL;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RemoveLinkRequest(
        @NotBlank @Pattern(regexp = HTTP_URL) String link) {}
