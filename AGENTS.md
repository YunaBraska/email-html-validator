# Repository Guidelines

## Project Structure & Module Organization
Cli glue, validator logic, and dataset utilities live under `src/main/java/org/nanonative` in three small packages: `cli` (entry point + report export), `validation` (feature scanning and scoring), and `caniemail` (dataset loader). Static resources for Can I Email stay inside `src/main/resources/caniemail`. Tests mirror this layout under `src/test/java`, while golden fixtures (HTML snippets, expected reports) are parked in `src/test/resources`. Maven writes all build artifacts to `target/`; keep that folder ignored and clean before diffing.

## Build, Test, and Development Commands
- `mvn clean package` — compile with Java 21, run the entire suite, and produce `target/email-html-validator.jar`.
- `java -jar target/email-html-validator.jar "<html><body>…</body></html>" --output-dir reports` — validate inline HTML, file paths, URLs, or `-` for stdin; CLI exits 1 when the source is missing and 2 on runtime failures.
- `cat email.html | java -jar target/email-html-validator.jar - --output-dir reports` — handy for piping templates from other tools.
- `mvn -q -DskipTests -Pnative clean package` — build the GraalVM binary (`target/email-html-validator.native` / `.exe`); requires GraalVM 21+ with `native-image`.
- `mvn test` — execute `EmailHtmlValidatorCliBlackBoxTest` plus dataset integrity checks; use `-Dtest=EmailHtmlValidatorCliBlackBoxTest` to iterate faster.
- BFSG compliance runs by default via axe-core + Playwright and adds `bfsgStatus`, `bfsgIssueCount`, and `bfsgIssues` to every report; pass `--no-bfsg` when you need to skip the accessibility audit (especially when running the GraalVM native binary, which cannot start Playwright’s Chromium runtime).
- The first BFSG run downloads the Playwright browsers (~120 MB) into cache. Set `PLAYWRIGHT_BROWSERS_PATH` if you want CI jobs to reuse the same download location.

## Coding Style & Naming Conventions
Use 4-space indentation, Java 21 language level, and immutable data (records, `TypeMap`) wherever possible. Stick to pure, static helpers: no shared state, no reflection, and keep methods focused on a single transformation. Tokens follow the `type:value` convention (`tag:table`, `css:at-media`). Public APIs should never return `null`; prefer empty collections or deterministic defaults. When adding CLI switches, expose them via constants and document them in `EmailHtmlValidatorCli`.

## Testing Guidelines
Tests favor end-to-end coverage through `EmailHtmlValidatorCliBlackBoxTest`: each case wires input → CLI → exported reports, creates a unique temp folder, and asserts console plus JSON output. Use AssertJ for fluent checks, and rely on real HTTP servers (Sun `HttpServer`) instead of mocks when remote access is required. Keep fixtures deterministic, seed any randomness, and clean temp directories in `@AfterAll`. Component-level verifications belong in dataset tests if the JSON schema changes.

## Commit & Pull Request Guidelines
Follow Conventional Commits (e.g., `feat: add yahoo android weighting`) and include context about dataset refreshes or CLI changes in the body. Every PR should mention the CLI commands or Maven goals executed, attach updated sample reports when output formatting changes, and link the relevant issue or spike. Never merge spike branches into `main`; rebase into a clean feature branch once the prototype graduates. Keep diffs flat—avoid sprawling utility classes and prefer `TypeMap` transformations over new OOP hierarchies.
