package net.yizuz.playerogrammod.chat;

import net.yizuz.playerogrammod.ai.LlamaClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationManagerTest {
    @Test
    void storesOrderedRolesAndContents() {
        ConversationManager manager = new ConversationManager(12, 6000);
        UUID player = UUID.randomUUID();
        manager.get(player).addExchange("hello", "hi there");
        manager.get(player).addExchange("again", "still here");

        List<LlamaClient.Message> snapshot = manager.get(player).snapshot();

        assertEquals(List.of("user", "assistant", "user", "assistant"),
                snapshot.stream().map(LlamaClient.Message::role).toList());
        assertEquals(List.of("hello", "hi there", "again", "still here"),
                snapshot.stream().map(LlamaClient.Message::content).toList());
    }

    @Test
    void keepsConversationsIsolated() {
        ConversationManager manager = new ConversationManager(12, 6000);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        manager.get(first).addExchange("only first", "answer");

        assertTrue(manager.get(second).snapshot().isEmpty());
        assertEquals(2, manager.get(first).messageCount());
    }

    @Test
    void trimsWholeExchangesByMessageCount() {
        ConversationManager manager = new ConversationManager(2, 0);
        UUID player = UUID.randomUUID();
        manager.get(player).addExchange("u1", "a1");
        manager.get(player).addExchange("u2", "a2");

        List<LlamaClient.Message> snapshot = manager.get(player).snapshot();
        assertEquals(2, snapshot.size());
        assertEquals("u2", snapshot.get(0).content());
        assertEquals("a2", snapshot.get(1).content());
    }

    @Test
    void trimsWholeExchangesByCharacterCount() {
        ConversationManager manager = new ConversationManager(12, 10);
        UUID player = UUID.randomUUID();
        manager.get(player).addExchange("12345", "12345");
        manager.get(player).addExchange("x", "y");

        List<LlamaClient.Message> snapshot = manager.get(player).snapshot();
        assertEquals(2, snapshot.size());
        assertEquals("x", snapshot.get(0).content());
        assertEquals("y", snapshot.get(1).content());
    }

    @Test
    void zeroMessageLimitDisablesHistory() {
        ConversationManager manager = new ConversationManager(0, 0);
        UUID player = UUID.randomUUID();
        manager.get(player).addExchange("u", "a");

        assertTrue(manager.get(player).snapshot().isEmpty());
    }

    @Test
    void busyFlagIsExclusiveUntilEnded() {
        ConversationManager manager = new ConversationManager(12, 6000);
        ConversationManager.Conversation conversation = manager.get(UUID.randomUUID());

        assertTrue(conversation.tryBegin());
        assertFalse(conversation.tryBegin());
        conversation.end();
        assertTrue(conversation.tryBegin());
    }

    @Test
    void clearAllRemovesEveryConversation() {
        ConversationManager manager = new ConversationManager(12, 6000);
        manager.get(UUID.randomUUID()).addExchange("u", "a");
        manager.clearAll();

        assertEquals(0, manager.size());
    }
}
