package backend.academy.linktracker.scrapper.client.bot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record RawLinkUpdateEvent(
        @Positive long id,
        @NotBlank String url,
        @NotBlank String description,
        @NotBlank String author,
        @NotEmpty List<@NotNull @Positive Long> tgChatIds) {}
