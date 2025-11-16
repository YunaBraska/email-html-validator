# Repository Guidelines

## Project Structure & Module Organization
Cli glue, validator logic, and dataset utilities live under `src/main/java/org/nanonative` in three small packages: `cli` (entry point + report export), `validation` (feature scanning and scoring), and `caniemail` (dataset loader). Static resources for Can I Email stay inside `src/main/resources/caniemail`. Tests mirror this layout under `src/test/java`, while golden fixtures (HTML snippets, expected reports) are parked in `src/test/resources`. Maven writes all build artifacts to `target/`; keep that folder ignored and clean before diffing.

## Build, Test, and Development Commands
- `mvn clean package` — compile with Java 21, run the entire suite, and produce the runnable JAR inside `target/`.
- `java -jar target/email-html-validator-0.1.0-SNAPSHOT.jar --output-dir reports sample.html` — validate a file and persist JSON/XML/HTML/Markdown artifacts.
- `cat email.html | java -jar target/email-html-validator-0.1.0-SNAPSHOT.jar -` — stream stdin input for ad‑hoc snippets.
- `mvn -DskipTests native:compile` — build the GraalVM binary; requires `native-image` installed locally.
- `mvn test` — execute `EmailHtmlValidatorCliBlackBoxTest` plus dataset integrity checks; use `-Dtest=EmailHtmlValidatorCliBlackBoxTest` to iterate faster.

## Coding Style & Naming Conventions
Use 4-space indentation, Java 21 language level, and immutable data (records, `TypeMap`) wherever possible. Stick to pure, static helpers: no shared state, no reflection, and keep methods focused on a single transformation. Tokens follow the `type:value` convention (`tag:table`, `css:at-media`). Public APIs should never return `null`; prefer empty collections or deterministic defaults. When adding CLI switches, expose them via constants and document them in `EmailHtmlValidatorCli`.

## Testing Guidelines
Tests favor end-to-end coverage through `EmailHtmlValidatorCliBlackBoxTest`: each case wires input → CLI → exported reports, creates a unique temp folder, and asserts console plus JSON output. Use AssertJ for fluent checks, and rely on real HTTP servers (Sun `HttpServer`) instead of mocks when remote access is required. Keep fixtures deterministic, seed any randomness, and clean temp directories in `@AfterAll`. Component-level verifications belong in dataset tests if the JSON schema changes.

## Commit & Pull Request Guidelines
Follow Conventional Commits (e.g., `feat: add yahoo android weighting`) and include context about dataset refreshes or CLI changes in the body. Every PR should mention the CLI commands or Maven goals executed, attach updated sample reports when output formatting changes, and link the relevant issue or spike. Never merge spike branches into `main`; rebase into a clean feature branch once the prototype graduates. Keep diffs flat—avoid sprawling utility classes and prefer `TypeMap` transformations over new OOP hierarchies.
