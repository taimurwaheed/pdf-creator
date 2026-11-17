# Final submission review

Reviewed on 17 November 2026 against all three pages of the supplied
`EngineeringInternshipAssessment2027..pdf`, including the rendered example and
submission notes. The source brief's SHA-256 is:

```text
5151c7ea007fbc7d813ba0d1b96603f08f04e67fbd530d65ecab97e03bf7f50f
```

## Functional review

| Assessment requirement | Review result |
| --- | --- |
| Java application reading a supplied text file and writing a PDF | `Main` reads UTF-8 `input/sample.txt` and writes `output/result.pdf`. |
| All nine commands | Parser covers every command; tests cover formatting state and invalid input. |
| Default `.nofill` | Default is left-aligned; `.fill` is explicit. |
| Inline regular, italic and bold | Formatter retains run styles; integration tests inspect actual PDF fonts and positions. |
| Normal and larger text | 12pt and 16pt; mixed sizes share a baseline and determine line height. |
| Indentation | Relative changes of 18pt per unit, applied to every affected line; the brief permits other unit widths. |
| Filled paragraphs | Measured wrapping and expanded word gaps reach the right margin; final lines remain left-aligned. See the single-word limitation. |
| Leading comma in the final text run | Output contains `Well that was exciting, good luck!` with the comma attached. |
| Repetition and at least three pages | Ten copies of the example generate three A4 pages. |
| Readable, testable design | Five production classes with parsing, data, formatting, PDF and entry-point responsibilities; no new framework or abstraction added. |
| Easy build/run and ZIP submission | Maven project with README commands, sample, output, tests and supporting documentation. |

The input matches the PDF's example after normalizing extraction whitespace,
private-use indentation-sign glyphs to ASCII `+`/`-`, and the extracted closing
quote to a standard typographic closing quote. The rendered input example was
also inspected. These are PDF text-encoding normalizations, not changes to the
example's words or commands.

No new functional defect requiring a code change was identified in this review.
The existing abstractions serve the mixed-formatting and positioning requirements;
a broader simplification was not justified. Only a stale renderer comment was
updated. The documented assumptions and edge-case limitations remain relevant.

## Actual verification

The final build command was:

```sh
mvn -B "-Dpdfbox.fontcache=target" clean package dependency:copy-dependencies "-DincludeScope=runtime"
```

Result: **BUILD SUCCESS; 51 tests, zero failures, zero errors, zero skipped**.
The JAR was built and the runtime dependencies were copied successfully. The
dependency-copy goal is a Maven command, not an added application dependency.

The README's Windows classpath was used to run the program in PowerShell:

```powershell
& "$env:JAVA_HOME/bin/java.exe" "-Dpdfbox.fontcache=target" -cp "target/classes;target/dependency/*" Main
```

The optional font-cache property keeps PDFBox's cache inside the build directory;
it is not needed for normal installations with a writable home directory.

- Environment: Windows, Maven 3.9.9, JDK 18.0.2.1, compiler release target 17.
- Output: `output/result.pdf`, **three A4 portrait pages**, 13,655 bytes.
- Reopened the final file with pypdf; Poppler independently reported three A4 pages.
- Extracted text matched all ten complete sample copies after whitespace
  normalization: 6,579 characters, including the expected punctuation.
- Rendered every final page with Poppler and visually inspected justification,
  indentation, fonts, font sizes and page boundaries. No clipping or overlapping
  text was observed. Pixel-bound checks also confirmed ink remained inside the
  margins within raster rounding tolerance.
- PDF tests independently reload generated documents with PDFBox and inspect
  extracted text, font names/sizes, positions, paragraph spacing and pagination.
- Poppler emitted an unused `Symbol` display-font warning; all example characters
  rendered correctly. PDFBox warned when replacing the existing output file; the
  replacement was subsequently reopened and verified.
- A separate JDK 17 runtime execution and macOS/Linux execution were not performed.
  Build bytecode and API compatibility are targeted to Java 17.

## AI disclosure and unresolved assessment ambiguity

Page 3 contains conflicting guidance: its Notes section discourages AI creating
the assessment, while the later Use of AI section welcomes tools, asks for the
candidate's own reasoning to lead, and requests prompt history. This project has
substantial AI-written implementation, tests and documentation. Including a prompt
history does not establish that the work meets the assessor's expectations about
authorship. That ambiguity remains for the candidate and assessor to resolve.

`AI_PROMPT_HISTORY.md` contains the actual four task prompts and the intervening
clarification reply exported from this chat. No candidate thought process or
fictional conversation has been added. It is a user-prompt export, not a full tool
or assistant transcript.

## Archive

`Gearset-PDF-Creator-submission.zip` contains one `pdf-creator/` root with 14 files:
the five production sources, two test sources, `pom.xml`, the sample input, the
generated PDF, README and these three supporting documents. Archive entries are
selected explicitly rather than copying the workspace recursively. `.git`,
`target`, IDE metadata, dependencies, caches, previews and temporary files are
excluded. ZIP CRCs and entry bytes are checked against the reviewed files.

See `ASSUMPTIONS.md` for all remaining implementation assumptions and limitations.
