package net.yizuz.playerogrammod.sound;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundResourceTest {
    @Test
    void soundDefinitionAndOggArePresent() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/assets/playerogrammod/sounds.json")) {
            assertNotNull(input, "sounds.json missing");
            JsonObject json = JsonParser.parseString(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(json.has("ring_sound"), "ring_sound entry missing");
            JsonObject ring = json.getAsJsonObject("ring_sound");
            assertTrue(ring.has("sounds"));
            assertEquals("playerogrammod:ring_sound",
                    ring.getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString());
        }

        try (InputStream input = getClass().getResourceAsStream(
                "/assets/playerogrammod/sounds/ring_sound.ogg")) {
            assertNotNull(input, "ring_sound.ogg missing");
            byte[] header = input.readNBytes(4);
            assertEquals("OggS", new String(header, StandardCharsets.US_ASCII));
        }
    }
}
