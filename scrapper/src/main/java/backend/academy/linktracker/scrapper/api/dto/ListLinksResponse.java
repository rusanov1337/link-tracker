package backend.academy.linktracker.scrapper.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

public record ListLinksResponse(
        @NotNull List<@Valid LinkResponse> links,
        @PositiveOrZero int size) {}
