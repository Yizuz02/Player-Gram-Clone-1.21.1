package net.yizuz.playerogrammod.chat;

import net.yizuz.playerogrammod.ai.LlamaClient;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ConversationManager {
    public record Exchange(String user, String assistant) {
    }

    private final Map<UUID, Conversation> conversations = new ConcurrentHashMap<>();
    private final int maxMessages;
    private final int maxChars;

    public ConversationManager(int maxMessages, int maxChars) {
        this.maxMessages = Math.max(0, maxMessages);
        this.maxChars = Math.max(0, maxChars);
    }

    public Conversation get(UUID playerId) {
        return conversations.computeIfAbsent(playerId,
                ignored -> new Conversation(maxMessages, maxChars));
    }

    public void clear(UUID playerId) {
        conversations.remove(playerId);
    }

    public void clearAll() {
        conversations.clear();
    }

    public int size() {
        return conversations.size();
    }

    public final class Conversation {
        private final Deque<Exchange> exchanges = new ArrayDeque<>();
        private final int conversationMaxMessages;
        private final int conversationMaxChars;
        private boolean busy;

        private Conversation(int maxMessages, int maxChars) {
            this.conversationMaxMessages = maxMessages;
            this.conversationMaxChars = maxChars;
        }

        public synchronized boolean tryBegin() {
            if (busy) {
                return false;
            }
            busy = true;
            return true;
        }

        public synchronized void end() {
            busy = false;
        }

        public synchronized boolean isBusy() {
            return busy;
        }

        public synchronized List<LlamaClient.Message> snapshot() {
            List<LlamaClient.Message> messages = new ArrayList<>();
            for (Exchange exchange : exchanges) {
                messages.add(new LlamaClient.Message("user", exchange.user()));
                messages.add(new LlamaClient.Message("assistant", exchange.assistant()));
            }
            return messages;
        }

        public synchronized void addExchange(String user, String assistant) {
            exchanges.addLast(new Exchange(user, assistant));
            trim();
        }

        public synchronized void clear() {
            exchanges.clear();
        }

        public synchronized int messageCount() {
            return exchanges.size() * 2;
        }

        public synchronized int characterCount() {
            int total = 0;
            for (Exchange exchange : exchanges) {
                total += exchange.user().length() + exchange.assistant().length();
            }
            return total;
        }

        private void trim() {
            while (!exchanges.isEmpty()) {
                boolean tooMany = exchanges.size() * 2 > conversationMaxMessages;
                boolean tooLong = conversationMaxChars > 0 && characterCount() > conversationMaxChars;
                if (!tooMany && !tooLong) {
                    break;
                }
                exchanges.removeFirst();
            }
        }
    }
}
