package net.yizuz.playerogrammod.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ModelSelection {
    private static final Pattern SHARD = Pattern.compile("^(.*)-(\\d{5})-of-(\\d{5})\\.gguf$", Pattern.CASE_INSENSITIVE);

    record Selection(Path path, boolean isDefault) {
    }

    static Selection select(List<Path> files, String defaultFileName) {
        List<Path> candidates = collapseShards(files);
        List<Path> custom = new ArrayList<>();
        for (Path file : candidates) {
            if (!ModelManifest.isDefaultFamily(file.getFileName().toString())) {
                custom.add(file);
            }
        }

        if (custom.size() > 1) {
            throw new AiException("Multiple GGUF models found in the models folder. Set modelPath in the config to choose one: "
                    + names(custom) + ".");
        }
        if (custom.size() == 1) {
            return new Selection(custom.get(0), false);
        }

        for (Path file : candidates) {
            if (file.getFileName().toString().equalsIgnoreCase(defaultFileName)) {
                return new Selection(file, true);
            }
        }
        if (!candidates.isEmpty()) {
            throw new AiException("Found default-family GGUF file(s) that do not match defaultModelQuant. "
                    + "Set defaultModelQuant or modelPath, or remove them: " + names(candidates) + ".");
        }
        return null;
    }

    static List<Path> collapseShards(List<Path> files) {
        Map<String, Path> firstShard = new TreeMap<>();
        List<Path> regular = new ArrayList<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            Matcher matcher = SHARD.matcher(name);
            if (matcher.matches()) {
                String base = matcher.group(1);
                Path previous = firstShard.get(base);
                if (previous == null || shardIndex(name) < shardIndex(previous.getFileName().toString())) {
                    firstShard.put(base, file);
                }
            } else {
                regular.add(file);
            }
        }
        List<Path> result = new ArrayList<>(regular);
        result.addAll(firstShard.values());
        result.sort((left, right) -> left.getFileName().toString()
                .compareToIgnoreCase(right.getFileName().toString()));
        return result;
    }

    static void validateGguf(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new AiException("GGUF model not found: " + path);
        }
        try {
            long size = Files.size(path);
            if (size < 1_048_576L) {
                throw new AiException("GGUF model is too small to be valid (" + size + " bytes): " + path);
            }
            byte[] magic = new byte[4];
            try (InputStream in = Files.newInputStream(path)) {
                int read = in.readNBytes(magic, 0, magic.length);
                if (read != magic.length || !"GGUF".equals(new String(magic, StandardCharsets.US_ASCII))) {
                    throw new AiException("File is not a GGUF model: " + path);
                }
            }
        } catch (IOException e) {
            throw new AiException("Could not read GGUF model " + path + ": " + e.getMessage(), e);
        }
    }

    private static int shardIndex(String name) {
        Matcher matcher = SHARD.matcher(name);
        if (!matcher.matches()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static String names(List<Path> files) {
        List<String> names = new ArrayList<>();
        for (Path file : files) {
            names.add(file.getFileName().toString());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join(", ", names);
    }

    private ModelSelection() {
    }
}
