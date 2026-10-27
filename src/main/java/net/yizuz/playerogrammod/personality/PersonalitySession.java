package net.yizuz.playerogrammod.personality;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mutable navigation/answer state for one personality-test run. Kept separate
 * from the screen so back/forward and answer preservation are unit testable.
 */
public final class PersonalitySession {
    private final List<PersonalityQuestion> questions = PersonalityTest.QUESTIONS;
    private final Map<String, LinkedHashSet<String>> answers = new LinkedHashMap<>();
    private int index;

    public PersonalitySession() {
    }

    public PersonalitySession(PersonalityProfile profile) {
        if (profile != null) {
            for (PersonalityQuestion question : this.questions) {
                List<String> selected = profile.answers(question.id());
                if (!selected.isEmpty()) {
                    this.answers.put(question.id(), new LinkedHashSet<>(selected));
                }
            }
        }
    }

    public int size() {
        return this.questions.size();
    }

    public int index() {
        return this.index;
    }

    public int number() {
        return this.index + 1;
    }

    public PersonalityQuestion current() {
        return this.questions.get(this.index);
    }

    public boolean isFirst() {
        return this.index == 0;
    }

    public boolean isLast() {
        return this.index == this.questions.size() - 1;
    }

    public Set<String> selected(String questionId) {
        Set<String> selected = this.answers.get(questionId);
        return selected == null ? Set.of() : Set.copyOf(selected);
    }

    public boolean isSelected(String questionId, String optionId) {
        Set<String> selected = this.answers.get(questionId);
        return selected != null && selected.contains(optionId);
    }

    public void select(PersonalityQuestion question, String optionId) {
        if (question == null || !question.hasOption(optionId)) {
            return;
        }
        LinkedHashSet<String> selected = this.answers.computeIfAbsent(
                question.id(), ignored -> new LinkedHashSet<>());
        if (!question.multiple()) {
            selected.clear();
            selected.add(optionId);
            return;
        }
        if ("none".equals(optionId)) {
            selected.clear();
            selected.add(optionId);
            return;
        }
        selected.remove("none");
        if (!selected.add(optionId)) {
            selected.remove(optionId);
        }
    }

    public boolean currentAnswered() {
        return PersonalityTest.answered(current(), this.answers.get(current().id()));
    }

    public boolean advance() {
        if (!currentAnswered() || this.index >= this.questions.size() - 1) {
            return false;
        }
        this.index++;
        return true;
    }

    public boolean back() {
        if (this.index <= 0) {
            return false;
        }
        this.index--;
        return true;
    }

    public boolean isComplete() {
        return PersonalityTest.allAnswered(this.answers);
    }

    public PersonalityProfile toProfile() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (PersonalityQuestion question : this.questions) {
            Set<String> selected = this.answers.get(question.id());
            result.put(question.id(), selected == null ? List.of() : List.copyOf(selected));
        }
        return PersonalityProfile.fromAnswers(result);
    }

    public Map<String, List<String>> answers() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashSet<String>> entry : this.answers.entrySet()) {
            result.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return result;
    }
}
