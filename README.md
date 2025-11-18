# Email HTML Validator

[![Issues][issues_shield]][issues_link]
[![Commit][commit_shield]][commit_link]
[![License][license_shield]][license_link]
[![Central][central_shield]][central_link]
[![Tag][tag_shield]][tag_link]
[![Javadoc][javadoc_shield]][javadoc_link]
[![Size][size_shield]][size_shield]
![Label][java_version]

Validate newsletter templates the fast way. Point this tool at inline HTML, files, stdin, or URLs and it will tell you
which features are accepted, partial, rejected, or unknown across the Can I Email dataset. Optional BFSG audits add a
thin accessibility sanity check powered by Playwright + axe-core.

## Choose Your Interface

Pick the runtime that matches your workflow:

| Option                              | When to use it                                     |
|-------------------------------------|----------------------------------------------------|
| **CLI**                             | Quick local checks; plug into any shell script     |
| **Java DSL**                        | Keep validations inside JVM tests or build plugins |
| **GitHub Action**                   | Validate pull requests with zero scripting         |
| **Maven Central / GitHub Packages** | Import as a library and embed the validator        |
| **Docker image**                    | Containerized runs or CI systems without Java      |

Each interface shares the same reporting core and produces JSON, XML, HTML, and Markdown artifacts.

---

## CLI

```bash
./email-html-validator [OPTIONS] <HTML | FILE | URL | ->
```

