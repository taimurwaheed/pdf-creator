# Assumptions and limitations

## Input and commands

- Input is UTF-8. Commands occupy their own lines; surrounding command whitespace
  is ignored. Unsupported commands and invalid arguments fail with line numbers.
- All nine assessment commands are supported. Defaults are regular font, normal
  size, zero indentation and `.nofill`. Formatting persists across paragraphs.
- `.indent N` is a relative integer change, including signed values. One logical
  unit is 18 points; negative totals are rejected. The brief permits indentation
  widths other than approximately `WWWW`.
- `.paragraph` starts a new paragraph. Repeated paragraph commands do not insert
  extra empty paragraphs; blank input lines are whitespace, not paragraph commands.
- Source line breaks and tabs become spaces during formatting, including in
  `.nofill`. Internal spaces are retained; formatted line edges are trimmed.
- Closing punctuation attaches to the preceding word, even when commands or
  whitespace separate it in the input. This supports the example's leading comma.
- Indentation and fill-mode changes start a fresh line. Their preceding line is
  left-aligned. Font style and size changes can occur within a line.
- Repetition uses the already-parsed example, so each copy has the same formatting
  state and explicit paragraph boundaries as the original example.

## Layout

- A4 portrait with 50-point margins, Helvetica regular/bold/oblique, 12-point normal
  text and 16-point large text. `.regular` changes style independently of size.
- Wrapping uses PDFBox font metrics. `.fill` expands inter-word gaps on wrapped
  lines; paragraph-final lines and `.nofill` lines remain left-aligned.
- A line with only one word has no inter-word gap to expand and remains left-aligned.
- Overlong words split at Unicode code-point boundaries without hyphens. Closing
  punctuation stays with a preceding character where that group can fit.
- Each line's vertical bounds use the largest relevant font extents, with leading
  equal to 20% of its largest font size. Paragraphs add 8 points of spacing. A line
  that will not fit moves whole to the next page; no extra paragraph gap is carried
  to the page top.

## Known limitations and trade-offs

- The standard Helvetica fonts are not embedded and do not support arbitrary
  Unicode, emoji or complex scripts. Unsupported characters fail explicitly.
- Very narrow widths, excessive indentation or a glyph/punctuation group that
  cannot fit cause an error rather than clipping. Lines taller than a page are
  rejected.
- Pagination has no widow/orphan control or heading keep-with-next rule; paragraphs
  can continue across pages. The brief does not require these features.
- The text/line model and PDF are held in memory. The application is intended for
  assessment-sized input, not very large files or very large repetition counts.
- Filenames and page geometry are fixed for this assessment. Errors propagate to
  the command line rather than using an additional error-handling framework.
- The PDF is not tagged for accessibility and is not a PDF/A archival document.
  Neither is requested by the brief.
- Automated verification used the installed JDK 18 with `--release 17`; a separate
  run on a JDK 17 runtime has not been performed.

These describe the implemented behavior and trade-offs. They are not a
reconstruction of the candidate's personal reasoning.
