package net.yizuz.playerogrammod.chat;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatRouterTest {
    @Test
    void extractsAddressedMessages() {
        assertEquals(Optional.of("hello"), ChatRouter.extract("AI:", "AI: hello"));
        assertEquals(Optional.of("hello"), ChatRouter.extract("AI:", "ai:hello"));
        assertEquals(Optional.of("hello"), ChatRouter.extract("AI:", "   AI: hello"));
    }

    @Test
    void supportsCustomPrefixes() {
        assertEquals(Optional.of("hi"), ChatRouter.extract("Player-O-Gram:", "player-o-gram: hi"));
    }

    @Test
    void ignoresOrdinaryChatAndEmptyPrompts() {
        assertTrue(ChatRouter.extract("AI:", "hello there").isEmpty());
        assertTrue(ChatRouter.extract("AI:", "AI:   ").isEmpty());
        assertTrue(ChatRouter.extract("", "AI: hello").isEmpty());
        assertTrue(ChatRouter.extract("AI:", null).isEmpty());
    }
}
