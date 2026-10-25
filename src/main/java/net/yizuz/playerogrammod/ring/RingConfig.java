package net.yizuz.playerogrammod.ring;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.UUID;

/**
 * Persistent configuration carried by one Lantern Ring item stack.
 *
 * <p>{@code customPrompt=false} means "use the server's default system prompt";
 * {@code customPrompt=true} with an empty string means "explicitly use no custom
 * system prompt". The two states stay distinct all the way to the model request.</p>
 */
public record RingConfig(
        UUID ringId,
        String displayName,
        String skinUsername,
        boolean customPrompt,
        String systemPrompt
) {
    public static final UUID UNASSIGNED = new UUID(0L, 0L);
    public static final RingConfig DEFAULT = new RingConfig(UNASSIGNED, "", "", false, "");

    public RingConfig {
        ringId = ringId == null ? UNASSIGNED : ringId;
        displayName = displayName == null ? "" : displayName;
        skinUsername = skinUsername == null ? "" : skinUsername;
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
    }

    public boolean hasRingId() {
        return ringId != null && !UNASSIGNED.equals(ringId);
    }

    public RingConfig withRingId(UUID id) {
        return new RingConfig(id, displayName, skinUsername, customPrompt, systemPrompt);
    }

    public RingConfig withSettings(String displayName, String skinUsername,
                                   boolean customPrompt, String systemPrompt) {
        return new RingConfig(ringId, displayName, skinUsername, customPrompt, systemPrompt);
    }

    public String effectivePrompt(String defaultPrompt) {
        return customPrompt ? systemPrompt : defaultPrompt;
    }

    public String effectiveSkinUsername(String ownerName) {
        if (!skinUsername.isBlank()) {
            return skinUsername;
        }
        return ownerName == null ? "" : ownerName;
    }

    public String effectiveDisplayName(String ownerName) {
        if (!displayName.isBlank()) {
            return displayName;
        }
        return effectiveSkinUsername(ownerName);
    }

    public String toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("ringId", ringId.toString());
        json.addProperty("displayName", displayName);
        json.addProperty("skinUsername", skinUsername);
        json.addProperty("customPrompt", customPrompt);
        json.addProperty("systemPrompt", systemPrompt);
        return json.toString();
    }

    public static RingConfig fromJson(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        try {
            JsonObject json = JsonParser.parseString(value).getAsJsonObject();
            UUID id = UNASSIGNED;
            if (json.has("ringId") && !json.get("ringId").isJsonNull()) {
                try {
                    id = UUID.fromString(json.get("ringId").getAsString());
                } catch (RuntimeException ignored) {
                    id = UNASSIGNED;
                }
            }
            return new RingConfig(
                    id,
                    string(json, "displayName"),
                    string(json, "skinUsername"),
                    json.has("customPrompt") && !json.get("customPrompt").isJsonNull()
                            && json.get("customPrompt").getAsBoolean(),
                    string(json, "systemPrompt"));
        } catch (RuntimeException e) {
            return DEFAULT;
        }
    }

    private static String string(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
    }
}
