package net.yizuz.playerogrammod.personality;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalityTestTest {
    @Test
    void definesTwentyQuestionsAcrossTheRequiredAreas() {
        assertEquals(20, PersonalityTest.QUESTIONS.size());
        Set<String> ids = new LinkedHashSet<>();
        Set<String> categories = new LinkedHashSet<>();
        Set<String> multiple = new LinkedHashSet<>();
        for (PersonalityQuestion question : PersonalityTest.QUESTIONS) {
            assertTrue(ids.add(question.id()), "duplicate id " + question.id());
            categories.add(question.category());
            assertFalse(question.options().isEmpty());
            assertTrue(question.options().size() >= 4);
            Set<String> optionIds = new LinkedHashSet<>();
            for (PersonalityOption option : question.options()) {
                assertTrue(optionIds.add(option.id()), "duplicate option " + option.id());
                assertFalse(option.label().isBlank());
            }
            if (question.multiple()) {
                multiple.add(question.id());
            }
        }
        assertEquals(Set.of("Language", "Communication", "Social", "Thinking",
                "Values and relationships", "Personal traits"), categories);
        assertEquals(Set.of("languages", "interests", "habits"), multiple);
    }

    @Test
    void includesSupportedLanguagesAndNeutralChoices() {
        PersonalityQuestion languages = PersonalityTest.question("languages");
        assertNotNull(languages);
        assertTrue(languages.multiple());
        assertEquals(PersonalityTest.LANGUAGES, languages.options());

        PersonalityQuestion preferred = PersonalityTest.question("preferred_language");
        assertNotNull(preferred);
        assertTrue(preferred.options().containsAll(PersonalityTest.LANGUAGES));
        assertTrue(preferred.options().stream().anyMatch(option -> option.id().equals("player")));
        assertTrue(preferred.options().stream().anyMatch(option -> option.id().equals("depends")));

        long dependsCount = PersonalityTest.QUESTIONS.stream()
                .filter(question -> question.options().stream()
                        .anyMatch(option -> option.id().equals("depends")))
                .count();
        assertTrue(dependsCount >= 14);
        assertTrue(PersonalityTest.question("interests").options().stream()
                .anyMatch(option -> option.id().equals("none")));
        assertTrue(PersonalityTest.question("habits").options().stream()
                .anyMatch(option -> option.id().equals("none")));
    }

    @Test
    void acceptsAFullyAnsweredProfile() {
        assertNull(PersonalityTest.validate(PersonalityProfile.fromAnswers(completeAnswers())));
    }

    @Test
    void acceptsTheDefaultProfileAndRejectsIncompleteCompletedProfiles() {
        assertNull(PersonalityTest.validate(PersonalityProfile.defaultProfile()));

        Map<String, List<String>> missing = completeAnswers();
        missing.remove("humor");
        assertNotNull(PersonalityTest.validate(PersonalityProfile.fromAnswers(missing)));

        Map<String, List<String>> tooMany = completeAnswers();
        tooMany.put("humor", List.of("dry", "playful"));
        assertNotNull(PersonalityTest.validate(PersonalityProfile.fromAnswers(tooMany)));

        Map<String, List<String>> unknownOption = completeAnswers();
        unknownOption.put("humor", List.of("sarcastic"));
        assertNotNull(PersonalityTest.validate(PersonalityProfile.fromAnswers(unknownOption)));
    }

    @Test
    void rejectsAPreferredLanguageThatIsNotUnderstood() {
        Map<String, List<String>> answers = completeAnswers();
        answers.put("languages", List.of("english"));
        answers.put("preferred_language", List.of("french"));
        assertNotNull(PersonalityTest.validate(PersonalityProfile.fromAnswers(answers)));
    }

    @Test
    void allAnsweredRequiresEveryQuestion() {
        assertTrue(PersonalityTest.allAnswered(completeAnswers()));
        Map<String, List<String>> missing = completeAnswers();
        missing.remove("decisions");
        assertFalse(PersonalityTest.allAnswered(missing));
    }

    static Map<String, List<String>> completeAnswers() {
        Map<String, List<String>> answers = new LinkedHashMap<>();
        for (PersonalityQuestion question : PersonalityTest.QUESTIONS) {
            String option = question.options().get(0).id();
            if (question.id().equals("languages")) {
                option = "english";
            } else if (question.id().equals("preferred_language")) {
                option = "english";
            }
            answers.put(question.id(), new ArrayList<>(List.of(option)));
        }
        return answers;
    }
}
