package backend.academy.linktracker.bot.api.dto;

import static backend.academy.linktracker.bot.validation.ValidationPatterns.HTTP_URL;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record LinkUpdate(
        @NotNull Long id,
        @NotBlank @Pattern(regexp = HTTP_URL) String url,
        @NotBlank String description,
        @NotEmpty List<@NotNull Long> tgChatIds) {}
