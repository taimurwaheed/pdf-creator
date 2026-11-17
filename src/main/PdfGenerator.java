import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.apache.fontbox.util.BoundingBox;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;

/** Renders the formatter's horizontal layout; owns vertical layout and pagination only. */
public final class PdfGenerator {
    public static final float MARGIN = 50;
    public static final double PARAGRAPH_SPACING = 8;
    private static final double EPSILON = 0.001;

    private final TextFormatter formatter;
    private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final PDFont italic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

    public PdfGenerator() {
        this(new TextFormatter(contentWidth()));
    }

    /** Pass the formatter used to produce the lines so configured font sizes stay identical. */
    public PdfGenerator(TextFormatter formatter) {
        this.formatter = Objects.requireNonNull(formatter, "formatter");
    }

    public static double contentWidth() {
        return PDRectangle.A4.getWidth() - 2 * MARGIN;
    }

    /** Writes an A4 PDF and returns its page count. Empty input produces one blank page. */
    public int generate(List<TextFormatter.Line> lines, Path output) throws IOException {
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(output, "output");
        try (PDDocument document = new PDDocument()) {
            int nextLine = 0;
            do {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                double top = page.getMediaBox().getHeight() - MARGIN;
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    while (nextLine < lines.size()) {
                        TextFormatter.Line line = lines.get(nextLine);
                        LineMetrics metrics = metrics(line);
                        if (metrics.height() > PDRectangle.A4.getHeight() - 2 * MARGIN) {
                            throw new IllegalArgumentException("A line is taller than the usable page height");
                        }
                        if (top - metrics.height() < MARGIN - EPSILON) {
                            break; // Retry this same line at the top of the next page.
                        }
                        renderLine(stream, line, top - metrics.ascent());
                        top -= metrics.height() + metrics.leading();
                        if (line.lastInParagraph()) {
                            top -= PARAGRAPH_SPACING;
                        }
                        nextLine++;
                    }
                }
            } while (nextLine < lines.size());

            Path destination = output.toAbsolutePath();
            Files.createDirectories(destination.getParent());
            document.save(destination.toFile());
            return document.getNumberOfPages();
        }
    }

    private void renderLine(PDPageContentStream stream, TextFormatter.Line line, double baseline)
            throws IOException {
        stream.beginText();
        try {
            for (TextFormatter.Fragment fragment : line.fragments()) {
                validateFragment(fragment);
                stream.setFont(font(fragment.style()), (float) formatter.fontSize(fragment.size()));
                // Absolute positioning preserves indentation and the formatter's expanded word gaps.
                stream.setTextMatrix(Matrix.getTranslateInstance(
                        (float) (MARGIN + fragment.x()), (float) baseline));
                stream.showText(fragment.text());
            }
        } finally {
            stream.endText();
        }
    }

    private void validateFragment(TextFormatter.Fragment fragment) throws IOException {
        double naturalWidth = formatter.measure(fragment.text(), fragment.style(), fragment.size());
        if (!Double.isFinite(fragment.x()) || !Double.isFinite(fragment.width())
                || fragment.x() < -EPSILON || fragment.width() < naturalWidth - EPSILON
                || fragment.x() + fragment.width() > contentWidth() + EPSILON) {
            throw new IllegalArgumentException("Text fragment exceeds page margins or has an invalid width: "
                    + fragment.text());
        }
    }

    /** Conservative font bounding boxes include ascenders, accents and descenders. */
    private LineMetrics metrics(TextFormatter.Line line) throws IOException {
        double ascent = 0;
        double descent = 0;
        double maxSize = formatter.fontSize(TextBlock.FontSize.NORMAL);
        for (TextFormatter.Fragment fragment : line.fragments()) {
            double size = formatter.fontSize(fragment.size());
            BoundingBox box = font(fragment.style()).getBoundingBox();
            ascent = Math.max(ascent, box.getUpperRightY() * size / 1000);
            descent = Math.max(descent, -box.getLowerLeftY() * size / 1000);
            maxSize = Math.max(maxSize, size);
        }
        if (line.fragments().isEmpty()) {
            BoundingBox box = regular.getBoundingBox();
            ascent = box.getUpperRightY() * maxSize / 1000;
            descent = -box.getLowerLeftY() * maxSize / 1000;
        }
        return new LineMetrics(ascent, descent, maxSize * 0.2);
    }

    private PDFont font(TextBlock.FontStyle style) {
        return switch (style) {
            case REGULAR -> regular;
            case BOLD -> bold;
            case ITALIC -> italic;
        };
    }

    private record LineMetrics(double ascent, double descent, double leading) {
        double height() {
            return ascent + descent;
        }
    }
}
