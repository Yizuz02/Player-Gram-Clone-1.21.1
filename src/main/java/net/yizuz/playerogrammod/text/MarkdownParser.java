package net.yizuz.playerogrammod.text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small, Minecraft-independent markdown parser for chat output.
 *
 * <p>Block support: fenced code, headings, blockquotes, unordered/ordered lists,
 * horizontal rules and simple pipe tables. Inline support: bold, italic,
 * bold-italic, strikethrough, inline code and links.</p>
 */
public final class MarkdownParser {
    public enum Color {
        DEFAULT, GRAY, DARK_GRAY, GOLD, YELLOW, AQUA, WHITE
    }

    public record Style(boolean bold, boolean italic, boolean underlined, boolean strikethrough, Color color) {
        public static final Style DEFAULT = new Style(false, false, false, false, Color.DEFAULT);

        public Style withBold(boolean value) {
            return new Style(value, italic, underlined, strikethrough, color);
        }

        public Style withItalic(boolean value) {
            return new Style(bold, value, underlined, strikethrough, color);
        }

        public Style withUnderlined(boolean value) {
            return new Style(bold, italic, value, strikethrough, color);
        }

        public Style withStrikethrough(boolean value) {
            return new Style(bold, italic, underlined, value, color);
        }

        public Style withColor(Color value) {
            return new Style(bold, italic, underlined, strikethrough, value);
        }
    }

    public record Span(String text, Style style) {
    }

    public record Line(List<Span> spans) {
        public static Line empty() {
            return new Line(List.of());
        }
    }

    private static final Style CODE = Style.DEFAULT.withColor(Color.GRAY);
    private static final Style MUTED = Style.DEFAULT.withColor(Color.DARK_GRAY);
    private static final Style QUOTE = Style.DEFAULT.withColor(Color.GRAY).withItalic(true);
    private static final Style HEADING_1 = Style.DEFAULT.withBold(true).withColor(Color.GOLD);
    private static final Style HEADING_2 = Style.DEFAULT.withBold(true).withColor(Color.YELLOW);
    private static final Style HEADING_3 = Style.DEFAULT.withBold(true).withColor(Color.WHITE);
    private static final Style TABLE_HEADER = Style.DEFAULT.withBold(true).withColor(Color.AQUA);

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern UNORDERED = Pattern.compile("^(\\s*)[-*+]\\s+(.*)$");
    private static final Pattern ORDERED = Pattern.compile("^(\\s*)(\\d+[.)])\\s+(.*)$");
    private static final Pattern BLOCKQUOTE = Pattern.compile("^\\s*>\\s?(.*)$");
    private static final Pattern RULE = Pattern.compile("^\\s*([-*_])(?:\\s*\\1){2,}\\s*$");
    private static final Pattern TABLE_SEPARATOR_CELL = Pattern.compile(":?-{3,}:?");
    private static final String BOUNDARIES = ".,;:!?()[]{}\"'<>";

    public static List<Line> parse(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return List.of();
        }
        String text = markdown.replace("\r\n", "\n").replace('\r', '\n').replace('\u00a7', '&');
        String[] raw = text.split("\n", -1);
        List<Line> lines = new ArrayList<>();
        int index = 0;

        while (index < raw.length) {
            String line = raw[index];
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                index++;
                while (index < raw.length && !raw[index].trim().startsWith("```")) {
                    lines.add(new Line(List.of(
                            new Span("│ ", MUTED),
                            new Span(raw[index], CODE))));
                    index++;
                }
                if (index < raw.length) {
                    index++;
                }
                continue;
            }

            if (index + 1 < raw.length && line.contains("|") && isTableSeparator(raw[index + 1])) {
                lines.add(tableLine(splitRow(line), true));
                index += 2;
                while (index < raw.length && raw[index].contains("|") && !raw[index].trim().isEmpty()) {
                    lines.add(tableLine(splitRow(raw[index]), false));
                    index++;
                }
                continue;
            }

