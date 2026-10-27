package net.yizuz.playerogrammod.personality;

import net.yizuz.playerogrammod.prompt.SystemPromptProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalityProfileTest {
    @Test
    void defaultProfileIsSensibleAndBackwardCompatible() {
        PersonalityProfile profile = PersonalityProfile.defaultProfile();
        assertFalse(profile.completed());
        assertTrue(String.join("\n", profile.summaryLines()).contains("Default personality"));
        assertEquals(SystemPromptProvider.DEFAULT_PROMPT, profile.buildSystemPrompt());
        assertTrue(profile.buildSystemPrompt().contains("Never pretend"));
    }

    @Test
    void completedProfileProvidesReadableSummaryAndStructuredPrompt() {
        PersonalityProfile profile = PersonalityProfile.fromAnswers(PersonalityTestTest.completeAnswers());
        String summary = String.join("\n", profile.summaryLines());
        assertTrue(summary.contains("Language: English"));
        assertTrue(summary.contains("Communication:"));
        assertTrue(summary.contains("Social:"));
        assertTrue(summary.contains("Thinking:"));
        assertTrue(summary.contains("Values:"));
        assertTrue(summary.contains("Interests:"));
        assertTrue(summary.contains("Habits:"));
        assertTrue(summary.contains("Emotional expression:"));

        String prompt = profile.buildSystemPrompt();
        assertTrue(prompt.contains("Your displayed name is \"the Lantern Ring\"."));
        assertTrue(prompt.contains("Language profile:"));
        assertTrue(prompt.contains("English"));
        assertTrue(prompt.contains("Never pretend to understand or speak an unsupported language."));
        assertTrue(prompt.contains("Communication:"));
        assertTrue(prompt.contains("Social behavior:"));
        assertTrue(prompt.contains("Thinking:"));
        assertTrue(prompt.contains("Values and relationships:"));
        assertTrue(prompt.contains("Personal traits:"));
        assertTrue(prompt.contains("Response length:"));
        assertTrue(prompt.contains("Conflict resolution:"));
        assertTrue(prompt.length() < 8000);
    }

    @Test
    void generatedPromptUsesTheRingsDisplayName() {
        PersonalityProfile profile = PersonalityProfile.fromAnswers(PersonalityTestTest.completeAnswers());
        String prompt = profile.buildSystemPrompt("Yizuz Guide");
        assertTrue(prompt.contains("Your displayed name is \"Yizuz Guide\"."));
        assertTrue(prompt.contains("Use it when referring to yourself"));
    }

    @Test
    void adaptivePreferredLanguageChangesThePromptAndSummary() {
        Map<String, List<String>> answers = PersonalityTestTest.completeAnswers();
        answers.put("preferred_language", List.of("player"));
        PersonalityProfile profile = PersonalityProfile.fromAnswers(answers);
        String summary = String.join("\n", profile.summaryLines());
        assertTrue(summary.contains("adapt to the player's language"));
        assertTrue(profile.buildSystemPrompt().contains("adapt to the player's language"));
    }

    @Test
    void jsonRoundTripPreservesCompletedStateAndAnswers() {
        PersonalityProfile profile = PersonalityProfile.fromAnswers(PersonalityTestTest.completeAnswers());
        PersonalityProfile parsed = PersonalityProfile.fromJson(profile.toJson());
        assertEquals(profile, parsed);
        assertTrue(parsed.completed());
    }

    @Test
    void malformedOrMissingJsonFallsBackToDefault() {
        assertEquals(PersonalityProfile.defaultProfile(), PersonalityProfile.fromJson(null));
        assertEquals(PersonalityProfile.defaultProfile(), PersonalityProfile.fromJson(""));
        assertEquals(PersonalityProfile.defaultProfile(), PersonalityProfile.fromJson("not json"));
    }

    @Test
    void answersAreDefensivelyCopied() {
        Map<String, List<String>> answers = PersonalityTestTest.completeAnswers();
        PersonalityProfile profile = PersonalityProfile.fromAnswers(answers);
        answers.get("languages").clear();
        assertFalse(profile.answers("languages").isEmpty());
        assertNotEquals(PersonalityProfile.defaultProfile(), profile);
    }
}
