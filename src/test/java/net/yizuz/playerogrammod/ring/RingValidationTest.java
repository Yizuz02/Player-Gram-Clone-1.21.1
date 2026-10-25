package net.yizuz.playerogrammod.ring;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RingValidationTest {
    @Test
    void displayNameLimitsAndControlsAreRejected() {
        assertNull(RingValidation.validateDisplayName(""));
        assertNull(RingValidation.validateDisplayName("A".repeat(RingValidation.MAX_DISPLAY_NAME)));
        assertNotNull(RingValidation.validateDisplayName("A".repeat(RingValidation.MAX_DISPLAY_NAME + 1)));
        assertNotNull(RingValidation.validateDisplayName("bad\nname"));
    }

    @Test
    void skinUsernameUsesTheMinecraftNameAlphabet() {
        assertNull(RingValidation.validateSkinUsername(""));
        assertNull(RingValidation.validateSkinUsername("Notch_123"));
        assertNotNull(RingValidation.validateSkinUsername("bad name"));
        assertNotNull(RingValidation.validateSkinUsername("A".repeat(RingValidation.MAX_SKIN_USERNAME + 1)));
        assertNotNull(RingValidation.validateSkinUsername("hyphen-name"));
    }

    @Test
    void promptAllowsNewlinesButRejectsNulAndOversize() {
        assertNull(RingValidation.validatePrompt("line one\nline two\t"));
        assertNotNull(RingValidation.validatePrompt("nul\0"));
        assertNotNull(RingValidation.validatePrompt("A".repeat(RingValidation.MAX_PROMPT + 1)));
    }

    @Test
    void validationAcceptsAValidConfiguration() {
        assertNull(RingValidation.validateSettings("Guide", "Notch", "Be helpful."));
    }

    @Test
    void sanitizePreservesIdentityAndDropsInvalidFields() {
        UUID id = UUID.randomUUID();
        RingConfig bad = new RingConfig(id, "bad\nname", "not a name", true,
                "A".repeat(RingValidation.MAX_PROMPT + 1));
        RingConfig safe = RingValidation.sanitize(bad);

        assertEquals(id, safe.ringId());
        assertEquals("", safe.displayName());
        assertEquals("", safe.skinUsername());
        assertFalse(safe.customPrompt());
        assertEquals("", safe.systemPrompt());
    }

    @Test
    void sanitizeKeepsValidFields() {
        RingConfig valid = RingConfig.DEFAULT.withRingId(UUID.randomUUID())
                .withSettings("Guide", "Notch", true, "Be helpful.");
        assertEquals(valid, RingValidation.sanitize(valid));
    }
}
