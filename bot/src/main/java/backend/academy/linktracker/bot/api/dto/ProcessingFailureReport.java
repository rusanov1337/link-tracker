package backend.academy.linktracker.bot.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ProcessingFailureReport(
        @NotBlank String description, @NotEmpty List<@NotNull Long> tgChatIds) {}
