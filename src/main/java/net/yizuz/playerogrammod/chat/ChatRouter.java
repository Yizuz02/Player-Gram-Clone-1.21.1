package net.yizuz.playerogrammod.chat;

import java.util.Optional;

public final class ChatRouter {
    public static Optional<String> extract(String prefix, String message) {
        if (prefix == null || prefix.isBlank() || message == null) {
            return Optional.empty();
        }
        String marker = prefix.trim();
        if (marker.isEmpty()) {
            return Optional.empty();
        }
        String text = message.stripLeading();
        if (!text.regionMatches(true, 0, marker, 0, marker.length())) {
            return Optional.empty();
        }
        String prompt = text.substring(marker.length()).strip();
        return prompt.isEmpty() ? Optional.empty() : Optional.of(prompt);
    }

    private ChatRouter() {
    }
}
