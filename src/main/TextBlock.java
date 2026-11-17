import java.util.List;
import java.util.Objects;

/** One paragraph containing immutable text runs with formatting snapshots. */
public record TextBlock(List<Run> runs) {
    public TextBlock {
        runs = List.copyOf(runs);
    }

    public enum FontStyle { REGULAR, ITALIC, BOLD }
    public enum FontSize { NORMAL, LARGE }

    /** Indent is a logical unit; its physical width belongs to the formatter. */
    public record Run(String text, FontStyle style, FontSize size, int indent, boolean fill) {
        public Run {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(style, "style");
            Objects.requireNonNull(size, "size");
            if (indent < 0) {
                throw new IllegalArgumentException("Indent cannot be negative");
            }
        }
    }
}
