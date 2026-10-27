package net.yizuz.playerogrammod.personality;

import java.util.List;

public record PersonalityQuestion(
        String id,
        String category,
        String prompt,
        boolean multiple,
        List<PersonalityOption> options
) {
    public PersonalityQuestion {
        options = List.copyOf(options);
    }

    public PersonalityOption option(String optionId) {
        for (PersonalityOption option : this.options) {
            if (option.id().equals(optionId)) {
                return option;
            }
        }
        return null;
    }

    public boolean hasOption(String optionId) {
        return option(optionId) != null;
    }
}
