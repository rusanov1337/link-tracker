package backend.academy.linktracker.scrapper.api.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record RemoveLinkRequest(@NotBlank @URL String link) {}
