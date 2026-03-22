package backend.academy.linktracker.scrapper.api.dto;

import static backend.academy.linktracker.scrapper.validation.ValidationPatterns.HTTP_URL;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record LinkResponse(
        @NotNull Long id,
        @NotBlank @Pattern(regexp = HTTP_URL) String url,
        List<@NotBlank String> tags,
        List<@NotBlank String> filters) {}