Grab the latest binary from the [Releases page](https://github.com/YunaBraska/email-html-validator/releases) or build it
yourself via `./mvnw package`. We publish:

- `email-html-validator.jar` (portable JAR for any Java 21+ runtime)
- `email-html-validator.native` (Linux x64, Linux arm64, macOS x64/arm64, Windows x64 via GraalVM)
- Docker image `ghcr.io/yunabraska/email-html-validator:<tag>`

| Flag                           | Description                                                                        |
|--------------------------------|------------------------------------------------------------------------------------|
| `--output-dir <dir>`           | Folder for JSON/XML/HTML/Markdown reports (default `./reports`)                    |
| `--no-bfsg`                    | Skip the BFSG accessibility audit                                                  |
| `--bfsg-tags <tag,...>`        | Limit BFSG rules to comma-separated axe-core tags (e.g., `wcag2aa,best-practice`)  |
| `--ignore-features <slug,...>` | Suppress noisy feature slugs (`tag:html`,`tag:head`,`tag:body` ignored by default) |
| `--github-summary`             | Mirror the Markdown report to `GITHUB_STEP_SUMMARY`                                |
| `--playwright-version`         | Print the bundled Playwright Java version and exit                                 |

- Provide inline HTML, a path, an `http(s)` URL, or `-` for stdin.
- Exit codes: `0` success, `1` usage/source error, `2` runtime/Playwright/BFSG failure.
- Environment twins (e.g., `EHV_OUTPUT_DIR`, `EHV_NO_BFSG`, `EHV_BFSG_TAGS`, `EHV_IGNORE_FEATURES`, `EHV_SUMMARY`) let you
  drive the CLI from containers and Actions without long flag lists.

### Example

```bash
# JAR (all platforms)
java -jar email-html-validator.jar --output-dir reports "<html><body><table></table></body></html>"

# Native binary (Linux/macOS)
./email-html-validator.native template.html --no-bfsg --output-dir reports/native

# Windows
email-html-validator.exe template.html --bfsg

# Stream from stdin
cat template.html | ./email-html-validator.native - --no-bfsg
```

---

## Java DSL

Embed validations inside tests or build plugins via `EmailHtmlValidatorRequest`:

```java
import berlin.yuna.ehv.validation.EmailValidator;
import java.nio.file.Path;
import java.util.List;

var report = EmailValidator.html("<table data-hero></table>")
    .bfsg(true)
    .bfsgTags(List.of("wcag2aa", "best-practice"))
    .ignoreFeatures(List.of("attribute:data-hero"))
    .outputDirectory(Path.of("build/reports/email"))
    .run();

var accepted = report.asBigDecimal("accepted");
```

- The `run()` call returns a `TypeMap` containing the same data as the CLI JSON report.
- Call `disableReportExport()` if you only want the in-memory payload.

Add the dependency from Maven Central (or GitHub Packages):

```xml
<dependency>
  <groupId>berlin.yuna</groupId>
  <artifactId>email-html-validator</artifactId>
  <version>${email-html-validator.version}</version>
</dependency>
```

---

## GitHub Action

Drop the Action into any workflow to fail builds when coverage falls short:

```yaml
- name: "Validate HTML"
  uses: YunaBraska/email-html-validator@main
  with:
    source: ${{ github.workspace }}/newsletter.html
    output_dir: reports/ci
    bfsg_tags: wcag2aa,best-practice
    github_summary: true
```

Outputs (`accepted`, `partial`, `rejected`, `bfsg_status`, `report_json`, etc.) can feed follow-up steps. Provide `source`
as inline HTML, a repo path, or `-` for stdin.

---

## GitHub Packages / Maven Central

Prefer to download from GitHub Packages? Add the repository plus dependency:

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/YunaBraska/email-html-validator</url>
  </repository>
</repositories>

<dependency>
  <groupId>berlin.yuna</groupId>
  <artifactId>email-html-validator</artifactId>
  <version>${email-html-validator.version}</version>
</dependency>
```

Use your `GITHUB_TOKEN` (or a PAT) as the repo credential. The artifact exposes the same API used by the DSL example.

---

## Docker Image

Run validations without installing Java:

```bash
docker run --rm \
  -v "$PWD/reports:/reports" \
  ghcr.io/yunabraska/email-html-validator:latest \
  --output-dir /reports "<title>Ship it</title>"
```

- Mount a host folder to collect the reports.
- Add `--no-bfsg` if Playwright browsers are unavailable inside your environment.
- Set `PLAYWRIGHT_CLI_DIR` or `EHV_*` variables with `-e` to customize behavior.

---

## Reports & Outputs

Every interface writes the same artifacts:

| File          | Purpose                                                      |
|---------------|--------------------------------------------------------------|
| `report.json` | Raw TypeMap serialized for automation                        |
| `report.xml`  | Deterministic XML for XPath/XSLT pipelines                   |
| `report.html` | Shareable human report with percentages, notes, BFSG results |
| `report.md`   | Markdown twin; appended to GitHub summaries when requested   |

`unknownFeatures`, `ignoredFeatures`, BFSG issue counts, and partial client lists appear across all formats. The CLI and
Action also emit quick stats to `GITHUB_OUTPUT` (`accepted`, `partial`, `rejected`, `bfsg_status`, etc.).

Each JSON/XML/TypeMap payload contains:

- `accepted`, `partial`, `rejected` – weighted percentages
- `partialNotes`, `unknownFeatures`, `ignoredFeatures`, `ignoredFeatureCount`
- `partialClients`, `rejectedClients`, `featureCount`, `clientCount`, `operatingSystemCount`
- `bfsgStatus`, `bfsgIssueCount`, `bfsgIssues` (when BFSG is enabled)
- `playwrightVersion`, `caniemailUrl`, and the exported file paths

---

## Playwright & BFSG Requirements

- BFSG checks launch headless Chromium through the Playwright Java bindings shipped with the tool.
- Linux hosts need the dependencies listed in the [Playwright CI guide](https://playwright.dev/java/docs/ci#linux-dependencies).
  When that’s not feasible, run the validator with `--no-bfsg` or `EHV_NO_BFSG=true`.
- Set `PLAYWRIGHT_CLI_DIR`/`EHV_PLAYWRIGHT_CLI_DIR` when you install the Playwright driver bundle yourself (for example, in
  multi-stage Docker builds). The CLI exposes `--playwright-version` so you can fetch matching artifacts.

---

## Developer Notes

Want to contribute or run the full suite locally?

- `./mvnw clean verify` – build the JAR, run CLI black-box tests, regenerate fixtures.
- `./mvnw -q -DskipTests -Pnative package` – build a GraalVM binary (`target/email-html-validator.native`).
- `Dockerfile` and `Dockerfile_Native` ship the CLI/native images used for releases.
- BFSG tests expect Playwright browsers. Install them once via
  `./mvnw -B -q exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium --with-deps"` or set
  `PLAYWRIGHT_BROWSERS_PATH` to a cached location.
- The Can I Email dataset snapshot lives under `src/main/resources/caniemail`. To regenerate, place upstream markdown files
  in `src/test/resources/caniemail/_features` and run `CaniEmailDatasetTest`.

Questions, bugs, or feature ideas? Open an issue and mention how you run the validator (CLI, Action, Docker, etc.) so we
can reproduce quickly.

[build_shield]: https://github.com/YunaBraska/email-html-validator/workflows/MVN_RELEASE/badge.svg
[build_link]: https://github.com/YunaBraska/email-html-validator/actions?query=workflow%3AMVN_RELEASE
[maintainable_shield]: https://img.shields.io/codeclimate/maintainability/YunaBraska/email-html-validator?style=flat-square
[maintainable_link]: https://codeclimate.com/github/YunaBraska/email-html-validator/maintainability
[coverage_shield]: https://img.shields.io/codeclimate/coverage/YunaBraska/email-html-validator?style=flat-square
[coverage_link]: https://codeclimate.com/github/YunaBraska/email-html-validator/test_coverage
[issues_shield]: https://img.shields.io/github/issues/YunaBraska/email-html-validator?style=flat-square
[issues_link]: https://github.com/YunaBraska/email-html-validator/issues/new/choose
[commit_shield]: https://img.shields.io/github/last-commit/YunaBraska/email-html-validator?style=flat-square
[commit_link]: https://github.com/YunaBraska/email-html-validator/commits
[license_shield]: https://img.shields.io/github/license/YunaBraska/email-html-validator?style=flat-square
[license_link]: https://github.com/YunaBraska/email-html-validator/blob/main/LICENSE
[central_shield]: https://img.shields.io/maven-central/v/berlin.yuna/email-html-validator?style=flat-square
[central_link]: https://central.sonatype.com/artifact/berlin.yuna/email-html-validator
[tag_shield]: https://img.shields.io/github/v/tag/YunaBraska/email-html-validator?style=flat-square
[tag_link]: https://github.com/YunaBraska/email-html-validator/releases
[javadoc_shield]: https://img.shields.io/badge/javadoc-online-green?style=flat-square
[javadoc_link]: https://javadoc.io/doc/berlin.yuna/email-html-validator
[size_shield]: https://img.shields.io/github/repo-size/YunaBraska/email-html-validator?style=flat-square
[java_version]: https://img.shields.io/badge/Java-21-red?style=flat-square
