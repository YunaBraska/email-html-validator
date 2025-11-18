package org.nanonative.cli;

import berlin.yuna.typemap.model.TypeMap;
import com.microsoft.playwright.Playwright;
import org.nanonative.validation.HtmlValidator;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public class EmailHtmlValidatorCli {

    public static final String OPTION_HELP = "--help";
    public static final String OPTION_SOURCE = "source";
    public static final String OPTION_OUTPUT = "--output-dir";
    public static final String OPTION_BFSG = "--bfsg";
    public static final String OPTION_BFSG_TAGS = "--bfsg-tags";
    public static final String OPTION_RICK_ROLL = "rick=";
    public static final String OPTION_SUMMARY = "--github-summary";
    public static final String OPTION_PLAYWRIGHT_VERSION = "--playwright-version";
    public static final String PLAYWRIGHT_VERSION = resolvePlaywrightVersion();
    private static final String OPTION_IGNORE_SLUGS = "--ignore-slugs";
    private static final String DEFAULT_OUTPUT_DIR = "reports";
    private static final String ENV_PREFIX = "EHV_";
    private static final String ENV_HELP = ENV_PREFIX + "HELP";
    private static final String ENV_OUTPUT_DIR = ENV_PREFIX + "OUTPUT_DIR";
    private static final String ENV_NO_BFSG = ENV_PREFIX + "NO_BFSG";
    private static final String ENV_BFSG_TAGS = ENV_PREFIX + "BFSG_TAGS";
    private static final String ENV_SUMMARY = ENV_PREFIX + "SUMMARY";
    private static final String ENV_GITHUB_OUTPUT = "GITHUB_OUTPUT";
    private static final String ENV_IGNORE_SLUGS = ENV_PREFIX + "IGNORE_SLUGS";
    private static final String UNICORN_TAG = "unicorn";
    private static final String RICK_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    private EmailHtmlValidatorCli() {
    }

    public static void main(final String[] args) {
        int status = execute(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    static int execute(final String[] args, final PrintStream out, final PrintStream err) {
        return execute(args, System.getenv(), out, err);
    }

    static int execute(final String[] args, final Map<String, String> environment, final PrintStream out, final PrintStream err) {
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        try {
            var options = parseOptions(args, environment);
            if (Boolean.TRUE.equals(options.asBooleanOpt(OPTION_HELP).orElse(false))) {
                printUsage(out);
                return 0;
            }
            if (Boolean.TRUE.equals(options.asBooleanOpt(OPTION_PLAYWRIGHT_VERSION).orElse(false))) {
                out.println("Playwright version: " + PLAYWRIGHT_VERSION);
                return 0;
            }

            var source = options.asStringOpt(OPTION_SOURCE)
                .orElseThrow(() -> new IllegalArgumentException("Missing HTML source. Provide inline HTML, '-', file path, or URL."));
            var html = HtmlSourceLoader.load(source);
            boolean runBfsg = options.asBooleanOpt(OPTION_BFSG).orElse(false);
            var rawTags = options.containsKey(OPTION_BFSG_TAGS)
                ? options.asList(String.class, OPTION_BFSG_TAGS)
                : List.<String>of();
            boolean unicornMode = isUnicornMode(rawTags);
            var bfsgTags = unicornMode ? List.<String>of() : rawTags;
            TypeMap report = HtmlValidator.validate(html, runBfsg, bfsgTags);
            report.put(HtmlValidator.FIELD_PLAYWRIGHT_VERSION, PLAYWRIGHT_VERSION);
            var ignoredSlugs = new ArrayList<>(HtmlValidator.DEFAULT_IGNORED_SLUGS);
            if (options.containsKey(OPTION_IGNORE_SLUGS)) {
                ignoredSlugs.addAll(options.asList(String.class, OPTION_IGNORE_SLUGS));
            }
            HtmlValidator.ignoreSlugs(report, ignoredSlugs);
            if (unicornMode) {
                out.println("🦄 Weighted coverage certified by Unicorn Labs.");
            }
            if (options.asBooleanOpt(OPTION_RICK_ROLL).orElse(false)) {
                report.put(HtmlValidator.FIELD_REFERENCE_URL, RICK_URL);
                out.println("♪ Never gonna give you up.");
            }
            ReportExporter.printConsole(report, out);

            var outputPath = options.asStringOpt(OPTION_OUTPUT)
                .filter(path -> !path.isBlank())
                .map(Path::of)
                .orElse(Path.of(DEFAULT_OUTPUT_DIR));
            new ReportExporter(outputPath).export(report);
            writeGithubOutputs(report, outputPath, environment);
            if (Boolean.TRUE.equals(options.asBooleanOpt(OPTION_SUMMARY).orElse(false))) {
                writeGithubSummary(outputPath, environment);
            }
            return exitCodeForReport(report);
        } catch (IllegalArgumentException exception) {
            err.println("Input error: " + exception.getMessage());
            return 1;
        } catch (RuntimeException exception) {
            err.println("Unable to validate HTML: " + exception.getMessage());
            return 2;
        }
    }

    static TypeMap parseOptions(final String[] args) {
        return parseOptions(args, System.getenv());
    }

    static TypeMap parseOptions(final String[] args, final Map<String, String> environment) {
        var options = new TypeMap()
            .putR(OPTION_HELP, false)
            .putR(OPTION_BFSG, true)
            .putR(OPTION_RICK_ROLL, false)
            .putR(OPTION_SUMMARY, false)
            .putR(OPTION_PLAYWRIGHT_VERSION, false);
        applyEnvironment(options, environment);
        var builder = new StringBuilder();
        if (args != null) {
            for (int index = 0; index < args.length; index++) {
                var arg = args[index];
                if (arg == null) {
                    continue;
                }
                switch (arg) {
                    case "-h", OPTION_HELP -> {
                        options.put(OPTION_HELP, true);
                        continue;
                    }
                    case OPTION_PLAYWRIGHT_VERSION -> {
                        options.put(OPTION_PLAYWRIGHT_VERSION, true);
                        continue;
                    }
                    case "-o", OPTION_OUTPUT -> {
                        if (index + 1 >= args.length) {
                            throw new IllegalArgumentException("Missing path for " + arg);
                        }
                        var dir = args[++index];
                        if (dir == null || dir.isBlank()) {
                            throw new IllegalArgumentException("Output directory cannot be blank");
                        }
                        options.put(OPTION_OUTPUT, dir);
                        continue;
                    }
                    case OPTION_BFSG -> {
                        options.put(OPTION_BFSG, true);
                        continue;
                    }
                    case "--no-bfsg" -> {
                        options.put(OPTION_BFSG, false);
                        continue;
                    }
                    case OPTION_BFSG_TAGS -> {
                        if (index + 1 >= args.length)
                            throw new IllegalArgumentException("Missing tag list for " + arg);
                        var tagArg = args[++index];
                        options.put(OPTION_BFSG_TAGS, parseBfsgTags(tagArg));
                        continue;
                    }
                    case OPTION_SUMMARY -> {
                        options.put(OPTION_SUMMARY, true);
                        continue;
                    }
                    case OPTION_IGNORE_SLUGS -> {
                        if (index + 1 >= args.length) {
                            throw new IllegalArgumentException("Missing slug list for " + arg);
                        }
                        options.put(OPTION_IGNORE_SLUGS, parseIgnoreSlugs(args[++index]));
                        continue;
                    }
                }
                var lower = arg.toLowerCase(Locale.ROOT);
                if (lower.startsWith(OPTION_RICK_ROLL)) {
                    var value = arg.substring(arg.indexOf('=') + 1);
                    if (isTruthy(value))
                        options.put(OPTION_RICK_ROLL, true);
                    continue;
                }
                if (!builder.isEmpty()) {
                    builder.append(' ');
                }
                builder.append(arg);
            }
        }
        if (!builder.isEmpty()) {
            options.put(OPTION_SOURCE, builder.toString());
        }
        if (!options.containsKey(OPTION_OUTPUT) || options.asStringOpt(OPTION_OUTPUT).isEmpty()) {
            options.put(OPTION_OUTPUT, DEFAULT_OUTPUT_DIR);
        }
        return options;
    }

    private static void printUsage(final PrintStream out) {
        out.println("Email HTML Validator CLI");
        out.println("Usage:");
        out.println("  java -jar email-html-validator.jar [HTML|FILE|URL]");
        out.println("  cat template.html | java -jar email-html-validator.jar");
        out.println();
        out.println("Options:");
        out.println("  --help                 Show this help message");
        out.println("  --output-dir <dir>     Persist JSON/XML/HTML/Markdown artifacts");
        out.println("  --no-bfsg              Skip BFSG compliance checks (enabled by default)");
        out.println("  --bfsg-tags <tag,...>  Limit the BFSG audit to specific axe-core tags (e.g., wcag2aa,best-practice)");
        out.println("  --github-summary       Append the Markdown report to GITHUB_STEP_SUMMARY");
        out.println("  --playwright-version   Print the bundled Playwright version and exit");
        out.println();
        out.println("Provide exactly one HTML source: inline HTML, a file path, an HTTP(S) URL, or pipe through stdin.");
    }

    private static String resolvePlaywrightVersion() {
        var resource = "META-INF/maven/com.microsoft.playwright/playwright/pom.properties";
        try (var stream = Playwright.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream != null) {
                var properties = new Properties();
                properties.load(stream);
                var version = properties.getProperty("version");
                if (version != null && !version.isBlank()) {
                    return version.trim();
                }
            }
        } catch (IOException ignored) {
            // fallback below
        }
        return "unknown";
    }

    private static List<String> parseBfsgTags(final String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Tag list cannot be null");
        }
        var split = raw.split(",");
        var tags = new ArrayList<String>();
        for (String part : split) {
            if (part == null) {
                continue;
            }
            var trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                tags.add(trimmed);
            }
        }
        if (tags.isEmpty()) {
            throw new IllegalArgumentException("BFSG tag list cannot be empty");
        }
        return List.copyOf(tags);
    }

    private static List<String> parseIgnoreSlugs(final String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        var slugs = new ArrayList<String>();
        for (String part : raw.split(",")) {
            if (part == null) {
                continue;
            }
            var trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                slugs.add(trimmed);
            }
        }
        return slugs.isEmpty() ? List.of() : List.copyOf(slugs);
    }

    static int exitCodeForReport(final TypeMap report) {
        if (report == null) {
            return 0;
        }
        return report.asStringOpt(HtmlValidator.FIELD_BFSG_STATUS)
            .filter(HtmlValidator.BFSG_STATUS_ERROR::equals)
            .map(status -> 2)
            .orElse(0);
    }

    private static void applyEnvironment(final TypeMap options, final Map<String, String> environment) {
        if (environment == null) {
            return;
        }
        var help = firstNonBlank(environment, ENV_HELP, "INPUT_HELP");
        if (isTruthy(help)) {
            options.put(OPTION_HELP, true);
        }
        var output = firstNonBlank(environment, ENV_OUTPUT_DIR, "INPUT_OUTPUT_DIR");
        if (output != null) {
            options.put(OPTION_OUTPUT, output);
        }
        var noBfsg = firstNonBlank(environment, ENV_NO_BFSG, "INPUT_NO_BFSG");
        if (noBfsg != null) {
            options.put(OPTION_BFSG, !isTruthy(noBfsg));
        }
        var tags = firstNonBlank(environment, ENV_BFSG_TAGS, "INPUT_BFSG_TAGS");
        if (tags != null) {
            options.put(OPTION_BFSG_TAGS, parseBfsgTags(tags));
        }
        var summary = firstNonBlank(environment, ENV_SUMMARY, "INPUT_GITHUB_SUMMARY");
        if (isTruthy(summary)) {
            options.put(OPTION_SUMMARY, true);
        }
        var ignore = firstNonBlank(environment, ENV_IGNORE_SLUGS, "INPUT_IGNORE_SLUGS");
        if (ignore != null) {
            options.put(OPTION_IGNORE_SLUGS, parseIgnoreSlugs(ignore));
        }
    }

    private static boolean isUnicornMode(final List<String> tags) {
        return tags != null
            && tags.size() == 1
            && UNICORN_TAG.equalsIgnoreCase(tags.getFirst());
    }

    private static void writeGithubOutputs(final TypeMap report, final Path outputDir, final Map<String, String> environment) {
        if (environment == null) {
            return;
        }
        var outputFile = environment.get(ENV_GITHUB_OUTPUT);
        if (outputFile == null || outputFile.isBlank()) {
            return;
        }
        var builder = new StringBuilder();
        appendOutput(builder, "accepted", percentage(report, HtmlValidator.LEVEL_ACCEPTED));
        appendOutput(builder, "partial", percentage(report, HtmlValidator.LEVEL_PARTIAL));
        appendOutput(builder, "rejected", percentage(report, HtmlValidator.LEVEL_REJECTED));
        appendOutput(builder, "unknown", String.join(",", report.asList(String.class, HtmlValidator.FIELD_UNKNOWN)));
        appendOutput(builder, "bfsg_status", report.asStringOpt(HtmlValidator.FIELD_BFSG_STATUS).orElse("skipped"));
        appendOutput(builder, "bfsg_issues", String.valueOf(report.asLongOpt(HtmlValidator.FIELD_BFSG_ISSUE_COUNT).orElse(0L)));
        appendOutput(builder, "report_dir", outputDir.toAbsolutePath().toString());
        appendOutput(builder, "report_json", outputDir.resolve("report.json").toAbsolutePath().toString());
        appendOutput(builder, "report_html", outputDir.resolve("report.html").toAbsolutePath().toString());
        appendOutput(builder, "report_md", outputDir.resolve("report.md").toAbsolutePath().toString());
        appendOutput(builder, "report_xml", outputDir.resolve("report.xml").toAbsolutePath().toString());
        appendOutput(builder, "summary_md", outputDir.resolve("report.md").toAbsolutePath().toString());
        try {
            Files.writeString(Path.of(outputFile), builder.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write GitHub outputs", exception);
        }
    }

    private static void writeGithubSummary(final Path outputDir, final Map<String, String> environment) {
        if (environment == null) {
            return;
        }
        var summaryPath = environment.get("GITHUB_STEP_SUMMARY");
        if (summaryPath == null || summaryPath.isBlank()) {
            return;
        }
        var markdownFile = outputDir.resolve("report.md");
        if (Files.notExists(markdownFile)) {
            return;
        }
        try {
            var content = Files.readString(markdownFile, StandardCharsets.UTF_8);
            Files.writeString(Path.of(summaryPath), content + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write GitHub summary", exception);
        }
    }

    private static void appendOutput(final StringBuilder builder, final String key, final String value) {
        builder.append(key)
            .append('=')
            .append(value == null ? "" : sanitizeGithubOutput(value))
            .append(System.lineSeparator());
    }

    private static String sanitizeGithubOutput(final String value) {
        return value.replace("%", "%25")
            .replace("\r", "%0D")
            .replace("\n", "%0A");
    }

    private static String percentage(final TypeMap report, final String field) {
        return report.asBigDecimalOpt(field)
            .orElse(BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP)
            .toPlainString();
    }

    private static boolean isTruthy(final String value) {
        if (value == null)
            return false;
        var normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("1")
            || normalized.equals("true")
            || normalized.equals("yes")
            || normalized.equals("on");
    }

    private static String firstNonBlank(final Map<String, String> environment, final String... keys) {
        if (environment == null)
            return null;
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            var value = environment.get(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
