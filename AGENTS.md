# Repository Guidelines

## Project Structure & Module Organization
CLI wiring, validation flow, and dataset plumbing reside under `src/main/java/org/nanonative`: `cli` (argument parsing, report export), `validation` (HTML parser, weighting, BFSG audit), and `caniemail` (static dataset + metadata). Support files stay in `src/main/resources`, including the Can I Email JSON and GraalVM configs. Tests mirror this tree in `src/test/java`, with fixtures in `src/test/resources`. Maven outputs (`target/`, native binaries, Playwright cache markers) should remain untracked.

## Build, Test, and Development Commands
- `mvn clean package` — compile, run the suite, and create `target/email-html-validator.jar`.
- `java -jar target/email-html-validator.jar "<html>…</html>" --output-dir reports` — validate strings, files, URLs, or `-` (stdin); defaults to writing reports under `./reports`, pass `--no-bfsg` to skip, `--bfsg-tags wcag2aa,best-practice` to limit axe-core, and `--github-summary` to mirror the Markdown report into GitHub summaries. The same options can be provided via `EHV_*` environment variables (e.g., `EHV_NO_BFSG=true`, `EHV_SUMMARY=true`), and when `GITHUB_OUTPUT` is available the CLI emits accepted/partial/rejected percentages, BFSG status/count, and report paths.
- `mvn test` — executes `EmailHtmlValidatorCliBlackBoxTest`, which spins up per-test temp folders, asserts console output, and inspects JSON/HTML/Markdown/XML artifacts.
- `mvn -q -DskipTests -Pnative clean package` — build the GraalVM binary (`target/email-html-validator.native`); ensure GraalVM 21+ plus `native-image` is installed.
- Set `PLAYWRIGHT_BROWSERS_PATH` so BFSG downloads happen once in CI; otherwise Playwright populates `~/.cache/ms-playwright`.

## Coding Style & Naming Conventions
Java 21, 4-space indent, and functional composition over inheritance. Favor `TypeMap` for report payloads, avoid `null` returns, and keep helpers static and side-effect free. Tokens use `type:value` (e.g., `tag:body`, `css:at-media`). Introduce new CLI options via constants in `EmailHtmlValidatorCli` and document them in `README.md`.

## Testing Guidelines
`EmailHtmlValidatorCliBlackBoxTest` is the single high-level test harness: each method provisions a unique report directory (`@BeforeAll`/`@AfterAll` handle lifecycle), invokes the CLI, and reads JSON to validate weighted percentages, BFSG results, and dataset metadata. Prefer AssertJ and actual HTTP/file IO instead of mocks. Add new fixtures under `src/test/resources/cli/`. Keep tests deterministic—seed clocks and avoid relying on remote services.

## Commit & Pull Request Guidelines
Use Conventional Commits (`feat:`, `fix:`, etc.) and describe the feature, dataset revision, or CLI behavior change. Every PR should list the Maven or native-image commands executed, attach sample report excerpts when formatting shifts, and link issues. Prototype in `feature/*-spike` branches, then squash or cherry-pick into stable branches once reviewed. Clean up temporary report folders before pushing to keep diffs focused.
