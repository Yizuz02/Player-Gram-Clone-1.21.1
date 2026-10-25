package net.yizuz.playerogrammod.ring;

/**
 * Validation limits and sanitizing rules shared by the screen and the server.
 * Limits are rejected, never silently truncated.
 */
public final class RingValidation {
    public static final int MAX_DISPLAY_NAME = 32;
    public static final int MAX_SKIN_USERNAME = 16;
    public static final int MAX_PROMPT = 8000;

    public static String validateDisplayName(String value) {
        String text = value == null ? "" : value;
        if (text.length() > MAX_DISPLAY_NAME) {
            return "Display name must be at most " + MAX_DISPLAY_NAME + " characters.";
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c)) {
                return "Display name contains an invalid control character.";
            }
        }
        return null;
    }

    public static String validateSkinUsername(String value) {
        String text = value == null ? "" : value;
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > MAX_SKIN_USERNAME) {
            return "Skin username must be at most " + MAX_SKIN_USERNAME + " characters.";
        }
        if (!text.matches("[A-Za-z0-9_]+")) {
            return "Skin username may only contain letters, numbers, and underscores.";
        }
        return null;
    }

    public static String validatePrompt(String value) {
        String text = value == null ? "" : value;
        if (text.length() > MAX_PROMPT) {
            return "System prompt must be at most " + MAX_PROMPT + " characters.";
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\0' || (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) {
                return "System prompt contains an invalid control character.";
            }
        }
        return null;
    }

    public static String validateSettings(String displayName, String skinUsername, String prompt) {
        String error = validateDisplayName(displayName);
        if (error != null) {
            return error;
        }
        error = validateSkinUsername(skinUsername);
        if (error != null) {
            return error;
        }
        return validatePrompt(prompt);
    }

    public static RingConfig sanitize(RingConfig config) {
        if (config == null) {
            return RingConfig.DEFAULT;
        }
        String display = validateDisplayName(config.displayName()) == null ? config.displayName() : "";
        String skin = validateSkinUsername(config.skinUsername()) == null ? config.skinUsername() : "";
        boolean custom = config.customPrompt();
        String prompt = config.systemPrompt();
        if (custom && validatePrompt(prompt) != null) {
            custom = false;
            prompt = "";
        }
        return config.withSettings(display, skin, custom, prompt);
    }

    private RingValidation() {
    }
}
