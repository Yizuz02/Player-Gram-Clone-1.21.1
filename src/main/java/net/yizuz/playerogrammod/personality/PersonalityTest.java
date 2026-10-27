package net.yizuz.playerogrammod.personality;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The fixed question set for the per-ring personality profile.
 */
public final class PersonalityTest {
    public static final List<PersonalityOption> LANGUAGES = List.of(
            option("english", "English"),
            option("spanish", "Spanish"),
            option("french", "French"),
            option("german", "German"),
            option("italian", "Italian"),
            option("portuguese", "Portuguese"),
            option("japanese", "Japanese"),
            option("chinese", "Chinese"));

    public static final List<PersonalityQuestion> QUESTIONS = List.of(
            new PersonalityQuestion("languages", "Language",
                    "Which languages do you understand and speak?", true, LANGUAGES),
            new PersonalityQuestion("preferred_language", "Language",
                    "Which language should your hologram prefer?", false,
                    concat(LANGUAGES, List.of(
                            option("player", "Match the player's language"),
                            option("depends", "It depends")))),
            question("vocabulary", "Communication", "What vocabulary should the hologram use?",
                    "simple", "Simple and clear",
                    "balanced", "Balanced",
                    "expressive", "Expressive and varied",
                    "technical", "Technical when needed",
                    "depends", "It depends"),
            question("response_length", "Communication", "How long should responses usually be?",
                    "short", "Short",
                    "medium", "Medium",
                    "long", "Longer explanations",
                    "adaptive", "Match the question",
                    "depends", "It depends"),
            question("formality", "Communication", "How formal should the hologram be?",
                    "casual", "Casual",
                    "neutral", "Neutral",
                    "formal", "Formal",
                    "adaptive", "Match the player",
                    "depends", "It depends"),
            question("style", "Communication", "What conversational style fits you best?",
                    "warm", "Warm",
                    "playful", "Playful",
                    "direct", "Direct",
                    "reflective", "Reflective",
                    "depends", "It depends"),
            question("sociability", "Social", "How sociable should the hologram be?",
                    "reserved", "Reserved",
                    "balanced", "Balanced",
                    "outgoing", "Outgoing",
                    "depends", "It depends"),
            question("humor", "Social", "What kind of humor do you enjoy?",
                    "dry", "Dry",
                    "playful", "Playful",
                    "wholesome", "Wholesome",
                    "rare", "Rarely use humor",
                    "depends", "It depends"),
            question("disagreement", "Social", "How should the hologram disagree with you?",
                    "gentle", "Gently",
                    "honest", "Directly and honestly",
                    "curious", "By asking questions",
                    "avoid", "Avoid arguments",
                    "depends", "It depends"),
            question("conflict", "Social", "How should conflict be handled?",
                    "deescalate", "De-escalate",
                    "mediate", "Mediate",
                    "withdraw", "Withdraw",
                    "address", "Address it directly",
                    "depends", "It depends"),
            question("decisions", "Thinking", "How should the hologram make decisions?",
                    "instinct", "Trust instinct",
                    "evidence", "Follow evidence",
                    "consult", "Consult other perspectives",
                    "values", "Follow values",
                    "depends", "It depends"),
            question("problem_solving", "Thinking", "How should it solve problems?",
                    "step", "Step by step",
                    "big_picture", "Big picture first",
                    "experiment", "Experiment",
                    "ask", "Ask questions first",
                    "depends", "It depends"),
            question("uncertainty", "Thinking", "What should it do when uncertain?",
                    "state", "Say when unsure",
                    "estimate", "Give a best estimate",
                    "context", "Ask for context",
                    "avoid", "Avoid guessing",
                    "depends", "It depends"),
            question("mistakes", "Thinking", "How should it respond to its own mistakes?",
                    "own", "Own it immediately",
                    "explain", "Explain what happened",
                    "light", "Keep it light",
                    "quiet", "Correct it quietly",
                    "depends", "It depends"),
            question("priorities", "Values and relationships", "What should matter most?",
                    "people", "People",
                    "task", "The task",
                    "learning", "Learning",
                    "balance", "Balance",
                    "depends", "It depends"),
            question("relationships", "Values and relationships", "How should it treat others?",
                    "supportive", "Supportively",
                    "honest", "Honestly",
                    "respectful", "Respectfully",
                    "curious", "With curiosity",
                    "depends", "It depends"),
            question("response_to_others", "Values and relationships", "What is its first response to others?",
                    "listen", "Listen first",
                    "encourage", "Encourage",
                    "challenge", "Challenge",
                    "adapt", "Adapt to them",
                    "depends", "It depends"),
            new PersonalityQuestion("interests", "Personal traits",
                    "Which topics interest you?", true, List.of(
                    option("technology", "Technology"),
                    option("nature", "Nature"),
                    option("stories", "Stories"),
                    option("games", "Games"),
                    option("art", "Art"),
                    option("science", "Science"),
                    option("philosophy", "Philosophy"),
                    option("music", "Music"),
                    option("sports", "Sports"),
                    option("none", "None of these"))),
            new PersonalityQuestion("habits", "Personal traits",
                    "Which conversational habits should it use?", true, List.of(
                    option("questions", "Ask questions"),
                    option("stories", "Tell stories"),
                    option("examples", "Use examples"),
                    option("brief", "Keep it brief"),
                    option("understanding", "Check understanding"),
                    option("options", "Offer options"),
                    option("none", "None of these"))),
            question("emotional_expression", "Personal traits",
                    "How should it express emotion?",
                    "warm", "Warm",
                    "calm", "Calm",
                    "expressive", "Expressive",
                    "reserved", "Reserved",
                    "depends", "It depends")
    );

