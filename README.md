# Email HTML Validator

Small CLI that inspects HTML snippets or templates and reports which e‑mail clients fully, partially, or never render the detected tags, attributes, and CSS rules. The validator ships a Can I Email dataset snapshot and produces JSON, XML, HTML, and Markdown reports per run.

## Features

- Scans inline HTML, files, URLs, or stdin and normalizes everything into a single pass of distinct features.
- Computes weighted support percentages and lists clients with partial or rejected coverage.
- Writes console output plus JSON/XML/HTML/Markdown artifacts (see `--output-dir`).
- GraalVM native builds for Linux/Windows/macOS, backed by Docker multi‑arch images and release workflows.
- Optional BFSG compliance audit (basic accessibility heuristics) via `--bfsg`.

## Quickstart

```bash
mvn -q test              # run component & dataset tests
mvn -q package           # build the runnable JAR in target/
java -jar target/email-html-validator-*.jar "<html><body><table></table></body></html>"
java -jar target/email-html-validator-*.jar --output-dir reports template.html
cat mail.html | java -jar target/email-html-validator-*.jar - --output-dir reports
```

Reports land in the directory you point to (one per format). Console output also includes dataset metadata and unknown features.

## Usage

```
email-html-validator [OPTIONS] <HTML|FILE|URL|-> 
```

| Option | Description |
| --- | --- |
| `--output-dir <dir>` | Persist JSON, XML, HTML, and Markdown reports into `<dir>` |
| `--no-bfsg` | Skip the BFSG compliance audit (enabled by default; uses axe-core + Playwright) |
| `--help` | Show usage and exit |

- Provide inline HTML (`"<table>...</table>"`), a local file path, or an `http(s)` URL as the positional argument.
- Use `-` to read from stdin (e.g., `cat mail.html \| email-html-validator - --output-dir reports`). Passing no source prints an error instead of blocking.
- Exit codes: `0` success, `1` input/usage error, `2` runtime failure (network, validator bug, etc.).

## Native Builds

- **Local GraalVM:** install GraalVM 21 with `native-image` and run `mvn -q -DskipTests -Pnative package`. The native binary appears as `target/email-html-validator.native` (or `.exe` on Windows). Native executables don’t embed Chromium, so pass `--no-bfsg` if you want to avoid the inevitable “BFSG compliance: error (Failed to create driver)” message.
- **Docker multi‑arch:** `docker build -t email-html-validator-native -f Dockerfile_Native .` validates the binary inside the container. Export artifacts with `docker build --target export -f Dockerfile_Native . --output target`.
- **CI release:** `.github/workflows/github_release.yml` creates JARs plus native executables for Linux (x64/ARM64 via Docker), macOS, and Windows. Each binary is executed against a fixture during the build to ensure it works before publishing.

## BFSG Compliance (axe-core)

- Uses `com.deque.html.axe-core:playwright` plus `com.microsoft.playwright` to launch headless Chromium, inject `axe.min.js`, and report violations inline. The first run downloads the Playwright browsers (~120 MB) into `~/.cache/ms-playwright/` (respect `PLAYWRIGHT_BROWSERS_PATH` if you want a custom cache location).
- The CLI currently runs axe-core with its default settings. The underlying `AxeBuilder` exposes further knobs—`withTags(...)`, `withRules(...)`, `disableRules(...)`, `include(...)`, `exclude(...)`, `withOptions(AxeRunOptions)`—should you decide to surface more CLI flags later.

## Continuous Delivery

- `.github/workflows/test_workflow.yml` runs on pushes/PRs, executes `mvn test`, builds the CLI, and validates a quick sample run of the JAR.
- `.github/workflows/github_release.yml` can be triggered manually or from other workflows to build signed artifacts and publish them as GitHub Releases.

## CLI Tips

```bash
# Validate remote templates
java -jar target/email-html-validator-*.jar --output-dir reports https://example.com/email.html

# Read from stdin
cat snippet.html | java -jar target/email-html-validator-*.jar - --output-dir reports/out

# Native binary (Linux example)
./target/email-html-validator.native sample.html --no-bfsg --output-dir reports/native

# Run BFSG audit
java -jar target/email-html-validator.jar --bfsg "<html><body><img src='hero.png'></body></html>"
```

The CLI exits with `1` for option/source errors and `2` for runtime failures, which the tests and workflows assert to keep the UX predictable.
