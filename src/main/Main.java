import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Main {
    // The assessment asks for repeated sample text covering at least three pages.
    public static final int DEFAULT_REPETITIONS = 10;

    /** Run from the project folder. Optional argument: a positive sample repetition count. */
    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalArgumentException("Usage: Main [positive repetition count]");
        }
        int repetitions = args.length == 0 ? DEFAULT_REPETITIONS : Integer.parseInt(args[0]);
        if (repetitions < 1) {
            throw new IllegalArgumentException("Repetition count must be positive");
        }

        List<TextBlock> sample;
        try (Reader reader = Files.newBufferedReader(Path.of("input/sample.txt"), StandardCharsets.UTF_8)) {
            sample = new TextParser().parse(reader);
        }
        if (sample.isEmpty()) {
            throw new IllegalArgumentException("input/sample.txt contains no text");
        }
        // Repeat the parsed sample so each copy starts with its original formatting state.
        List<TextBlock> paragraphs = new ArrayList<>();
        for (int copy = 0; copy < repetitions; copy++) {
            paragraphs.addAll(sample);
        }
        TextFormatter formatter = new TextFormatter(PdfGenerator.contentWidth());
        Path output = Path.of("output/result.pdf");
        int pages = new PdfGenerator(formatter).generate(formatter.format(paragraphs), output);
        System.out.printf("Generated %s: %d pages, %d sample repetitions%n",
                output.toAbsolutePath(), pages, repetitions);
        if (args.length == 0 && pages < 3) {
            throw new IllegalStateException("The default repeated sample must produce at least three pages");
        }
    }
}
