package net.yizuz.playerogrammod.client.flight;

import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.data.gson.AnimationJson;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingFlightAnimationJsonTest {
    @Test
    void flightAnimationsLoadWithExpectedNames() throws Exception {
        for (String name : List.of("flight_idle", "flight_boost", "flight_backward")) {
            var stream = getClass().getResourceAsStream(
                    "/assets/playerogrammod/player_animations/" + name + ".json");
            assertNotNull(stream, name + " resource missing");

            List<KeyframeAnimation> animations;
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                animations = AnimationJson.GSON.fromJson(reader, AnimationJson.getListedTypeToken());
            }

            assertNotNull(animations, name + " did not parse");
            assertEquals(1, animations.size(), name + " should contain one animation");

            KeyframeAnimation animation = animations.get(0);
            assertEquals(name, animation.getName().replace("\"", ""));
            assertTrue(animation.isInfinite(), name + " should loop");
            assertFalse(animation.getBodyParts().isEmpty(), name + " should pose body parts");
        }
    }
}
