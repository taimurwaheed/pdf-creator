# Code review and submission readiness report

Date: 17 November 2026  
Reviewed commit: `e18e0ae` (`Implement PDF creator with formatting, pagination and tests`)

## Verdict

The implementation is readable, has sensible responsibilities and is well tested
for the assessment example. The current application builds successfully and
produces three A4 pages containing all ten complete copies of the example.

**Recommend a small correction before an unqualified submission-ready verdict:**
there is a confirmed italic glyph overhang bug that the current margin tests do
not detect. Two lower-priority input/repository issues are described below. No
critical or high-priority defects were found in the code reviewed. This is a
review assessment, not a guarantee that no other defects exist.

Application source and existing tests were not modified by this review. This
report is the only permanent file added. Probes and freshly generated output are
under `target/review/`; the tracked example PDF was left unchanged.

## Findings, ordered by priority

### 1. [P2] Italic glyph ink can cross the right page margin

Locations: `src/main/PdfGenerator.java:95-101`,
`src/main/TextFormatter.java:140-158`; related test gap at
`src/test/TextFormatterTest.java:400-402`.

The renderer validates horizontal advance widths, not the visible outlines of
glyphs. Italic characters can extend beyond their advance widths. Consequently,
a line can pass every existing margin assertion and still paint outside the
right margin. Vertical layout already uses font bounding boxes, but that does
not protect the horizontal edges.

Reproduction using the unchanged application classes:

```java
TextFormatter formatter = new TextFormatter(PdfGenerator.contentWidth());
var lines = formatter.format(new TextParser().parse(
        ".italic\n.fill\n" + "f ".repeat(300)));
new PdfGenerator(formatter).generate(lines, Path.of("italic-margin.pdf"));
```

Actual observations:

- The first line is justified. Its reported advance ends at approximately
  **545.276pt**, exactly the A4 right content boundary with a 50pt margin.
- Rendering with Poppler at 288 DPI produced dark pixels through column 2185;
  their exclusive right edge is **546.5pt**, roughly **1.22pt outside** that
  boundary. This exceeds ordinary one-pixel rounding at that resolution.
- The sample supplied by Gearset does not trigger this case. Its rendered pages
  looked correct in the fresh visual inspection.

Impact: the broad promise that supported text never crosses the page margins is
not currently true. The existing tests inspect `TextPosition` advances and
therefore cannot catch this particular failure.

Recommended correction: account for relevant glyph ink overhang at the line
edges while measuring/positioning text, keeping formatter and renderer geometry
consistent. Add a regression using an italic line ending in an overhanging glyph.
Check actual glyph bounds or rendered ink, not only character advances. Clipping
the excess would hide part of the glyph and is not a satisfactory correction.

### 2. [P3] UTF-8 files with a leading BOM fail to format

Locations: `src/main/TextParser.java:44-47`; input opened by
`src/main/Main.java:25-26`.

The application advertises UTF-8 input, but it does not consume an optional UTF-8
byte-order mark. A decoded leading U+FEFF is not removed by `String.strip()`.
For a file beginning with a BOM followed by `.large`, the parser treats the first
command as text and the formatter subsequently fails to encode U+FEFF in Helvetica.

Reproduction:

```java
new TextFormatter(200).format(
        new TextParser().parse("\uFEFF.large\nHeading"));
```

Actual error includes:

```text
U+FEFF ('zerowidthjoiner') is not available in the font Helvetica, encoding: WinAnsiEncoding
```

Impact: the supplied sample is unaffected, but an otherwise valid input saved
with a UTF-8 signature by an editor fails unexpectedly. The assessment does not
explicitly require BOM handling, so this is lower-priority robustness work.

Recommended correction: consume one optional BOM at the beginning of input,
before command recognition. Do not remove U+FEFF indiscriminately throughout the
document. Add a reader-based regression with BOM-prefixed UTF-8 input and assert
that the heading receives the large font size.

### 3. [P3] Build output is still untracked rather than ignored

Repository root: no `.gitignore` exists or is tracked in the reviewed checkout.
The initial `git status --short` reported `?? target/`.

Impact: every build leaves a noisy working tree and makes accidental inclusion of
compiled classes, copied dependencies and temporary PDFs more likely when using
`git add .`. This is a maintenance issue, not an application failure.

Recommended correction: add a small `.gitignore` covering `target/`, local IDE
metadata and the generated submission ZIP. Continue tracking the intentionally
included `output/result.pdf` example. Verify with `git check-ignore target/`.

## Maintainability assessment

| Area | Assessment |
| --- | --- |
| Responsibilities | Appropriate five-class design: entry point, parser, immutable model, horizontal formatter and PDF/vertical layout. No framework or extra production layer is necessary. |
| Data ownership | `TextBlock` and formatted lines defensively copy their lists; run and fragment values are records. Repeated samples safely reuse immutable paragraphs. |
| Parser | Straightforward command switch, local state per parse, useful line-number errors, checked indentation addition and explicit reader ownership. |
| Formatting | Mixed-font runs survive tokenization and wrapping. Named helpers separate word building, splitting, line construction and measurement. The per-glyph model is warranted by mixed formatting and punctuation attachment. |
| Rendering | Absolute fragment positions preserve calculated spacing. The same configured font sizes are supplied to the generator. Streams, documents and the file reader are closed safely. |
| Pagination | Whole lines move to the next page; font extents include ascenders/descenders; paragraph spacing is distinct from line leading. Oversized lines fail instead of looping or clipping. |
| Build | Java 17 release target, nonstandard source roots correctly configured, pinned compiler/test plugins and dependencies; JUnit is test-scoped. |
| Documentation | Build/run instructions and assumptions describe the implementation accurately, with dated verification and substantial AI contribution disclosed. |

