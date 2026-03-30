package backend.academy.linktracker.bot.api;

import backend.academy.linktracker.bot.api.dto.LinkUpdate;
import backend.academy.linktracker.bot.api.dto.ProcessingFailureReport;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Validated
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public interface BotApi {

    @PostMapping(path = "/updates", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> processUpdate(@Valid @RequestBody LinkUpdate linkUpdate);

    @PostMapping(path = "/reports", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> processReport(@Valid @RequestBody ProcessingFailureReport report);
}
