# PDF Creator

Java implementation of the Gearset Software Engineering Internship Assessment.
Reads UTF-8 commands and text from `input/sample.txt` and writes `output/result.pdf`.
The supplied assessment example is repeated ten times to demonstrate pagination.

## Requirements

- JDK 17 or newer, with `JAVA_HOME` pointing to that JDK.
- Maven 3.9 or newer and internet access for the first dependency download.
- Apache PDFBox 3.0.3 and JUnit 5 are declared in `pom.xml`.

Run all commands from the extracted `pdf-creator` directory.

## Build and test

```sh
mvn clean package
```

This compiles for Java 17, runs the tests and builds a JAR in `target/`.
To run just the tests, use `mvn clean test`.

## Run

Copy the runtime dependencies once after a clean build:

```sh
mvn dependency:copy-dependencies "-DincludeScope=runtime"
```

Windows (PowerShell):

```sh
& "$env:JAVA_HOME/bin/java.exe" -cp "target/classes;target/dependency/*" Main
```

macOS/Linux:

```sh
java -cp "target/classes:target/dependency/*" Main
```

The application reports the output path and page count. The default example produces
three A4 pages. An optional positive argument changes the repetition count, e.g.
append `1` to the appropriate command above to generate one copy.
The JAR is not self-contained; use the classpath commands above.

For an IDE, import `pom.xml` as a Maven project, select JDK 17 or newer, and run
`Main.main` with the project directory as the working directory. Maven uses the
existing `src/main` and `src/test` directories directly.

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