            if (trimmed.isEmpty()) {
                lines.add(Line.empty());
                index++;
                continue;
            }

            Matcher heading = HEADING.matcher(trimmed);
            if (heading.matches()) {
                int level = heading.group(1).length();
                Style style = level == 1 ? HEADING_1 : (level == 2 ? HEADING_2 : HEADING_3);
                String content = heading.group(2).replaceAll("\\s+#+\\s*$", "").trim();
                lines.add(new Line(parseInline(content, style)));
                index++;
                continue;
            }

            if (RULE.matcher(trimmed).matches()) {
                lines.add(new Line(List.of(new Span("─".repeat(40), MUTED))));
                index++;
                continue;
            }

            Matcher quote = BLOCKQUOTE.matcher(line);
            if (quote.matches()) {
                List<Span> spans = new ArrayList<>();
                spans.add(new Span("│ ", MUTED));
                spans.addAll(parseInline(quote.group(1), QUOTE));
                lines.add(new Line(spans));
                index++;
                continue;
            }

            Matcher unordered = UNORDERED.matcher(line);
            if (unordered.matches()) {
                List<Span> spans = new ArrayList<>();
                if (!unordered.group(1).isEmpty()) {
                    spans.add(new Span(unordered.group(1), MUTED));
                }
                spans.add(new Span("• ", MUTED));
                spans.addAll(parseInline(unordered.group(2), Style.DEFAULT));
                lines.add(new Line(spans));
                index++;
                continue;
            }

            Matcher ordered = ORDERED.matcher(line);
            if (ordered.matches()) {
                List<Span> spans = new ArrayList<>();
                if (!ordered.group(1).isEmpty()) {
                    spans.add(new Span(ordered.group(1), MUTED));
                }
                spans.add(new Span(ordered.group(2) + " ", MUTED));
                spans.addAll(parseInline(ordered.group(3), Style.DEFAULT));
                lines.add(new Line(spans));
                index++;
                continue;
            }

