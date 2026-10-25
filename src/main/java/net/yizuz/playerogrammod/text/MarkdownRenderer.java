package net.yizuz.playerogrammod.text;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;

public final class MarkdownRenderer {
    public static List<Component> render(String markdown) {
        List<Component> result = new ArrayList<>();
        for (MarkdownParser.Line line : MarkdownParser.parse(markdown)) {
            MutableComponent component = Component.empty();
            for (MarkdownParser.Span span : line.spans()) {
                component.append(Component.literal(span.text()).withStyle(toStyle(span.style())));
            }
            if (line.spans().isEmpty()) {
                component.append(Component.literal(" "));
            }
            result.add(component);
        }
        if (result.isEmpty()) {
            result.add(Component.literal(" "));
        }
        return result;
    }

    private static Style toStyle(MarkdownParser.Style source) {
        Style style = Style.EMPTY;
        if (source.bold()) {
            style = style.withBold(true);
        }
        if (source.italic()) {
            style = style.withItalic(true);
        }
        if (source.underlined()) {
            style = style.withUnderlined(true);
        }
        if (source.strikethrough()) {
            style = style.withStrikethrough(true);
        }
        ChatFormatting color = switch (source.color()) {
            case GRAY -> ChatFormatting.GRAY;
            case DARK_GRAY -> ChatFormatting.DARK_GRAY;
            case GOLD -> ChatFormatting.GOLD;
            case YELLOW -> ChatFormatting.YELLOW;
            case AQUA -> ChatFormatting.AQUA;
            case WHITE -> ChatFormatting.WHITE;
            case DEFAULT -> null;
        };
        if (color != null) {
            style = style.withColor(color);
        }
        return style;
    }

    private MarkdownRenderer() {
    }
}
