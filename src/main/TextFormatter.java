import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Horizontal layout only. All dimensions are PDF points; availableWidth excludes margins.
 * Defaults: Helvetica, 12pt normal, 16pt large, 18pt per logical indentation unit.
 * Source line breaks/tabs become spaces. Other spaces are retained between words;
 * leading/trailing whitespace at a formatted line boundary is discarded.
 * Closing punctuation attaches to the preceding word, including across run boundaries.
 * Indent/fill changes force a line boundary; style/size changes do not.
 */
public final class TextFormatter {
    // Ignore sub-micropoint rounding when summing individual PDFBox glyph widths.
    private static final double WIDTH_EPSILON = 0.0000001;
    private final double availableWidth;
    private final double indentUnit;
    private final double normalSize;
    private final double largeSize;
    private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final PDFont italic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

    public TextFormatter(double availableWidth) {
        this(availableWidth, 18, 12, 16);
    }

    public TextFormatter(double availableWidth, double indentUnit, double normalSize, double largeSize) {
        requirePositive(availableWidth, "Available width");
        requirePositive(indentUnit, "Indent unit");
        requirePositive(normalSize, "Normal font size");
        requirePositive(largeSize, "Large font size");
        this.availableWidth = availableWidth;
        this.indentUnit = indentUnit;
        this.normalSize = normalSize;
        this.largeSize = largeSize;
    }

    /** x is relative to the content area's left edge. A space's width includes justification. */
    public record Fragment(String text, TextBlock.FontStyle style, TextBlock.FontSize size,
                           double x, double width) { }

    /** width excludes indentation and includes expanded spaces. Paragraph indices are zero-based. */
    public record Line(List<Fragment> fragments, double indent, double width,
                       boolean justified, boolean lastInParagraph, int paragraphIndex) {
        public Line {
            fragments = List.copyOf(fragments);
        }
    }

    private record Glyph(String text, TextBlock.FontStyle style, TextBlock.FontSize size,
                         int indent, boolean fill, double width) { }
    private record Word(List<Glyph> glyphs, List<Glyph> gap) { }

    public double fontSize(TextBlock.FontSize size) {
        return switch (Objects.requireNonNull(size, "size")) {
            case NORMAL -> normalSize;
            case LARGE -> largeSize;
        };
    }

    /** Uses the same PDFBox fonts as layout; unsupported glyphs fail with PDFBox's diagnostic. */
    public double measure(String text, TextBlock.FontStyle style, TextBlock.FontSize size)
            throws IOException {
        PDFont font = switch (Objects.requireNonNull(style, "style")) {
            case REGULAR -> regular;
            case BOLD -> bold;
            case ITALIC -> italic;
        };
        return font.getStringWidth(Objects.requireNonNull(text, "text")) * fontSize(size) / 1000;
    }

    public List<Line> format(List<TextBlock> paragraphs) throws IOException {
        Objects.requireNonNull(paragraphs, "paragraphs");
        List<Line> lines = new ArrayList<>();
        for (int index = 0; index < paragraphs.size(); index++) {
            formatParagraph(paragraphs.get(index), index, lines);
        }
        return List.copyOf(lines);
    }

    private void formatParagraph(TextBlock paragraph, int index, List<Line> lines) throws IOException {
        List<Word> words = words(paragraph);
        if (words.isEmpty()) {
            int units = paragraph.runs().isEmpty() ? 0 : paragraph.runs().get(0).indent();
            double indent = indentation(units);
            lines.add(new Line(List.of(), indent, 0, false, true, index));
            return;
        }
        List<Word> current = new ArrayList<>();
        double used = 0;
        Glyph settings = words.get(0).glyphs().get(0);
        for (Word word : words) {
            Glyph nextSettings = word.glyphs().get(0);
            if (!sameLayout(settings, nextSettings)) {
                if (!current.isEmpty()) {
                    lines.add(line(current, settings, false, false, index));
                    current.clear();
                }
                used = 0;
                settings = nextSettings;
            }
            double capacity = availableWidth - indentation(settings.indent());
            double wordWidth = width(word.glyphs());
            double gapWidth = current.isEmpty() ? 0 : width(word.gap());
            if (!current.isEmpty() && used + gapWidth + wordWidth > capacity + WIDTH_EPSILON) {
                lines.add(line(current, settings, false, true, index));
                current.clear();
                used = 0;
            }
            List<List<Glyph>> chunks = splitWord(word.glyphs(), capacity);
            for (int i = 0; i < chunks.size(); i++) {
                List<Glyph> gap = current.isEmpty() ? List.of() : word.gap();
                current.add(new Word(chunks.get(i), gap));
                used += width(gap) + width(chunks.get(i));
                if (i < chunks.size() - 1) {
                    lines.add(line(current, settings, false, true, index));
                    current.clear();
                    used = 0;
                }
            }
        }
        lines.add(line(current, settings, true, false, index));
    }

