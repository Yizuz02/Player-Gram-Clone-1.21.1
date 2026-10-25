package net.yizuz.playerogrammod.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiPlatformTest {
    @Test
    void mapsWindows() {
        assertEquals("windows-x86_64", AiPlatform.detect("Windows 11", "amd64").id());
        assertEquals("windows-aarch64", AiPlatform.detect("Windows Server 2022", "aarch64").id());
        assertEquals("llama-server.exe", AiPlatform.detect("Windows 11", "amd64").binaryName());
    }

    @Test
    void mapsLinux() {
        assertEquals("linux-x86_64", AiPlatform.detect("Linux", "amd64").id());
        assertEquals("linux-aarch64", AiPlatform.detect("Linux", "arm64").id());
        assertEquals("llama-server", AiPlatform.detect("Linux", "x86_64").binaryName());
    }

    @Test
    void mapsMac() {
        assertEquals("macos-aarch64", AiPlatform.detect("Mac OS X", "aarch64").id());
        assertEquals("macos-x86_64", AiPlatform.detect("Darwin", "x86_64").id());
    }

    @Test
    void rejectsUnsupported() {
        assertThrows(AiException.class, () -> AiPlatform.detect("Plan 9", "amd64"));
        assertThrows(AiException.class, () -> AiPlatform.detect("Linux", "x86"));
        assertThrows(AiException.class, () -> AiPlatform.detect("Linux", "s390x"));
    }
}
