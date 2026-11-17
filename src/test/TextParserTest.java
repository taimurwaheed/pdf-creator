import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TextParserTest {
    private final TextParser parser = new TextParser();

    @Test
    void parsesNormalTextWithDefaults() {
        List<TextBlock> blocks = parser.parse("Hello, world!");
        assertEquals(1, blocks.size());
        assertEquals(List.of(new TextBlock.Run("Hello, world!",
                TextBlock.FontStyle.REGULAR, TextBlock.FontSize.NORMAL, 0, false)),
                blocks.get(0).runs());
    }

    @Test
    void preservesSpacesPunctuationAndMultipleLines() throws Exception {
        TextBlock block = parser.parse(new StringReader("  Hello  \r\n, world!  \r\n\r\nNext.")).get(0);
        assertEquals("  Hello  \n, world!  \n\nNext.", text(block));
    }

    @Test
    void keepsStyleChangesWithinOneParagraph() {
        List<TextBlock> blocks = parser.parse("First\n.bold\nBold\n.italic\nItalic\n.regular\nRegular");
        assertEquals(1, blocks.size());
        List<TextBlock.Run> runs = blocks.get(0).runs();
        assertEquals(List.of(TextBlock.FontStyle.REGULAR, TextBlock.FontStyle.BOLD,
                TextBlock.FontStyle.ITALIC, TextBlock.FontStyle.REGULAR),
                runs.stream().map(TextBlock.Run::style).toList());
        assertEquals("First\nBold\nItalic\nRegular", text(blocks.get(0)));
    }

    @Test
    void separatesParagraphsAndRetainsFormatting() {
        List<TextBlock> blocks = parser.parse(".paragraph\n.bold\nFirst\n.paragraph\n.paragraph\nSecond\n.paragraph");
        assertEquals(2, blocks.size());
        assertEquals("First", text(blocks.get(0)));
        assertEquals("Second", text(blocks.get(1)));
        assertEquals(TextBlock.FontStyle.BOLD, blocks.get(1).runs().get(0).style());
    }

    @Test
    void recordsRelativeIndentationAndFillChanges() {
        List<TextBlock.Run> runs = parser.parse("Default\n.indent +2\n.nofill\nIndented\n.indent -1\n.fill\nFilled")
                .get(0).runs();
        assertEquals(List.of(0, 2, 1), runs.stream().map(TextBlock.Run::indent).toList());
        assertEquals(List.of(false, false, true), runs.stream().map(TextBlock.Run::fill).toList());
    }

    @Test
    void recordsFontSizeChanges() {
        List<TextBlock.Run> runs = parser.parse("Normal\n.large\nLarge\n.normal\nNormal").get(0).runs();
        assertEquals(List.of(TextBlock.FontSize.NORMAL, TextBlock.FontSize.LARGE,
                TextBlock.FontSize.NORMAL), runs.stream().map(TextBlock.Run::size).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {".unknown", ".bold extra", ".indent", ".indent two",
            ".indent -1", ".indent 2147483648", ".indent 2147483647\n.indent 1"})
    void rejectsInvalidCommandsWithLineNumbers(String command) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> parser.parse("Text\n" + command));
        assertTrue(error.getMessage().startsWith("Line "));
        assertFalse(error.getMessage().substring(error.getMessage().indexOf(':') + 1).isBlank());
    }

    @Test
    void identifiesUnsupportedCommandAndItsLine() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> parser.parse("Text\n.wat"));
        assertEquals("Line 2: Unsupported command: .wat", error.getMessage());
    }

    @Test
    void startsEachParseWithDefaultsAndReturnsImmutableParagraphs() {
        parser.parse(".bold\n.indent 2\n.fill\n.large\nStyled");
        List<TextBlock> blocks = parser.parse("Plain");
        assertEquals(new TextBlock.Run("Plain", TextBlock.FontStyle.REGULAR,
                TextBlock.FontSize.NORMAL, 0, false), blocks.get(0).runs().get(0));
        assertThrows(UnsupportedOperationException.class, () -> blocks.clear());
        assertThrows(UnsupportedOperationException.class, () -> blocks.get(0).runs().clear());
    }

    @Test
    void emptyOrCommandOnlyInputProducesNoParagraphs() {
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse(".bold\n.paragraph\n.fill").isEmpty());
    }

    private String text(TextBlock block) {
        return block.runs().stream().map(TextBlock.Run::text).reduce("", String::concat);
    }
}