    private Line line(List<Word> words, Glyph settings, boolean last, boolean wrapped, int paragraphIndex) {
        double indent = indentation(settings.indent());
        double natural = 0;
        for (int i = 0; i < words.size(); i++) {
            natural += width(words.get(i).glyphs());
            if (i > 0) {
                natural += width(words.get(i).gap());
            }
        }
        boolean justify = settings.fill() && wrapped && !last && words.size() > 1
                && availableWidth - indent - natural > WIDTH_EPSILON;
        double extra = justify ? (availableWidth - indent - natural) / (words.size() - 1) : 0;
        List<Fragment> fragments = new ArrayList<>();
        double x = indent;
        for (int i = 0; i < words.size(); i++) {
            Word word = words.get(i);
            if (i > 0) {
                List<Glyph> gap = word.gap();
                for (int j = 0; j < gap.size(); j++) {
                    Glyph glyph = gap.get(j);
                    double advance = glyph.width() + (j == gap.size() - 1 ? extra : 0);
                    append(fragments, glyph, x, advance);
                    x += advance;
                }
            }
            for (Glyph glyph : word.glyphs()) {
                append(fragments, glyph, x, glyph.width());
                x += glyph.width();
            }
        }
        return new Line(fragments, indent, x - indent, justify, last, paragraphIndex);
    }

    private void append(List<Fragment> fragments, Glyph glyph, double x, double advance) {
        // Keep spaces separate so the renderer can use their explicit advances.
        if (!fragments.isEmpty() && !glyph.text().equals(" ")) {
            Fragment previous = fragments.get(fragments.size() - 1);
            if (!previous.text().endsWith(" ") && previous.style() == glyph.style()
                    && previous.size() == glyph.size()) {
                fragments.set(fragments.size() - 1, new Fragment(previous.text() + glyph.text(),
                        glyph.style(), glyph.size(), previous.x(), previous.width() + advance));
                return;
            }
        }
        fragments.add(new Fragment(glyph.text(), glyph.style(), glyph.size(), x, advance));
    }

    private List<Word> words(TextBlock paragraph) throws IOException {
        List<Glyph> glyphs = new ArrayList<>();
        for (TextBlock.Run run : paragraph.runs()) {
            for (int offset = 0; offset < run.text().length();) {
                int codePoint = run.text().codePointAt(offset);
                offset += Character.charCount(codePoint);
                String text = Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)
                        ? " " : new String(Character.toChars(codePoint));
                if (closingPunctuation(text) && !glyphs.isEmpty()) {
                    while (!glyphs.isEmpty() && glyphs.get(glyphs.size() - 1).text().equals(" ")) {
                        glyphs.remove(glyphs.size() - 1);
                    }
                }
                glyphs.add(new Glyph(text, run.style(), run.size(), run.indent(), run.fill(),
                        measure(text, run.style(), run.size())));
            }
        }
        List<Word> result = new ArrayList<>();
        List<Glyph> current = new ArrayList<>();
        List<Glyph> gap = new ArrayList<>();
        for (Glyph glyph : glyphs) {
            boolean layoutChanged = !current.isEmpty() && !sameLayout(current.get(0), glyph);
            if ((glyph.text().equals(" ") || layoutChanged) && !current.isEmpty()) {
                result.add(new Word(List.copyOf(current), List.copyOf(gap)));
                current.clear();
                gap.clear();
            }
            if (glyph.text().equals(" ")) {
                gap.add(glyph);
            } else {
                current.add(glyph);
            }
        }
        if (!current.isEmpty()) {
            result.add(new Word(List.copyOf(current), List.copyOf(gap)));
        }
        return result;
    }

    private boolean sameLayout(Glyph left, Glyph right) {
        return left.indent() == right.indent() && left.fill() == right.fill();
    }

    /** Split at code-point boundaries, keeping closing punctuation with a preceding character. */
    private List<List<Glyph>> splitWord(List<Glyph> glyphs, double capacity) {
        List<List<Glyph>> chunks = new ArrayList<>();
        for (int start = 0; start < glyphs.size();) {
            int end = start;
            double used = 0;
            while (end < glyphs.size() && used + glyphs.get(end).width() <= capacity + WIDTH_EPSILON) {
                used += glyphs.get(end).width();
                end++;
            }
            while (end > start && end < glyphs.size() && closingPunctuation(glyphs.get(end).text())) {
                end--;
            }
            if (end == start) {
                throw new IllegalArgumentException("Available line width " + capacity
                        + " is too small for glyph or attached punctuation near '"
                        + glyphs.get(start).text() + "'");
            }
            chunks.add(List.copyOf(glyphs.subList(start, end)));
            start = end;
        }
        return chunks;
    }

    private boolean closingPunctuation(String text) {
        return ",.;:!?%)]}»”’".contains(text);
    }

    private double indentation(int units) {
        double indent = units * indentUnit;
        if (indent >= availableWidth) {
            throw new IllegalArgumentException("Indentation " + indent
                    + " leaves no available line width (" + availableWidth + ")");
        }
        return indent;
    }

    private double width(List<Glyph> glyphs) {
        return glyphs.stream().mapToDouble(Glyph::width).sum();
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
