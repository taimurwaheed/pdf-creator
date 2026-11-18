# PDF Creator

Java implementation of the Gearset Software Engineering Internship Assessment.
Reads UTF-8 commands and text from `input/sample.txt` and writes `output/result.pdf`.
The supplied assessment example is repeated ten times to demonstrate pagination.

## Requirements

- JDK 17 or newer, with `JAVA_HOME` pointing to that JDK.
- Maven 3.9 or newer and internet access for the first dependency download.
- Apache PDFBox 3.0.3 and JUnit 5 are declared in `pom.xml`.

Run all commands from the extracted `pdf-creator` directory.

## Run

```sh
mvn clean compile exec:java
```

This compiles the application and runs `Main` with its runtime dependencies.
It automatically reads `input/sample.txt`, repeats the sample ten times and writes
`output/result.pdf`. The output directory is created if necessary. The application
reports the output path and page count; the supplied example produces three A4 pages.

## Test and build

Run all automated tests:

```sh
mvn clean test
```

Build a JAR and run the tests:

```sh
mvn clean package
```

The run command compiles and generates the PDF; it does not run the tests.
The packaged JAR is not self-contained; use the Maven run command above.

To generate a single sample copy instead of ten:

```sh
mvn clean compile exec:java "-Dexec.args=1"
```

Running without `-Dexec.args` restores the default ten-copy, three-page example.

## VS Code and IntelliJ

Open the project directory in VS Code with Java/Maven support, or import `pom.xml`
as a Maven project in IntelliJ. Select JDK 17 or newer. In the IDE terminal, run
`mvn clean compile exec:java` from the project directory. Alternatively, run
`Main.main` with that directory as its working directory. Maven uses the existing
`src/main` and `src/test` directories directly.

## Design and documentation

The five production classes have separate responsibilities: `Main` connects the
pipeline, `TextParser` reads commands, `TextBlock` stores immutable formatting
snapshots, `TextFormatter` measures and positions lines, and `PdfGenerator` renders
those lines and creates pages. PDF integration tests remain in `TextFormatterTest`.

- [Assumptions and limitations](docs/ASSUMPTIONS.md)
- [Assessment review and verification](docs/REVIEW.md)
- [Actual AI prompt history and disclosure](docs/AI_PROMPT_HISTORY.md)

This implementation, tests and documentation were substantially written with Codex.
The history is provided transparently; this is not a claim of independent authorship.
The assessment has conflicting AI guidance, discussed in the review document.
