package backend.academy.linktracker.bot;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

final class TelegramRequestBodyParser {
    private TelegramRequestBodyParser() {}

    static String getFormFieldValue(String body, String fieldName) {
        for (String pair : body.split("&")) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length > 0 && fieldName.equals(keyValue[0])) {
                String value = keyValue.length == 2 ? keyValue[1] : "";
                return URLDecoder.decode(value, StandardCharsets.UTF_8);
            }
        }

        throw new IllegalArgumentException("Field '" + fieldName + "' not found in request body: " + body);
    }
}