There is some duplicate Helvetica style-to-font selection in `TextFormatter` and
`PdfGenerator`. It is consistent today. If fonts change later, both mappings must
be kept synchronized. This is a future maintenance consideration, not a current
functional finding or a reason to add another abstraction for this assessment.

Likewise, the formatter is the most involved class because it handles geometry,
styling, punctuation and long words together. A wholesale refactor immediately
before submission would add risk without a demonstrated benefit. Prefer the
small targeted corrections above.

## Test assessment and actual results

Fresh command executed from the project directory:

```powershell
mvn -B "-Dpdfbox.fontcache=target" clean test dependency:copy-dependencies "-DincludeScope=runtime"
```

**BUILD SUCCESS: 51 JUnit invocations, 0 failures, 0 errors, 0 skipped.** Totals
were also checked from the generated Surefire XML reports. The nested test
layout can make Surefire's per-class grouping look unusual; the total is 51,
not a claim of 51 separate test methods.

Existing coverage is meaningful:

- Parser defaults, multiline text/whitespace, styles, sizes, paragraph boundaries,
  relative indentation, fill flags, invalid commands, overflow and state reset.
- Proportional-width wrapping, exact fits, equal gap expansion, final-line rules,
  nofill alignment, repeated indentation, inline fonts/sizes and punctuation.
- Long words, empty paragraphs, invalid dimensions, immutable output and fill-mode
  changes inside a paragraph.
- PDF reopen/extraction, A4 geometry, actual font names/sizes/baselines, positioned
  justification, paragraph spacing, empty output, pagination without text loss,
  margin advances and the repeated assessment sample.

Additional review probes, separate from the 51 JUnit invocations:

- **500 deterministic mixed-run checks passed** using seed `20261117`: preserve
  every non-whitespace character, keep fragment advances contiguous and keep
  measured advances within a 150pt content width across styles, sizes,
  indentation changes and fill modes. These are supplementary checks, not a
  coverage percentage or proof of all geometry cases.
- The italic ink-overhang and BOM probes above reproduced the two findings.

The most useful missing regressions are those two cases. Other worthwhile tests
would exercise `Main` directly (default run, a single repetition, invalid counts,
missing/empty input), exact page-height boundaries, and explicit unsupported-font
characters. The current sample integration test reconstructs the pipeline rather
than invoking `Main`, so it does not replace entry-point coverage.

No line/branch coverage percentage was measured. A coverage report, mutation
testing, static-analysis suite and dependency vulnerability audit were not part of
this review; no claim about those checks is made.

## Assessment sample and output verification

The original three-page assessment PDF was read again. `input/sample.txt` matches
its example after normalizing PDF extraction whitespace, the font-encoded
indentation signs and the closing quotation mark. All nine commands and the
default `.nofill` behavior are present. The chosen indentation unit is permitted
by the brief's explicit allowance for alternative widths.

The actual `Main` entry point was executed in an isolated working directory under
`target/review/application/`, with a copy of the real sample. It generated
`target/review/application/output/result.pdf` without changing the tracked PDF.

Both the tracked output and freshly generated output were independently reopened
with pypdf and checked:

- Exactly **3 A4 portrait pages** each.
- Extracted text equals all **10 complete sample copies** after whitespace
  normalization: **6,579 characters**, including the attached comma.
- Every page of the fresh output was rendered with Poppler and visually inspected.
  The assessment sample showed correct indentation, bold/italic text, larger
  headings, justification and paragraph-final alignment, with no observed clipping
  or overlap.
- Paragraph continuation across page boundaries occurs as documented; widow/orphan
  control is not implemented or required by the supplied brief.

Poppler emitted its existing `Symbol` display-font warning, but all characters
used in the sample rendered correctly. The separate italic stress case is the
specific geometry exception documented in finding 1.

Environment: Windows, installed JDK 18.0.2.1, Java 17 compiler release target.
A separate JDK 17 runtime and macOS/Linux execution were not available/performed.
The standard Helvetica fonts' limited Unicode support remains a documented
limitation, rather than a claim of arbitrary Unicode support.

## Submission documentation and release checks

- The prior `docs/REVIEW.md` records the earlier sample review. This report
  supersedes its broader no-new-defect conclusion with the newly reproduced edge
  cases; the earlier sample-output checks remain consistent with this run.
- Page 3 of the brief contains conflicting AI guidance. Existing disclosure is
  transparent about substantial AI-written code. That disclosure cannot establish
  acceptance under the assessor's authorship expectations. This remains an
  assessment-policy question, separate from software correctness.
- `AI_PROMPT_HISTORY.md` is a snapshot ending with the earlier final-submission
  review request. If the complete current prompt history is intended for the final
  release, append subsequent real prompts, including this review request, without
  inventing a candidate thought process. This report was also produced with Codex.
- No submission ZIP was present in the project root during this review. An older
  ZIP is discussed in the previous review document, but its current availability
  and contents were not reverified. Rebuild the final ZIP from an explicit file
  list after any fixes, including this report if useful, and exclude `target`,
  `.git`, IDE files and temporary review artifacts.

## Recommended next steps

1. Correct the P2 italic ink-boundary issue and add a regression that fails on the
   current implementation; rerun the tests and rendered-output checks.
2. Add the small `.gitignore`. Consider BOM support and its regression before
   accepting input files from other editors.
3. If available, verify once on an actual JDK 17 runtime.
4. Refresh dated verification/prompt-history documentation and rebuild the final
   ZIP from the reviewed files. Preserve the AI disclosure.

The assessment example is working and the project has a sound foundation. The
recommended work is targeted correction and release hygiene, not a redesign.
