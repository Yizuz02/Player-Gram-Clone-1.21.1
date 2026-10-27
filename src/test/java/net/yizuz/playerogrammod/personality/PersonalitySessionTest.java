package net.yizuz.playerogrammod.personality;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalitySessionTest {
    @Test
    void singleSelectionReplacesAndMultipleSelectionToggles() {
        PersonalitySession session = new PersonalitySession();
        PersonalityQuestion preferred = PersonalityTest.question("preferred_language");
        session.select(preferred, "english");
        session.select(preferred, "french");
        assertEquals(Set.of("french"), session.selected("preferred_language"));

        PersonalityQuestion interests = PersonalityTest.question("interests");
        session.select(interests, "technology");
        session.select(interests, "stories");
        assertEquals(Set.of("technology", "stories"), session.selected("interests"));
        session.select(interests, "technology");
        assertEquals(Set.of("stories"), session.selected("interests"));
    }

    @Test
    void noneClearsTheOtherMultiSelections() {
        PersonalitySession session = new PersonalitySession();
        PersonalityQuestion interests = PersonalityTest.question("interests");
        session.select(interests, "technology");
        session.select(interests, "stories");
        session.select(interests, "none");
        assertEquals(Set.of("none"), session.selected("interests"));
        session.select(interests, "music");
        assertEquals(Set.of("music"), session.selected("interests"));
    }

    @Test
    void navigationPreservesAnswersAndRespectsReadiness() {
        PersonalitySession session = new PersonalitySession();
        assertEquals(0, session.index());
        assertFalse(session.advance());

        PersonalityQuestion first = session.current();
        session.select(first, "english");
        assertTrue(session.advance());
        assertEquals(1, session.index());
        assertFalse(session.isFirst());

        PersonalityQuestion second = session.current();
        session.select(second, "english");
        assertTrue(session.back());
        assertTrue(session.isSelected(first.id(), "english"));
        assertTrue(session.advance());
        assertTrue(session.isSelected(second.id(), "english"));
    }

    @Test
    void backAtFirstQuestionDoesNothing() {
        PersonalitySession session = new PersonalitySession();
        assertFalse(session.back());
        assertEquals(0, session.index());
    }

    @Test
    void completedSessionBuildsAValidProfile() {
        PersonalitySession session = new PersonalitySession();
        while (true) {
            PersonalityQuestion question = session.current();
            String option = question.options().get(0).id();
            if (question.id().equals("languages") || question.id().equals("preferred_language")) {
                option = "english";
            }
            session.select(question, option);
            if (session.isLast()) {
                break;
            }
            assertTrue(session.advance());
        }
        assertTrue(session.isComplete());
        PersonalityProfile profile = session.toProfile();
        assertTrue(profile.completed());
        assertNull(PersonalityTest.validate(profile));
        assertNotEquals(PersonalityProfile.defaultProfile(), profile);
    }

    @Test
    void retakeStartsWithExistingAnswersPreserved() {
        PersonalityProfile profile = PersonalityProfile.fromAnswers(PersonalityTestTest.completeAnswers());
        PersonalitySession session = new PersonalitySession(profile);
        assertEquals(0, session.index());
        assertTrue(session.isSelected("languages", "english"));
        assertTrue(session.isSelected("preferred_language", "english"));
        assertTrue(session.currentAnswered());
        assertEquals(profile.answers(), session.toProfile().answers());
    }
}
