# Email HTML Validator

Small CLI that inspects HTML snippets or templates and reports which e‑mail clients fully, partially, or never render
the detected tags, attributes, and CSS rules. The validator ships a Can I Email dataset snapshot and produces JSON, XML,
HTML, and Markdown reports per run.

## Features

- Scans inline HTML, files, URLs, or stdin and normalizes everything into a single pass of distinct features.
- Computes weighted support percentages and lists clients with partial or rejected coverage.
- Writes console output plus JSON/XML/HTML/Markdown artifacts (see `--output-dir`).
- GraalVM native builds for Linux/Windows/macOS, backed by Docker multi‑arch images and release workflows.
- Optional BFSG compliance audit (basic accessibility heuristics) via `--bfsg`, with selectable axe-core tag sets.

## Requirements

- Java 21 and Maven 3.9+ (or use the bundled `./mvnw` wrapper).
- GraalVM 21+ with `native-image` for native builds.
- BFSG audits rely on Playwright’s Chromium runtime. Linux hosts need the system libraries documented in
  the [Playwright dependency guide](https://playwright.dev/java/docs/ci#linux-dependencies). A typical installation
  looks like:
  [./mvnw -B -q exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install --with-deps"](https://playwright.dev/java/docs/ci)
  or [npx playwright install-deps](https://playwright.dev/docs/ci)
  or [playwright install --with-deps](https://playwright.dev/python/docs/ci)
  (The published Docker image ships with these libraries preinstalled.)
- If those libraries are unavailable (for example, in slim CI images), run the CLI with `--no-bfsg` and set
  `RUN_BFSG_TESTS=false` so the integration tests skip BFSG assertions.
- Dataset regeneration (`CaniEmailDatasetTest`) expects the upstream markdown export under
  `src/test/resources/caniemail/_features`. Clone [maizzle/caniemail](https://github.com/maizzle/caniemail) when you
  need to refresh it.

## Usage

```
email-html-validator [OPTIONS] <HTML|FILE|URL|-> 
```

| Option                  | Description                                                                              |
|-------------------------|------------------------------------------------------------------------------------------|
| `--output-dir <dir>`    | Persist JSON, XML, HTML, and Markdown reports; defaults to `./reports`                   |
| `--no-bfsg`             | Skip the BFSG compliance audit (enabled by default; uses axe-core + Playwright)          |
| `--bfsg-tags <tag,...>` | Restrict the BFSG audit to comma-separated axe-core tags (e.g., `wcag2aa,best-practice`) |
| `--ignore-slugs <slug,...>` | Ignore specific unknown/partial slugs (defaults to `tag:html,tag:head,tag:body`)        |
| `--help`                | Show usage and exit                                                                      |
| `--github-summary`      | Append the generated Markdown report to `GITHUB_STEP_SUMMARY` (GitHub Actions)           |
| `--playwright-version`  | Print the bundled Playwright Java client version and exit                                |

- Provide inline HTML (`"<table>...</table>"`), a local file path, or an `http(s)` URL as the positional argument.
- Use `-` to read from stdin (e.g., `cat mail.html \| email-html-validator - --output-dir reports`). Passing no source
  prints an error instead of blocking.
- Exit codes: `0` success, `1` input/usage error, `2` runtime failure (network, validator bug, BFSG/Playwright error, etc.).
- To keep reports focused, the CLI ignores `tag:html`, `tag:head`, and `tag:body` by default. Extend the ignore list via
  `--ignore-slugs`/`EHV_IGNORE_SLUGS` when you want to suppress additional slugs.

### Environment Overrides

Every CLI flag can be mirrored through environment variables with the short `EHV_` prefix. These are handy for GitHub
Actions and Docker wrappers:

| Variable         | Effect                                                   |
|------------------|----------------------------------------------------------|
| `EHV_HELP`       | When truthy (`true`, `1`, `yes`), prints usage and exits |
| `EHV_OUTPUT_DIR` | Sets the report directory (defaults to `./reports`)      |
| `EHV_NO_BFSG`    | Truthy values disable the BFSG audit (`--no-bfsg`)       |
| `EHV_BFSG_TAGS`  | Comma-separated axe-core tags (same as `--bfsg-tags`)    |
| `EHV_IGNORE_SLUGS` | Comma-separated slugs to hide (same as `--ignore-slugs`) |
| `EHV_SUMMARY`    | Truthy values enable `--github-summary` behavior         |

Set `PLAYWRIGHT_CLI_DIR` (or `EHV_PLAYWRIGHT_CLI_DIR`) to point at a Playwright CLI installation when you provision the
Node binary and `package/` directory yourself. The validator forwards that path to Playwright so the BFSG audit can reuse
existing toolchains without embedding them into the JAR or native binary.

*Bonus:* set `EHV_BFSG_TAGS=unicorn` to get a whimsical console shout-out. Append `rick=1` to any CLI invocation if you
want the dataset reference to lead somewhere… unexpected.

## Developer Quickstart

```bash
mvn -q test              # run component & dataset tests
mvn -q package           # build the runnable JAR in target/
java -jar target/email-html-validator-*.jar "<html><body><table></table></body></html>"
java -jar target/email-html-validator-*.jar --output-dir reports template.html
cat mail.html | java -jar target/email-html-validator-*.jar - --output-dir reports
```

Reports land in the directory you point to (one per format). Console output also includes dataset metadata, the bundled
Playwright version (same value as `--playwright-version`), and unknown features.

### Preinstalled Playwright CLI

CI environments that already host the Playwright CLI (Node binary + `package/` folder) can slim both the runnable JAR
and GraalVM binary:

1. Extract or install the CLI once (e.g., `npx playwright install` or unpacking the Maven `driver-bundle` for your OS).
2. Point `PLAYWRIGHT_CLI_DIR` or `EHV_PLAYWRIGHT_CLI_DIR` at that directory so the BFSG audit reuses it at runtime.
3. Build with `-Ppreinstalled-playwright` to mark the Playwright driver bundle as `provided`, removing the embedded Node
   runtimes from the shaded JAR and native image.

The default build keeps shipping the driver bundle for portability, but the profile trims ~160 MB from the JAR and drops
hundreds of megabytes from the native image when you're willing to manage the CLI externally.

### GitHub Outputs

When `GITHUB_OUTPUT` is set (as in GitHub Actions), the CLI appends reusable statistics:

| Output        | Description                                           |
|---------------|-------------------------------------------------------|
| `accepted`    | Weighted accepted percentage (string, two decimals)   |
| `partial`     | Weighted partial percentage                           |
| `rejected`    | Weighted rejected percentage                          |
| `unknown`     | Comma-separated unknown feature identifiers           |
| `bfsg_status` | BFSG status (`pass`, `fail`, `error`, or `skipped`)   |
| `bfsg_issues` | Number of BFSG violations in the last run             |
| `report_dir`  | Absolute path to the directory containing all reports |
| `report_json` | Absolute path to `report.json`                        |
| `report_html` | Absolute path to `report.html`                        |
| `report_md`   | Absolute path to `report.md`                          |
| `report_xml`  | Absolute path to `report.xml`                         |
| `summary_md`  | Markdown content written to `GITHUB_STEP_SUMMARY`     |

## Native Builds

- **Local GraalVM:** install GraalVM 21 with `native-image` and run `mvn -q -DskipTests -Pnative package`. The native
  binary appears as `target/email-html-validator.native` (or `.exe` on Windows). Native executables don’t embed
  Chromium, so pass `--no-bfsg` if you want to avoid the inevitable “BFSG compliance: error (Failed to create driver)”
  message.
- **Docker multi‑arch:** `docker build -t email-html-validator-native -f Dockerfile_Native .` validates the binary
  inside the container. Export artifacts with `docker build --target export -f Dockerfile_Native . --output target`.
- **CI release:** `.github/workflows/github_release.yml` creates JARs plus native executables for Linux (x64/ARM64 via
  Docker), macOS, and Windows. Each binary is executed against a fixture during the build to ensure it works before
  publishing.

## BFSG Compliance (axe-core)

- Uses `com.deque.html.axe-core:playwright` plus `com.microsoft.playwright` to launch headless Chromium, inject
  `axe.min.js`, and report violations inline. The first run downloads the Playwright browsers (~120 MB) into
  `~/.cache/ms-playwright/` (respect `PLAYWRIGHT_BROWSERS_PATH` if you want a custom cache location).
- By default, the CLI runs the entire axe-core rule catalog. Pass `--bfsg-tags` with comma-separated identifiers (e.g.,
  `wcag2a,wcag2aa,best-practice`) to limit the audit to specific tags. Pair it with `--no-bfsg` when the Playwright
  runtime is unavailable (for example, in restricted native builds).

## Continuous Delivery

- `.github/workflows/test_workflow.yml` runs on pushes/PRs, executes `mvn test`, builds the CLI, and validates a quick
  sample run of the JAR.
- `.github/workflows/github_release.yml` can be triggered manually or from other workflows to build signed artifacts and
  publish them as GitHub Releases.

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

# Run BFSG audit against specific axe-core tags
java -jar target/email-html-validator.jar --bfsg-tags wcag2a,wcag2aa "<html><body><img src='hero.png'></body></html>"
```

The CLI exits with `1` for option/source errors and `2` for runtime failures, which the tests and workflows assert to
keep the UX predictable.
