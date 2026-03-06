package backend.academy.linktracker.scrapper.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.hibernate.validator.constraints.URL;

public record LinkResponse(
        @NotNull Long id, @NotBlank @URL String url, List<@NotBlank String> tags, List<@NotBlank String> filters) {}
