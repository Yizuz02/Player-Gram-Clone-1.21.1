package net.yizuz.playerogrammod.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatNamingTest {
    @Test
    void blankNamesUseTheDefaultPrefix() {
        assertEquals("Player-O-Gram", ChatNaming.displayName(null));
        assertEquals("Player-O-Gram", ChatNaming.displayName(""));
        assertEquals("Player-O-Gram", ChatNaming.displayName("   "));
        assertEquals("[Player-O-Gram] ", ChatNaming.prefix(null));
    }

    @Test
    void configuredDisplayNameBecomesTheChatPrefix() {
        assertEquals("Guide", ChatNaming.displayName("Guide"));
        assertEquals("Guide", ChatNaming.displayName("  Guide  "));
        assertEquals("[Guide] ", ChatNaming.prefix("Guide"));
    }
}
