package net.yizuz.playerogrammod.ring;

import net.yizuz.playerogrammod.personality.PersonalityProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingConfigTest {
    @Test
    void defaultsUseTheServerPromptAndNoOverrides() {
        RingConfig config = RingConfig.DEFAULT;
        assertFalse(config.hasRingId());
        assertEquals("", config.displayName());
        assertEquals("", config.skinUsername());
        assertFalse(config.customPrompt());
        assertEquals("server-default", config.effectivePrompt("server-default"));
    }

    @Test
    void customPromptCanBeEmptyAndIsDistinctFromDefault() {
        RingConfig config = RingConfig.DEFAULT.withRingId(UUID.randomUUID())
                .withSettings("Companion", "Alex", true, "");
        assertTrue(config.hasRingId());
        assertEquals("", config.effectivePrompt("server-default"));
        assertEquals("Companion", config.displayName());
        assertEquals("Alex", config.skinUsername());
    }

    @Test
    void jsonRoundTripPreservesEveryField() {
        UUID id = UUID.randomUUID();
        RingConfig config = new RingConfig(id, "Guide", "Notch", true, "Line one\nLine two");
        RingConfig parsed = RingConfig.fromJson(config.toJson());

        assertEquals(config, parsed);
        assertEquals(id, parsed.ringId());
        assertTrue(parsed.customPrompt());
        assertEquals("Line one\nLine two", parsed.systemPrompt());
    }

    @Test
    void malformedJsonFallsBackToDefaults() {
        assertEquals(RingConfig.DEFAULT, RingConfig.fromJson("not json"));
        assertEquals(RingConfig.DEFAULT, RingConfig.fromJson(""));
        assertEquals(RingConfig.DEFAULT, RingConfig.fromJson(null));
    }

    @Test
    void independentRingsDoNotShareIdentity() {
        RingConfig first = RingConfig.DEFAULT.withRingId(UUID.randomUUID());
        RingConfig second = RingConfig.DEFAULT.withRingId(UUID.randomUUID());
        assertNotEquals(first.ringId(), second.ringId());
        assertEquals(first.ringId(), first.withSettings("A", "B", true, "C").ringId());
    }

    @Test
    void emptyNameAndSkinFallBackToTheOwner() {
        RingConfig config = RingConfig.DEFAULT;
        assertEquals("Steve", config.effectiveSkinUsername("Steve"));
        assertEquals("Steve", config.effectiveDisplayName("Steve"));

        RingConfig customSkin = config.withSettings("", "Alex", false, "");
        assertEquals("Alex", customSkin.effectiveSkinUsername("Steve"));
        assertEquals("Alex", customSkin.effectiveDisplayName("Steve"));

        RingConfig customName = config.withSettings("Guide", "", false, "");
        assertEquals("Steve", customName.effectiveSkinUsername("Steve"));
        assertEquals("Guide", customName.effectiveDisplayName("Steve"));
    }

    @Test
    void personalityBelongsToTheRingAndSurvivesSettingsChanges() {
        PersonalityProfile profile = PersonalityProfile.fromAnswers(Map.of(
                "languages", List.of("english"),
                "preferred_language", List.of("english")));
        UUID id = UUID.randomUUID();
        RingConfig config = new RingConfig(id, "Guide", "Notch", false, "")
                .withPersonality(profile);

        assertEquals(profile, config.personality());
        assertEquals(profile, config.withSettings("New name", "Alex", true, "Prompt")
                .personality());
        assertEquals(profile, config.withRingId(UUID.randomUUID()).personality());
        assertEquals(profile, RingConfig.fromJson(config.toJson()).personality());
        assertNotEquals(profile, RingConfig.DEFAULT.personality());
    }

    @Test
    void existingJsonWithoutAPersonalityKeepsItsManualPromptAndGetsTheDefaultProfile() {
        UUID id = UUID.randomUUID();
        String legacy = "{\"ringId\":\"" + id + "\",\"displayName\":\"Guide\","
                + "\"skinUsername\":\"\",\"customPrompt\":true,"
                + "\"systemPrompt\":\"Legacy prompt\"}";
        RingConfig config = RingConfig.fromJson(legacy);
        assertFalse(config.personality().completed());
        assertEquals(PersonalityProfile.defaultProfile(), config.personality());
        assertEquals("Legacy prompt", config.effectivePrompt("server-default"));
    }
}