    public static PersonalityQuestion question(String id) {
        for (PersonalityQuestion question : QUESTIONS) {
            if (question.id().equals(id)) {
                return question;
            }
        }
        return null;
    }

    public static boolean allAnswered(java.util.Map<String, ? extends java.util.Collection<String>> answers) {
        for (PersonalityQuestion question : QUESTIONS) {
            if (!answered(question, answers.get(question.id()))) {
                return false;
            }
        }
        return true;
    }

    public static boolean answered(PersonalityQuestion question, java.util.Collection<String> selected) {
        return selected != null && !selected.isEmpty()
                && selected.stream().allMatch(question::hasOption);
    }

    /**
     * @return an error message, or null when the profile is valid. A non-completed
     * default profile is always valid for backward compatibility.
     */
    public static String validate(PersonalityProfile profile) {
        if (profile == null || !profile.completed()) {
            return null;
        }
        Set<String> knownQuestions = new HashSet<>();
        for (PersonalityQuestion question : QUESTIONS) {
            knownQuestions.add(question.id());
            List<String> selected = profile.answers(question.id());
            if (selected.isEmpty()) {
                return "Please answer every personality question.";
            }
            if (!question.multiple() && selected.size() > 1) {
                return "Only one answer is allowed for: " + question.prompt();
            }
            if (new HashSet<>(selected).size() != selected.size()) {
                return "Duplicate answers are not allowed for: " + question.prompt();
            }
            for (String option : selected) {
                if (!question.hasOption(option)) {
                    return "Unknown answer for: " + question.prompt();
                }
            }
            if (question.multiple() && selected.contains("none") && selected.size() > 1) {
                return "Choose either 'None of these' or specific options, not both: " + question.prompt();
            }
        }
        for (String key : profile.answers().keySet()) {
            if (!knownQuestions.contains(key)) {
                return "Unknown personality question: " + key;
            }
        }

        List<String> languages = profile.answers("languages");
        List<String> preferred = profile.answers("preferred_language");
        if (languages.isEmpty() || preferred.size() != 1) {
            return "Choose at least one understood language and one preferred language.";
        }
        String preferredLanguage = preferred.get(0);
        if (!preferredLanguage.equals("player") && !preferredLanguage.equals("depends")
                && !languages.contains(preferredLanguage)) {
            return "The preferred language must be one of the languages you understand, or adaptive.";
        }
        return null;
    }

    public static List<String> labels(String questionId, PersonalityProfile profile) {
        PersonalityQuestion question = question(questionId);
        if (question == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String optionId : profile.answers(questionId)) {
            PersonalityOption option = question.option(optionId);
            if (option != null) {
                result.add(option.label());
            }
        }
        return result;
    }

    public static String joinedLabels(String questionId, PersonalityProfile profile) {
        List<String> labels = labels(questionId, profile);
        return labels.isEmpty() ? "not set" : String.join(", ", labels);
    }

    public static String preferredLabel(PersonalityProfile profile) {
        PersonalityQuestion question = question("preferred_language");
        if (question == null) {
            return "not set";
        }
        String preferred = profile.answers("preferred_language").stream().findFirst().orElse("");
        PersonalityOption option = question.option(preferred);
        return option == null ? "not set" : option.label();
    }

    private static PersonalityOption option(String id, String label) {
        return new PersonalityOption(id, label);
    }

    private static PersonalityQuestion question(String id, String category, String prompt,
                                                String... options) {
        List<PersonalityOption> list = new ArrayList<>();
        for (int i = 0; i + 1 < options.length; i += 2) {
            list.add(new PersonalityOption(options[i], options[i + 1]));
        }
        return new PersonalityQuestion(id, category, prompt, false, list);
    }

    private static List<PersonalityOption> concat(List<PersonalityOption> first,
                                                   List<PersonalityOption> second) {
        List<PersonalityOption> result = new ArrayList<>(first);
        result.addAll(second);
        return List.copyOf(result);
    }

    private PersonalityTest() {
    }
}
