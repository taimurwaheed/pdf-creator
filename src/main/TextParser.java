import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Commands occupy whole lines (surrounding whitespace is allowed).
 * .indent N changes indentation by N logical units: +2 then -1 leaves one unit.
 * Indentation starts at zero and cannot become negative.
 * Formatting persists across paragraphs; .paragraph alone starts a new paragraph.
 * Text starts in regular, normal-size, unindented, nofill mode.
 * Source spaces and blank lines are retained; line endings normalize to '\n'.
 * The final input line terminator is not included. Fill is metadata only;
 * interpreting line breaks for layout belongs to the future formatter.
 */
public final class TextParser {
    public List<TextBlock> parse(String input) {
        Objects.requireNonNull(input, "input");
        try {
            return parse(new StringReader(input));
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected error reading a string", exception);
        }
    }

    /** Reads without closing the caller-owned reader. Each call starts with defaults. */
    public List<TextBlock> parse(Reader input) throws IOException {
        Objects.requireNonNull(input, "input");
        BufferedReader reader = input instanceof BufferedReader buffered
                ? buffered : new BufferedReader(input);
        List<TextBlock> blocks = new ArrayList<>();
        List<TextBlock.Run> runs = new ArrayList<>();
        TextBlock.FontStyle style = TextBlock.FontStyle.REGULAR;
        TextBlock.FontSize size = TextBlock.FontSize.NORMAL;
        int indent = 0;
        boolean fill = false;
        int lineNumber = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            String command = line.strip();
            if (!command.startsWith(".")) {
                String text = runs.isEmpty() ? line : "\n" + line;
                runs.add(new TextBlock.Run(text, style, size, indent, fill));
                continue;
            }
            String[] parts = command.split("\\s+");
            if (parts[0].equals(".indent")) {
                if (parts.length != 2) {
                    throw invalid(lineNumber, "Expected .indent followed by an integer delta");
                }
                try {
                    int nextIndent = Math.addExact(indent, Integer.parseInt(parts[1]));
                    if (nextIndent < 0) {
                        throw invalid(lineNumber, "Indentation cannot become negative");
                    }
                    indent = nextIndent;
                } catch (NumberFormatException | ArithmeticException exception) {
                    throw invalid(lineNumber, "Invalid indentation: " + parts[1]);
                }
                continue;
            }
            if (parts.length != 1) {
                throw invalid(lineNumber, "Unsupported command or arguments: " + command);
            }
            switch (command) {
                case ".paragraph" -> {
                    if (!runs.isEmpty()) {
                        blocks.add(new TextBlock(runs));
                        runs.clear();
                    }
                }
                case ".fill" -> fill = true;
                case ".nofill" -> fill = false;
                case ".regular" -> style = TextBlock.FontStyle.REGULAR;
                case ".italic" -> style = TextBlock.FontStyle.ITALIC;
                case ".bold" -> style = TextBlock.FontStyle.BOLD;
                case ".large" -> size = TextBlock.FontSize.LARGE;
                case ".normal" -> size = TextBlock.FontSize.NORMAL;
                default -> throw invalid(lineNumber, "Unsupported command: " + command);
            }
        }
        if (!runs.isEmpty()) {
            blocks.add(new TextBlock(runs));
        }
        return List.copyOf(blocks);
    }

    private IllegalArgumentException invalid(int lineNumber, String message) {
        return new IllegalArgumentException("Line " + lineNumber + ": " + message);
    }
}
