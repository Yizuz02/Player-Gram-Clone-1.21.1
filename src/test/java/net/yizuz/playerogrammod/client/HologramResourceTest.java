package net.yizuz.playerogrammod.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HologramResourceTest {
    @Test
    void veiltShaderProgramAndSourcesArePresent() throws Exception {
        String base = "/assets/playerogrammod/pinwheel/shaders/program/hologram";
        try (InputStream input = getClass().getResourceAsStream(base + ".json")) {
            assertNotNull(input, "hologram.json missing");
            JsonObject json = JsonParser.parseString(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("playerogrammod:hologram", json.get("vertex").getAsString());
            assertEquals("playerogrammod:hologram", json.get("fragment").getAsString());
        }

        String vertex;
        try (InputStream input = getClass().getResourceAsStream(base + ".vsh")) {
            assertNotNull(input, "hologram.vsh missing");
            vertex = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(vertex.contains("ModelViewMat"));
        assertTrue(vertex.contains("ProjMat"));
        assertTrue(vertex.contains("texelFetch(Sampler2"));

        String fragment;
        try (InputStream input = getClass().getResourceAsStream(base + ".fsh")) {
            assertNotNull(input, "hologram.fsh missing");
            fragment = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(fragment.contains("uniform sampler2D Sampler0"));
        assertTrue(fragment.contains("VeilRenderTime"));
        assertTrue(fragment.contains("vertexColor.a"));
        assertTrue(fragment.contains("vec3 green"));
    }
}
