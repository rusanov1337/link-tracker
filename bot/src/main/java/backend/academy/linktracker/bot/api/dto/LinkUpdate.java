package backend.academy.linktracker.bot.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.hibernate.validator.constraints.URL;

public record LinkUpdate(
        @NotNull Long id,
        @NotBlank @URL String url,
        @NotBlank String description,
        @NotEmpty List<@NotNull Long> tgChatIds) {}
