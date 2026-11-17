import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TextFormatterTest {
    private static final double EPSILON = 0.000001;

    @Test
    void measuresUsingPdfBoxMetrics() throws Exception {
        TextFormatter formatter = new TextFormatter(200);
        double expected = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
                .getStringWidth("Hello!") * 16.0 / 1000.0;
        assertEquals(expected, formatter.measure("Hello!", TextBlock.FontStyle.BOLD,
                TextBlock.FontSize.LARGE), EPSILON);
    }

    @Test
    void wrapsByMeasuredWidthRatherThanCharacterCount() throws Exception {
        TextFormatter formatter = new TextFormatter(70);
        List<TextFormatter.Line> narrow = formatter.format(parse("iiii iiii iiii"));
        List<TextFormatter.Line> wide = formatter.format(parse("WWWW WWWW WWWW"));
        assertEquals(1, narrow.size());
        assertEquals(3, wide.size());
        for (TextFormatter.Line line : wide) {
            assertTrue(line.width() <= 70 + EPSILON);
        }
    }

    @Test
    void acceptsAWordThatExactlyFits() throws Exception {
        TextFormatter metrics = new TextFormatter(200);
        double width = metrics.measure("Hello", TextBlock.FontStyle.REGULAR, TextBlock.FontSize.NORMAL);
        List<TextFormatter.Line> lines = new TextFormatter(width).format(parse("Hello"));
        assertEquals(1, lines.size());
        assertEquals("Hello", text(lines.get(0)));
        assertEquals(width, lines.get(0).width(), EPSILON);
    }

    @Test
    void justifiesWrappedFillLinesButNotParagraphLastLine() throws Exception {
        List<TextFormatter.Line> lines = new TextFormatter(85).format(parse(".fill\none two three four five six"));
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).justified());
        assertEquals(85, lines.get(0).width(), EPSILON);
        assertEquals("one two three", text(lines.get(0)));
        assertFalse(lines.get(0).lastInParagraph());
        assertFalse(lines.get(1).justified());
        assertTrue(lines.get(1).lastInParagraph());
        assertTrue(lines.get(1).width() < 85);
        assertEquals(85, rightEdge(lines.get(0)), EPSILON);
    }

    @Test
    void distributesExtraWidthEquallyAcrossWordGaps() throws Exception {
        TextFormatter formatter = new TextFormatter(85);
        TextFormatter.Line line = formatter.format(parse(".fill\none two three four")).get(0);
        List<TextFormatter.Fragment> gaps = line.fragments().stream()
                .filter(fragment -> fragment.text().equals(" ")).toList();
        assertEquals(2, gaps.size());
        assertEquals(gaps.get(0).width(), gaps.get(1).width(), EPSILON);
        assertTrue(gaps.get(0).width() > formatter.measure(" ", TextBlock.FontStyle.REGULAR,
                TextBlock.FontSize.NORMAL));
        assertContiguous(line);
    }

    @Test
    void leavesNofillLinesLeftAlignedWhileStillWrapping() throws Exception {
        TextFormatter formatter = new TextFormatter(85);
        List<TextFormatter.Line> lines = formatter.format(parse(".nofill\none two three four five six"));
        assertEquals(2, lines.size());
        for (TextFormatter.Line line : lines) {
            assertFalse(line.justified());
            assertEquals(0, line.fragments().get(0).x(), EPSILON);
            assertEquals(formatter.measure(text(line), TextBlock.FontStyle.REGULAR,
                    TextBlock.FontSize.NORMAL), line.width(), EPSILON);
        }
    }

    @Test
    void appliesIndentationToEveryLineAndReducesWrappingWidth() throws Exception {
        TextFormatter formatter = new TextFormatter(100, 10, 12, 16);
        List<TextFormatter.Line> lines = formatter.format(parse(".indent +2\n.fill\none two three four five six"));
        assertTrue(lines.size() > 1);
        for (TextFormatter.Line line : lines) {
            assertEquals(20, line.indent(), EPSILON);
            assertEquals(20, line.fragments().get(0).x(), EPSILON);
            assertTrue(rightEdge(line) <= 100 + EPSILON);
            assertContiguous(line);
        }
        assertEquals(100, rightEdge(lines.get(0)), EPSILON);
    }

    @Test
    void preservesStylesWithinOneLine() throws Exception {
        TextFormatter.Line line = new TextFormatter(300)
                .format(parse("Plain\n.bold\nBold\n.italic\nItalic\n.regular\nRegular")).get(0);
        List<TextFormatter.Fragment> words = line.fragments().stream()
                .filter(fragment -> !fragment.text().isBlank()).toList();
        assertEquals("Plain Bold Italic Regular", text(line));
        assertEquals(List.of(TextBlock.FontStyle.REGULAR, TextBlock.FontStyle.BOLD,
                TextBlock.FontStyle.ITALIC, TextBlock.FontStyle.REGULAR),
                words.stream().map(TextFormatter.Fragment::style).toList());
        TextFormatter formatter = new TextFormatter(300);
        for (TextFormatter.Fragment word : words) {
            assertEquals(formatter.measure(word.text(), word.style(), word.size()), word.width(), EPSILON);
        }
        assertContiguous(line);
    }

    @Test
    void preservesStyleChangesInsideAnUnbrokenWord() throws Exception {
        TextBlock paragraph = new TextBlock(List.of(run("inter", TextBlock.FontStyle.REGULAR),
                run("view", TextBlock.FontStyle.BOLD)));
        List<TextFormatter.Line> lines = new TextFormatter(100).format(List.of(paragraph));
        assertEquals(1, lines.size());
        assertEquals("interview", text(lines.get(0)));
        assertEquals(2, lines.get(0).fragments().size());
    }

    @Test
    void supportsMixedSizesAndUsesLargeSizeForWrapping() throws Exception {
        TextFormatter formatter = new TextFormatter(70);
        List<TextFormatter.Line> normal = formatter.format(parse("word word"));
        List<TextFormatter.Line> large = formatter.format(parse(".large\nword word"));
        assertEquals(1, normal.size());
        assertEquals(2, large.size());
        TextFormatter.Line mixed = new TextFormatter(300).format(parse("small\n.large\nBIG\n.normal\nsmall")).get(0);
        assertEquals(List.of(TextBlock.FontSize.NORMAL, TextBlock.FontSize.LARGE, TextBlock.FontSize.NORMAL),
                mixed.fragments().stream().filter(fragment -> !fragment.text().isBlank())
                        .map(TextFormatter.Fragment::size).toList());
    }

    @Test
    void attachesCommaStartingNewRunWithoutLosingItsStyle() throws Exception {
        TextFormatter.Line line = new TextFormatter(200).format(parse("Hello\n.bold\n, world!")).get(0);
        assertEquals("Hello, world!", text(line));
        TextFormatter.Fragment comma = line.fragments().get(1);
        assertEquals(",", comma.text());
        assertEquals(TextBlock.FontStyle.BOLD, comma.style());
        assertContiguous(line);
    }

    @Test
    void keepsCommaWithPrecedingWordWhenWrapping() throws Exception {
        List<TextFormatter.Line> lines = new TextFormatter(40).format(parse("Hello\n.italic\n, world!"));
        assertEquals(2, lines.size());
        assertEquals("Hello,", text(lines.get(0)));
        assertEquals("world!", text(lines.get(1)));
    }

    @Test
    void treatsInputLineBreaksAsSpacesAndKeepsParagraphsSeparate() throws Exception {
        List<TextFormatter.Line> lines = new TextFormatter(300)
                .format(parse("one\ntwo\n.paragraph\nthree\nfour"));
        assertEquals(2, lines.size());
        assertEquals("one two", text(lines.get(0)));
        assertEquals("three four", text(lines.get(1)));
        assertEquals(List.of(0, 1), lines.stream().map(TextFormatter.Line::paragraphIndex).toList());
        assertTrue(lines.stream().allMatch(line -> line.lastInParagraph() && !line.justified()));
    }

    @Test
    void handlesIndentAndFillChangesWithinAParagraph() throws Exception {
        List<TextFormatter.Line> lines = new TextFormatter(120, 10, 12, 16)
                .format(parse("first\n.indent 2\n.nofill\nsecond third fourth fifth\n.fill\nlast"));
        assertEquals("first", text(lines.get(0)));
        assertEquals(0, lines.get(0).indent(), EPSILON);
        assertTrue(lines.size() >= 4);
        for (int i = 1; i < lines.size(); i++) {
            assertEquals(20, lines.get(i).indent(), EPSILON);
            assertFalse(lines.get(i).justified());
        }
        assertEquals("last", text(lines.get(lines.size() - 1)));
    }

    @Test
    void splitsLongWordsWithoutDroppingCharactersOrOverflowing() throws Exception {
        String word = "Supercalifragilisticexpialidocious";
        List<TextFormatter.Line> lines = new TextFormatter(35).format(parse(word));
        assertTrue(lines.size() > 1);
        assertEquals(word, lines.stream().map(this::text).reduce("", String::concat));
        for (TextFormatter.Line line : lines) {
            assertFalse(line.justified());
            assertFalse(line.fragments().isEmpty());
            assertTrue(line.width() <= 35 + EPSILON);
        }
    }

    @Test
    void keepsClosingPunctuationAttachedWhenSplittingLongWords() throws Exception {
        TextFormatter metrics = new TextFormatter(200);
        double width = metrics.measure("Hello", TextBlock.FontStyle.REGULAR, TextBlock.FontSize.NORMAL);
        List<TextFormatter.Line> lines = new TextFormatter(width).format(parse("Hello\n.bold\n,"));
        assertEquals("Hello,", lines.stream().map(this::text).reduce("", String::concat));
        assertEquals(2, lines.size());
        assertFalse(text(lines.get(1)).startsWith(","));
        assertTrue(lines.stream().allMatch(line -> line.width() <= width + EPSILON));
    }

    @Test
    void wrapsMixedStylesWithoutLosingFormattingOrExceedingTheWidth() throws Exception {
        List<TextFormatter.Line> lines = new TextFormatter(60)
                .format(parse("one\n.bold\ntwo three\n.italic\nfour five"));
        assertTrue(lines.size() > 1);
        assertEquals("onetwothreefourfive", lines.stream().map(this::text)
                .reduce("", String::concat).replace(" ", ""));
        TextFormatter metrics = new TextFormatter(60);
        for (TextFormatter.Line line : lines) {
            assertTrue(rightEdge(line) <= 60 + EPSILON);
            for (TextFormatter.Fragment fragment : line.fragments()) {
                if (!fragment.text().isBlank()) {
                    assertEquals(metrics.measure(fragment.text(), fragment.style(), fragment.size()),
                            fragment.width(), EPSILON);
                    TextBlock.FontStyle expected = switch (fragment.text()) {
                        case "one" -> TextBlock.FontStyle.REGULAR;
                        case "two", "three" -> TextBlock.FontStyle.BOLD;
                        default -> TextBlock.FontStyle.ITALIC;
                    };
                    assertEquals(expected, fragment.style());
                }
            }
            assertContiguous(line);
        }
    }

    @Test
    void handlesEmptyAndWhitespaceOnlyParagraphs() throws Exception {
        TextFormatter formatter = new TextFormatter(100);
        assertTrue(formatter.format(List.of()).isEmpty());
        List<TextFormatter.Line> lines = formatter.format(List.of(new TextBlock(List.of()),
                new TextBlock(List.of(run(" \n  ", TextBlock.FontStyle.REGULAR)))));
        assertEquals(2, lines.size());
        for (TextFormatter.Line line : lines) {
            assertTrue(line.fragments().isEmpty());
            assertEquals(0, line.width(), EPSILON);
            assertTrue(line.lastInParagraph());
            assertFalse(line.justified());
        }
    }

    @Test
    void preservesInternalSpacesButTrimsLineEdges() throws Exception {
        TextFormatter.Line line = new TextFormatter(200).format(parse("  one  two  ")).get(0);
        assertEquals("one  two", text(line));
    }

    @Test
    void rejectsIndentationOrGlyphsThatCannotFit() {
        assertThrows(IllegalArgumentException.class,
                () -> new TextFormatter(30).format(parse(".indent 2\nword")));
        assertThrows(IllegalArgumentException.class,
                () -> new TextFormatter(1).format(parse("W")));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsInvalidDimensions(double width) {
        assertThrows(IllegalArgumentException.class, () -> new TextFormatter(width));
        assertThrows(IllegalArgumentException.class, () -> new TextFormatter(100, width, 12, 16));
        assertThrows(IllegalArgumentException.class, () -> new TextFormatter(100, 18, width, 16));
        assertThrows(IllegalArgumentException.class, () -> new TextFormatter(100, 18, 12, width));
    }

    @Test
    void returnsImmutableLayout() throws Exception {
        List<TextFormatter.Line> lines = new TextFormatter(100).format(parse("Hello"));
        assertThrows(UnsupportedOperationException.class, lines::clear);
        assertThrows(UnsupportedOperationException.class, () -> lines.get(0).fragments().clear());
    }

    @Test
    void doesNotStretchTheFinalLineOfAFilledSectionWhenNofillStarts() throws Exception {
        List<TextFormatter.Line> lines = new TextFormatter(200)
                .format(parse(".fill\nshort filled section\n.nofill\nplain text"));
        assertEquals(2, lines.size());
        assertFalse(lines.get(0).justified());
        assertFalse(lines.get(0).lastInParagraph());
        assertTrue(lines.get(1).lastInParagraph());
    }

    /** Integration tests stay in the existing test structure; production still has five classes. */
    @Nested
    class PdfGenerationTests {
        @TempDir
        Path directory;

        @Test
        void createsReadableA4PdfWithExtractableText() throws Exception {
            Path output = directory.resolve("nested/result.pdf");
            TextFormatter formatter = new TextFormatter(PdfGenerator.contentWidth());
            int count = new PdfGenerator(formatter).generate(
                    formatter.format(parse("Hello\n.bold\n, world!")), output);
            assertTrue(Files.size(output) > 0);
            assertEquals(1, count);
            try (PDDocument document = Loader.loadPDF(output.toFile())) {
                assertEquals(1, document.getNumberOfPages());
                assertEquals(PDRectangle.A4.getWidth(), document.getPage(0).getMediaBox().getWidth());
                assertEquals(PDRectangle.A4.getHeight(), document.getPage(0).getMediaBox().getHeight());
                assertEquals("Hello, world!", new PDFTextStripper().getText(document).strip());
            }
        }

        @Test
        void rendersAllFontsAndSizesOnTheSameBaseline() throws Exception {
            Path output = generate("Regular\n.bold\nBOLD\n.italic\nITALIC\n.regular\n.large\nZLarge", "fonts.pdf");
            try (PDDocument document = Loader.loadPDF(output.toFile())) {
                PdfInspection inspection = inspect(document);
                List<TextPosition> characters = inspection.pages.get(0);
                TextPosition regular = character(characters, "R");
                TextPosition bold = character(characters, "B");
                TextPosition italic = character(characters, "I");
                TextPosition large = character(characters, "Z");
                assertEquals("Helvetica", regular.getFont().getName());
                assertEquals("Helvetica-Bold", bold.getFont().getName());
                assertEquals("Helvetica-Oblique", italic.getFont().getName());
                assertEquals("Helvetica", large.getFont().getName());
                assertEquals(12, regular.getFontSizeInPt(), EPSILON);
                assertEquals(12, bold.getFontSizeInPt(), EPSILON);
                assertEquals(12, italic.getFontSizeInPt(), EPSILON);
                assertEquals(16, large.getFontSizeInPt(), EPSILON);
                assertEquals(regular.getYDirAdj(), large.getYDirAdj(), EPSILON);
                assertEquals(regular.getYDirAdj(), bold.getYDirAdj(), EPSILON);
                assertEquals(regular.getYDirAdj(), italic.getYDirAdj(), EPSILON);
            }
        }

        @Test
        void preservesIndentedJustificationPositionsInTheActualPdf() throws Exception {
            TextFormatter formatter = new TextFormatter(PdfGenerator.contentWidth());
            List<TextFormatter.Line> lines = formatter.format(parse(".indent 2\n.fill\n"
                    + "one two three four five six seven eight nine ten ".repeat(8)));
            assertTrue(lines.get(0).justified());
            Path output = directory.resolve("justified.pdf");
            new PdfGenerator(formatter).generate(lines, output);
            try (PDDocument document = Loader.loadPDF(output.toFile())) {
                List<TextPosition> positions = inspect(document).pages.get(0);
                float baseline = positions.get(0).getYDirAdj();
                List<TextPosition> firstLine = positions.stream()
                        .filter(position -> Math.abs(position.getYDirAdj() - baseline) < 0.01).toList();
                assertEquals(PdfGenerator.MARGIN + 36, firstLine.get(0).getXDirAdj(), 0.01);
                TextPosition last = firstLine.get(firstLine.size() - 1);
                assertEquals(PDRectangle.A4.getWidth() - PdfGenerator.MARGIN,
                        last.getXDirAdj() + last.getWidthDirAdj(), 0.01);
            }
        }

        @Test
        void leavesDefaultNofillTextAtItsNaturalWidth() throws Exception {
            Path output = generate("one two three", "nofill.pdf");
            try (PDDocument document = Loader.loadPDF(output.toFile())) {
                List<TextPosition> positions = inspect(document).pages.get(0);
                TextPosition last = positions.get(positions.size() - 1);
                double expected = PdfGenerator.MARGIN + new TextFormatter(200)
                        .measure("one two three", TextBlock.FontStyle.REGULAR, TextBlock.FontSize.NORMAL);
                assertEquals(expected, last.getXDirAdj() + last.getWidthDirAdj(), 0.01);
            }
        }

        @Test
        void paginatesWithoutLosingTextOrCrossingMargins() throws Exception {
            List<TextBlock> paragraphs = new ArrayList<>();
            StringBuilder expected = new StringBuilder();
            for (int index = 0; index < 160; index++) {
                String text = "Paragraph%03d: descenders gyp and accents café.".formatted(index);
                paragraphs.addAll(parse(index % 2 == 0 ? ".large\n" + text : text));
                expected.append(text).append(' ');
            }
            TextFormatter formatter = new TextFormatter(PdfGenerator.contentWidth());
            Path output = directory.resolve("pagination.pdf");
            int count = new PdfGenerator(formatter).generate(formatter.format(paragraphs), output);
            assertTrue(count >= 3);
            try (PDDocument document = Loader.loadPDF(output.toFile())) {
                assertEquals(count, document.getNumberOfPages());
                PdfInspection inspection = inspect(document);
                assertEquals(expected.toString().strip(), inspection.extracted.replaceAll("\\s+", " ").strip());
                for (List<TextPosition> page : inspection.pages) {
                    assertFalse(page.isEmpty(), "No trailing or intervening blank pages");
                    for (TextPosition position : page) {
                        double size = position.getFontSizeInPt();
                        double ascent = position.getFont().getBoundingBox().getUpperRightY() * size / 1000;
                        double descent = -position.getFont().getBoundingBox().getLowerLeftY() * size / 1000;
                        assertTrue(position.getXDirAdj() >= PdfGenerator.MARGIN - 0.01);
                        assertTrue(position.getXDirAdj() + position.getWidthDirAdj()
                                <= PDRectangle.A4.getWidth() - PdfGenerator.MARGIN + 0.01);
                        assertTrue(position.getYDirAdj() - ascent >= PdfGenerator.MARGIN - 0.01);
                        assertTrue(position.getYDirAdj() + descent
                                <= PDRectangle.A4.getHeight() - PdfGenerator.MARGIN + 0.01);
                    }
                }
            }
        }

        @Test
        void addsParagraphSpacingBeyondOrdinaryLineSpacing() throws Exception {
            Path paragraphOutput = generate("First\n.paragraph\nSecond", "paragraphs.pdf");
            TextFormatter narrow = new TextFormatter(50);
            Path wrappedOutput = directory.resolve("wrapped.pdf");
            new PdfGenerator(narrow).generate(narrow.format(parse("First Second")), wrappedOutput);
            try (PDDocument paragraphs = Loader.loadPDF(paragraphOutput.toFile());
                 PDDocument wrapped = Loader.loadPDF(wrappedOutput.toFile())) {
                List<TextPosition> spaced = inspect(paragraphs).pages.get(0);
                List<TextPosition> ordinary = inspect(wrapped).pages.get(0);
                double paragraphDistance = character(spaced, "S").getYDirAdj() - character(spaced, "F").getYDirAdj();
                double lineDistance = character(ordinary, "S").getYDirAdj() - character(ordinary, "F").getYDirAdj();
                assertEquals(PdfGenerator.PARAGRAPH_SPACING, paragraphDistance - lineDistance, 0.01);
            }
        }

        @Test
        void writesOneBlankPageForEmptyInput() throws Exception {
            Path output = directory.resolve("empty.pdf");
            assertEquals(1, new PdfGenerator().generate(List.of(), output));
            try (PDDocument document = Loader.loadPDF(output.toFile())) {
                assertEquals(1, document.getNumberOfPages());
                assertTrue(new PDFTextStripper().getText(document).isBlank());
            }
        }

        @Test
        void rejectsHorizontalOverflowAndLinesTallerThanThePage() throws Exception {
            TextFormatter oversized = new TextFormatter(2000);
            List<TextFormatter.Line> wide = oversized.format(parse("W".repeat(100)));
            assertThrows(IllegalArgumentException.class, () -> new PdfGenerator(oversized)
                    .generate(wide, directory.resolve("too-wide.pdf")));
            TextFormatter tall = new TextFormatter(PdfGenerator.contentWidth(), 18, 800, 900);
            List<TextFormatter.Line> high = tall.format(parse("i"));
            assertThrows(IllegalArgumentException.class, () -> new PdfGenerator(tall)
                    .generate(high, directory.resolve("too-tall.pdf")));
        }

        @Test
        void suppliedSampleRepeatedTenTimesProducesAtLeastThreePages() throws Exception {
            List<TextBlock> sample = parse(Files.readString(Path.of("input/sample.txt")));
            List<TextBlock> repeated = new ArrayList<>();
            for (int copy = 0; copy < Main.DEFAULT_REPETITIONS; copy++) {
                repeated.addAll(sample);
            }
            TextFormatter formatter = new TextFormatter(PdfGenerator.contentWidth());
            Path output = directory.resolve("sample.pdf");
            int count = new PdfGenerator(formatter).generate(formatter.format(repeated), output);
            try (PDDocument document = Loader.loadPDF(output.toFile())) {
                assertTrue(count >= 3);
                assertEquals(count, document.getNumberOfPages());
                String extracted = new PDFTextStripper().getText(document);
                assertEquals(Main.DEFAULT_REPETITIONS, extracted.split("My PDF Document", -1).length - 1);
                assertEquals(Main.DEFAULT_REPETITIONS, extracted.split("exciting, good luck!", -1).length - 1);
            }
        }

        private Path generate(String input, String filename) throws IOException {
            Path output = directory.resolve(filename);
            TextFormatter formatter = new TextFormatter(PdfGenerator.contentWidth());
            new PdfGenerator(formatter).generate(formatter.format(parse(input)), output);
            return output;
        }

        private TextPosition character(List<TextPosition> characters, String text) {
            return characters.stream().filter(position -> position.getUnicode().equals(text))
                    .findFirst().orElseThrow();
        }

        private PdfInspection inspect(PDDocument document) throws IOException {
            PdfInspection inspection = new PdfInspection();
            inspection.extracted = inspection.getText(document);
            return inspection;
        }
    }

    /** Observe actual PDF text operators rather than trusting the formatter's own output. */
    private static final class PdfInspection extends PDFTextStripper {
        private final List<List<TextPosition>> pages = new ArrayList<>();
        private String extracted;

        private PdfInspection() throws IOException { }

        @Override
        protected void startPage(PDPage page) throws IOException {
            super.startPage(page);
            pages.add(new ArrayList<>());
        }

        @Override
        protected void processTextPosition(TextPosition text) {
            pages.get(pages.size() - 1).add(text);
            super.processTextPosition(text);
        }
    }

    private List<TextBlock> parse(String input) {
        return new TextParser().parse(input);
    }

    private TextBlock.Run run(String text, TextBlock.FontStyle style) {
        return new TextBlock.Run(text, style, TextBlock.FontSize.NORMAL, 0, true);
    }

    private String text(TextFormatter.Line line) {
        return line.fragments().stream().map(TextFormatter.Fragment::text).reduce("", String::concat);
    }

    private double rightEdge(TextFormatter.Line line) {
        TextFormatter.Fragment last = line.fragments().get(line.fragments().size() - 1);
        return last.x() + last.width();
    }

    private void assertContiguous(TextFormatter.Line line) {
        double x = line.indent();
        for (TextFormatter.Fragment fragment : line.fragments()) {
            assertEquals(x, fragment.x(), EPSILON);
            x += fragment.width();
        }
        assertEquals(line.width(), x - line.indent(), EPSILON);
    }
}