            lines.add(new Line(parseInline(line, Style.DEFAULT)));
            index++;
        }

        while (!lines.isEmpty() && lines.get(lines.size() - 1).spans().isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private static Line tableLine(List<String> cells, boolean header) {
        List<Span> spans = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                spans.add(new Span(" │ ", MUTED));
            }
            spans.addAll(parseInline(cells.get(i), header ? TABLE_HEADER : Style.DEFAULT));
        }
        return new Line(spans);
    }

    private static boolean isTableSeparator(String line) {
        if (!line.contains("|")) {
            return false;
        }
        List<String> cells = splitRow(line);
        if (cells.isEmpty()) {
            return false;
        }
        for (String cell : cells) {
            if (!TABLE_SEPARATOR_CELL.matcher(cell).matches()) {
                return false;
            }
        }
        return true;
    }

    private static List<String> splitRow(String line) {
        String value = line.trim();
        if (value.startsWith("|")) {
            value = value.substring(1);
        }
        if (value.endsWith("|")) {
            value = value.substring(0, value.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        for (String cell : value.split("\\|", -1)) {
            cells.add(cell.trim());
        }
        return cells;
    }

    private static List<Span> parseInline(String text, Style base) {
        List<Span> spans = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int index = 0;

        while (index < text.length()) {
            char current = text.charAt(index);

            if (current == '\\' && index + 1 < text.length() && isEscapable(text.charAt(index + 1))) {
                literal.append(text.charAt(index + 1));
                index += 2;
                continue;
            }

            if (current == '`') {
                int end = findUnescaped(text, "`", index + 1);
                if (end > index) {
                    flush(spans, literal, base);
                    spans.add(new Span(text.substring(index + 1, end), base.withColor(Color.GRAY)));
                    index = end + 1;
                    continue;
                }
            }

            if (current == '[') {
                int labelEnd = text.indexOf("](", index + 1);
                if (labelEnd > index) {
                    int urlEnd = findUnescaped(text, ")", labelEnd + 2);
                    if (urlEnd > labelEnd) {
                        flush(spans, literal, base);
                        String label = text.substring(index + 1, labelEnd);
                        String url = text.substring(labelEnd + 2, urlEnd);
                        spans.addAll(parseInline(label, base.withUnderlined(true).withColor(Color.AQUA)));
                        if (!url.isBlank() && !url.equals(label)) {
                            spans.add(new Span(" (" + url + ")", base.withColor(Color.DARK_GRAY)));
                        }
                        index = urlEnd + 1;
                        continue;
                    }
                }
            }

            String marker = markerAt(text, index);
            if (marker != null) {
                int contentStart = index + marker.length();
                int end = findClosingMarker(text, contentStart, marker);
                if (end > contentStart) {
                    flush(spans, literal, base);
                    spans.addAll(parseInline(text.substring(contentStart, end), applyMarker(base, marker)));
                    index = end + marker.length();
                    continue;
                }
            }

            literal.append(current);
            index++;
        }

        flush(spans, literal, base);
        return spans;
    }

    private static String markerAt(String text, int index) {
        String marker = null;
        if (text.startsWith("***", index)) {
            marker = "***";
        } else if (text.startsWith("___", index)) {
            marker = "___";
        } else if (text.startsWith("**", index)) {
            marker = "**";
        } else if (text.startsWith("__", index)) {
            marker = "__";
        } else if (text.startsWith("~~", index)) {
            marker = "~~";
        } else if (text.charAt(index) == '*') {
            marker = "*";
        } else if (text.charAt(index) == '_') {
            marker = "_";
        }
        if (marker == null || !isValidOpening(text, index, marker)) {
            return null;
        }
        return marker;
    }

    private static boolean isValidOpening(String text, int index, String marker) {
        int next = index + marker.length();
        if (next >= text.length() || Character.isWhitespace(text.charAt(next))) {
            return false;
        }
        if (marker.charAt(0) == '_' && index > 0) {
            char previous = text.charAt(index - 1);
            return Character.isWhitespace(previous) || isBoundary(previous);
        }
        return true;
    }

    private static int findClosingMarker(String text, int from, String marker) {
        int index = from;
        while (true) {
            index = text.indexOf(marker, index);
            if (index < 0) {
                return -1;
            }
            if (isEscaped(text, index)) {
                index += marker.length();
                continue;
            }
            int after = index + marker.length();
            char before = index > 0 ? text.charAt(index - 1) : ' ';
            char next = after < text.length() ? text.charAt(after) : ' ';
            if (!Character.isWhitespace(before) && isBoundary(next)) {
                return index;
            }
            index += marker.length();
        }
    }

    private static int findUnescaped(String text, String needle, int from) {
        int index = from;
        while (true) {
            index = text.indexOf(needle, index);
            if (index < 0 || !isEscaped(text, index)) {
                return index;
            }
            index += needle.length();
        }
    }

    private static boolean isEscaped(String text, int index) {
        int backslashes = 0;
        for (int i = index - 1; i >= 0 && text.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    private static boolean isBoundary(char value) {
        return Character.isWhitespace(value) || BOUNDARIES.indexOf(value) >= 0;
    }

    private static boolean isEscapable(char value) {
        return "\\`*_{}[]()#+-.!~|>".indexOf(value) >= 0;
    }

    private static Style applyMarker(Style base, String marker) {
        return switch (marker) {
            case "***", "___" -> base.withBold(true).withItalic(true);
            case "**", "__" -> base.withBold(true);
            case "*", "_" -> base.withItalic(true);
            case "~~" -> base.withStrikethrough(true);
            default -> base;
        };
    }

    private static void flush(List<Span> spans, StringBuilder literal, Style style) {
        if (literal.length() > 0) {
            spans.add(new Span(literal.toString(), style));
            literal.setLength(0);
        }
    }

    private MarkdownParser() {
    }
}
