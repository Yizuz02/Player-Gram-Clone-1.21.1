package net.yizuz.playerogrammod.ai;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class LibraryResolver {
    private static final Pattern GLIBCXX = Pattern.compile("GLIBCXX_(\\d+)\\.(\\d+)\\.(\\d+)");

    static String requiredVersion(String output) {
        if (output == null || output.isBlank()) {
            return null;
        }
        Matcher matcher = GLIBCXX.matcher(output);
        String best = null;
        while (matcher.find()) {
            String version = matcher.group(1) + "." + matcher.group(2) + "." + matcher.group(3);
            if (best == null || compare(parse(version), parse(best)) > 0) {
                best = version;
            }
        }
        return best;
    }

    static Path findCompatible(String requiredVersion, String configuredPath) {
        int[] required = parse(requiredVersion);
        if (required == null) {
            return null;
        }

        if (configuredPath != null && !configuredPath.isBlank()) {
            try {
                Path configured = Path.of(configuredPath.trim());
                if (Files.isRegularFile(configured)) {
                    if (supports(configured, requiredVersion)) {
                        return configured.toAbsolutePath().normalize().getParent();
                    }
                }
            } catch (RuntimeException ignored) {
                // Fall through to the automatic search.
            }
        }

        Set<Path> directories = new LinkedHashSet<>();
        addConfiguredDirectory(directories, configuredPath);
        addEnvironmentDirectories(directories);
        addIfDirectory(directories, Path.of("/usr/local/lib64"));
        addIfDirectory(directories, Path.of("/usr/local/lib"));
        addChildren(directories, Path.of("/opt/rh"), child ->
                child.getFileName().toString().startsWith("gcc-toolset-")
                        ? child.resolve("root/usr/lib64") : null);
        addChildren(directories, Path.of("/opt"), child -> {
            addIfDirectory(directories, child.resolve("lib64"));
            addIfDirectory(directories, child.resolve("lib"));
            return null;
        });
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            addChildren(directories, Path.of(home).resolve(".local/lib"), child -> child);
        }
        addIfDirectory(directories, Path.of("/usr/lib64"));
        addIfDirectory(directories, Path.of("/usr/lib"));

        for (Path directory : directories) {
            Path library = directory.resolve("libstdc++.so.6");
            if (supports(library, requiredVersion)) {
                return directory.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    static boolean supports(Path library, String requiredVersion) {
        int[] required = parse(requiredVersion);
        if (required == null || library == null || !Files.isRegularFile(library)) {
            return false;
        }
        try {
            String text = new String(Files.readAllBytes(library), StandardCharsets.ISO_8859_1);
            Matcher matcher = GLIBCXX.matcher(text);
            while (matcher.find()) {
                int[] version = {
                        Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3))
                };
                if (compare(version, required) >= 0) {
                    return true;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Unreadable or malformed library; try the next candidate.
        }
        return false;
    }

    private static void addConfiguredDirectory(Set<Path> directories, String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return;
        }
        try {
            Path configured = Path.of(configuredPath.trim());
            Path directory = Files.isDirectory(configured) ? configured : configured.toAbsolutePath().getParent();
            addIfDirectory(directories, directory);
        } catch (RuntimeException ignored) {
            // Ignore invalid paths and continue with auto-detection.
        }
    }

    private static void addEnvironmentDirectories(Set<Path> directories) {
        String value = System.getenv("LD_LIBRARY_PATH");
        if (value == null || value.isBlank()) {
            return;
        }
        for (String entry : value.split(Pattern.quote(File.pathSeparator))) {
            if (!entry.isBlank()) {
                addIfDirectory(directories, Path.of(entry));
            }
        }
    }

    private static void addChildren(Set<Path> directories, Path parent, ChildMapper mapper) {
        if (!Files.isDirectory(parent)) {
            return;
        }
        try (Stream<Path> children = Files.list(parent)) {
            children.filter(Files::isDirectory).forEach(child -> {
                Path mapped = mapper.map(child);
                if (mapped != null) {
                    addIfDirectory(directories, mapped);
                }
            });
        } catch (IOException ignored) {
            // Best effort.
        }
    }

    private static void addIfDirectory(Set<Path> directories, Path path) {
        if (path != null && Files.isDirectory(path)) {
            directories.add(path.toAbsolutePath().normalize());
        }
    }

    private static int[] parse(String version) {
        if (version == null) {
            return null;
        }
        String[] parts = version.trim().split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new int[] {
                    Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int compare(int[] left, int[] right) {
        for (int i = 0; i < 3; i++) {
            int result = Integer.compare(left[i], right[i]);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    @FunctionalInterface
    private interface ChildMapper {
        Path map(Path child);
    }

    private LibraryResolver() {
    }
}
