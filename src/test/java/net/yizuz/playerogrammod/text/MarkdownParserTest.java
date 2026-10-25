package net.yizuz.playerogrammod.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownParserTest {
    @Test
    void parsesInlineStyles() {
        List<MarkdownParser.Line> lines = MarkdownParser.parse(
                "**bold** *italic* ***both*** ~~gone~~ `code`");
        assertEquals(1, lines.size());

        assertTrue(span(lines, "bold").style().bold());
        assertTrue(span(lines, "italic").style().italic());
        assertTrue(span(lines, "both").style().bold());
        assertTrue(span(lines, "both").style().italic());
        assertTrue(span(lines, "gone").style().strikethrough());
        assertEquals(MarkdownParser.Color.GRAY, span(lines, "code").style().color());
    }

    @Test
    void parsesHeadings() {
        assertTrue(span(MarkdownParser.parse("# Title"), "Title").style().bold());
        assertEquals(MarkdownParser.Color.GOLD, span(MarkdownParser.parse("# Title"), "Title").style().color());
        assertEquals(MarkdownParser.Color.YELLOW,
                span(MarkdownParser.parse("## Subtitle"), "Subtitle").style().color());
    }

    @Test
    void parsesListsAndIndentation() {
        List<MarkdownParser.Line> unordered = MarkdownParser.parse("- item");
        assertEquals("• ", unordered.get(0).spans().get(0).text());
        assertEquals(MarkdownParser.Color.DARK_GRAY, unordered.get(0).spans().get(0).style().color());
        assertEquals("item", unordered.get(0).spans().get(1).text());

        List<MarkdownParser.Line> nested = MarkdownParser.parse("  - nested");
        assertEquals("  ", nested.get(0).spans().get(0).text());
        assertEquals("• ", nested.get(0).spans().get(1).text());

        List<MarkdownParser.Line> ordered = MarkdownParser.parse("1. first");
        assertEquals("1. ", ordered.get(0).spans().get(0).text());
        assertEquals("first", ordered.get(0).spans().get(1).text());
    }

    @Test
    void parsesLinks() {
        List<MarkdownParser.Line> lines = MarkdownParser.parse("[docs](https://example.com)");
        MarkdownParser.Span label = span(lines, "docs");
        assertTrue(label.style().underlined());
        assertEquals(MarkdownParser.Color.AQUA, label.style().color());
        assertNotNull(findContains(lines, "https://example.com"));
    }

    @Test
    void doesNotParseMarkdownInsideCodeBlocks() {
        List<MarkdownParser.Line> lines = MarkdownParser.parse("```\nhello **world**\n```");
        assertEquals(1, lines.size());
        MarkdownParser.Span code = lines.get(0).spans().get(1);
        assertEquals("hello **world**", code.text());
        assertEquals(MarkdownParser.Color.GRAY, code.style().color());
        assertFalse(code.style().bold());
    }

    @Test
    void parsesTables() {
        List<MarkdownParser.Line> lines = MarkdownParser.parse("| A | B |\n|---|---|\n| 1 | 2 |");
        assertEquals(2, lines.size());
        assertTrue(span(lines, "A").style().bold());
        assertEquals(MarkdownParser.Color.AQUA, span(lines, "A").style().color());
        assertEquals("1", span(lines, "1").text());
        assertFalse(span(lines, "1").style().bold());
    }

    @Test
    void ignoresUnderscoresInsideWords() {
        List<MarkdownParser.Line> lines = MarkdownParser.parse("snake_case");
        assertEquals(1, lines.size());
        MarkdownParser.Span span = lines.get(0).spans().get(0);
        assertEquals("snake_case", span.text());
        assertFalse(span.style().italic());
    }

    @Test
    void leavesUnclosedMarkersLiteral() {
        List<MarkdownParser.Line> lines = MarkdownParser.parse("**bold");
        assertFalse(span(lines, "**bold").style().bold());
    }

    @Test
    void sanitizesSectionSigns() {
        List<MarkdownParser.Line> lines = MarkdownParser.parse("§c red");
        assertEquals("&c red", lines.get(0).spans().get(0).text());
    }

    @Test
    void parsesRulesAndQuotes() {
        List<MarkdownParser.Line> rule = MarkdownParser.parse("---");
        assertEquals("─".repeat(40), rule.get(0).spans().get(0).text());

        List<MarkdownParser.Line> quote = MarkdownParser.parse("> quoted");
        assertEquals("│ ", quote.get(0).spans().get(0).text());
        assertTrue(span(quote, "quoted").style().italic());
        assertEquals(MarkdownParser.Color.GRAY, span(quote, "quoted").style().color());
    }


    @Test
    void parsesRealisticModelOutput() {
        List<MarkdownParser.Line> lines = MarkdownParser.parse("""
                **1. Generación de Texto Creativo:**
                *   **Redacción:** Escribir correos y artículos.
                | Tarea | Ejemplo |
                |---|---|
                | Redactar | Hola |
                """);

        assertTrue(span(lines, "1. Generación de Texto Creativo:").style().bold());
        assertEquals("• ", lines.get(1).spans().get(0).text());
        assertTrue(span(lines, "Redacción:").style().bold());
        assertTrue(span(lines, "Tarea").style().bold());
        assertEquals(MarkdownParser.Color.AQUA, span(lines, "Tarea").style().color());
        assertEquals("Redactar", span(lines, "Redactar").text());
    }

    private static MarkdownParser.Span span(List<MarkdownParser.Line> lines, String text) {
        for (MarkdownParser.Line line : lines) {
            for (MarkdownParser.Span span : line.spans()) {
                if (span.text().equals(text)) {
                    return span;
                }
            }
        }
        throw new AssertionError("Missing span: " + text);
    }

    private static MarkdownParser.Span findContains(List<MarkdownParser.Line> lines, String text) {
        for (MarkdownParser.Line line : lines) {
            for (MarkdownParser.Span span : line.spans()) {
                if (span.text().contains(text)) {
                    return span;
                }
            }
        }
        return null;
    }
}
