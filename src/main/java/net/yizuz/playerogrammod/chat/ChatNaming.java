package net.yizuz.playerogrammod.chat;

/**
 * Pure naming rules for the AI chat prefix.
 */
public final class ChatNaming {
    public static final String DEFAULT_NAME = "Player-O-Gram";

    public static String displayName(String configured) {
        return configured == null || configured.isBlank() ? DEFAULT_NAME : configured.trim();
    }

    public static String prefix(String configured) {
        return "[" + displayName(configured) + "] ";
    }

    private ChatNaming() {
    }
}
