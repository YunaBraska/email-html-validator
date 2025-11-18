package org.nanonative.validation;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.AxeResults;
import com.deque.html.axecore.results.CheckedNode;
import com.deque.html.axecore.results.Rule;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;

import java.net.URI;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Runs the BFSG (Better Friends of Screenreader Guild) audit by feeding the
 * HTML snippet through Playwright + axe-core. Handles native-image quirks and
 * produces human-readable violations.
 */
class BfsgComplianceValidator {

    private static final boolean NATIVE_IMAGE = System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    private static final AtomicBoolean RESOURCE_FS_READY = new AtomicBoolean(false);
    private static final AtomicBoolean PLAYWRIGHT_CLI_READY = new AtomicBoolean(false);
    private static final Set<String> FRAGMENT_DOCUMENT_RULES = Set.of(
            "document-title",
            "html-has-lang",
            "landmark-one-main",
            "page-has-heading-one"
    );
    private static final String PLAYWRIGHT_CLI_PROPERTY = "playwright.cli.dir";
    private static final String ENV_PLAYWRIGHT_CLI_DIR = "PLAYWRIGHT_CLI_DIR";
    private static final String ENV_EHV_PLAYWRIGHT_CLI_DIR = "EHV_PLAYWRIGHT_CLI_DIR";
    public static final String STATUS_ERROR = "error";
    public static final String STATUS_PASS = "pass";

    /**
     * Static helper—instantiation would only make it self-conscious.
     */
    protected BfsgComplianceValidator() {
    }

    /**
     * Runs the BFSG audit with default settings.
     */
    static BfsgResult evaluate(final String html) {
        return evaluate(html, List.of());
    }

    /**
     * Runs the BFSG audit while limiting axe-core to the provided tag list.
     */
    static BfsgResult evaluate(final String html, final List<String> tags) {
        if (html == null || html.isBlank()) {
            return new BfsgResult(STATUS_PASS, List.of());
        }
        configurePlaywrightCliDir();
        var fsError = mountNativeResourceFileSystem();
        if (fsError.isPresent()) {
            return new BfsgResult(STATUS_ERROR, List.of(fsError.get()));
        }
        var htmlIsFragment = isHtmlFragment(html);
        var bfsgTags = normalizeTags(tags);
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
             BrowserContext context = browser.newContext()) {
            try (Page page = context.newPage()) {
                page.setContent(html, new Page.SetContentOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
                var builder = new AxeBuilder(page);
                if (!bfsgTags.isEmpty()) {
                    builder.withTags(bfsgTags);
                }
                AxeResults results = builder.analyze();
                if (results == null) {
                    return new BfsgResult(STATUS_ERROR, List.of("axe-core returned no result"));
                }
                var violations = Optional.ofNullable(results.getViolations()).orElse(List.of());
                var issues = violations.stream()
                        .filter(rule -> !shouldIgnoreRule(rule, htmlIsFragment))
                        .map(BfsgComplianceValidator::formatViolation)
                        .toList();
                return new BfsgResult(issues.isEmpty() ? STATUS_PASS : "fail", issues);
            }
        } catch (Exception exception) {
            if (playwrightCliUnavailable(exception)) {
                return new BfsgResult(STATUS_ERROR, List.of("Playwright CLI not available. Set PLAYWRIGHT_CLI_DIR (or EHV_PLAYWRIGHT_CLI_DIR) to an installed driver directory."));
            }
            return new BfsgResult(STATUS_ERROR, List.of("axe-core failure: " + describe(exception)));
        }
    }

    /**
     * Ignores structural rules when only a fragment was supplied. Otherwise
     * axe-core would yell about missing {@code <html>} forever.
     */
    protected static boolean shouldIgnoreRule(final Rule rule, final boolean htmlIsFragment) {
        if (!htmlIsFragment || rule == null)
            return false;
        return FRAGMENT_DOCUMENT_RULES.contains(Optional.ofNullable(rule.getId())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .orElse(""));
    }

    /**
     * Determines whether the provided HTML looks like a full document or a
     * snippet missing the {@code <html>} root.
     */
    protected static boolean isHtmlFragment(final String html) {
        return html == null || !html.toLowerCase(Locale.ROOT).contains("<html");
    }

    /**
     * Sanitizes the optional BFSG tag filter: trims, lowercases, and removes
     * duplicates so axe-core stays focused.
     */
    protected static List<String> normalizeTags(final List<String> tags) {
        if (tags == null || tags.isEmpty())
            return List.of();

        var unique = new ArrayList<String>();
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            var trimmed = tag.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            var normalized = trimmed.toLowerCase(Locale.ROOT);
            if (!unique.contains(normalized)) {
                unique.add(normalized);
            }
        }
        return unique.isEmpty() ? List.of() : List.copyOf(unique);
    }

    /**
     * Ensures {@code playwright.cli.dir} is set so the Java bindings can find
     * the downloaded driver bundle, both on JVM and native-image.
     */
    protected static void configurePlaywrightCliDir() {
        if (PLAYWRIGHT_CLI_READY.get()) {
            return;
        }
        synchronized (PLAYWRIGHT_CLI_READY) {
            if (PLAYWRIGHT_CLI_READY.get()) {
                return;
            }
            var existing = System.getProperty(PLAYWRIGHT_CLI_PROPERTY);
            if (existing != null && !existing.isBlank()) {
                PLAYWRIGHT_CLI_READY.set(true);
                return;
            }
            resolvePlaywrightCliDir()
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .ifPresent(value -> System.setProperty(PLAYWRIGHT_CLI_PROPERTY, value));
            PLAYWRIGHT_CLI_READY.set(true);
        }
    }

    /**
     * Checks both {@code PLAYWRIGHT_CLI_DIR} and {@code EHV_PLAYWRIGHT_CLI_DIR}
     * for an installed CLI directory.
     */
    protected static Optional<Path> resolvePlaywrightCliDir() {
        return Stream.of(ENV_PLAYWRIGHT_CLI_DIR, ENV_EHV_PLAYWRIGHT_CLI_DIR)
                .map(System::getenv)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .map(Path::of)
                .filter(Files::isDirectory)
                .findFirst();
    }

    /**
     * Heuristically detects when the Playwright driver jars are missing, so we
     * can raise a targeted error message instead of a stack trace.
     */
    protected static boolean playwrightCliUnavailable(final Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ClassNotFoundException missing
                    && Optional.ofNullable(missing.getMessage())
                    .map(message -> message.contains("DriverJar"))
                    .orElse(false)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Native-image ships axe-core resources inside the binary. This method mounts
     * the virtual {@code resource:/} filesystem once so Playwright can reach it.
     */
    protected static Optional<String> mountNativeResourceFileSystem() {
        if (!NATIVE_IMAGE || RESOURCE_FS_READY.get()) {
            return Optional.empty();
        }
        synchronized (RESOURCE_FS_READY) {
            if (RESOURCE_FS_READY.get()) {
                return Optional.empty();
            }
            try {
                FileSystems.newFileSystem(URI.create("resource:/"), Map.of());
                RESOURCE_FS_READY.set(true);
                return Optional.empty();
            } catch (FileSystemAlreadyExistsException alreadyExists) {
                RESOURCE_FS_READY.set(true);
                return Optional.empty();
            } catch (Exception exception) {
                return Optional.of("axe-core driver unavailable in native image: " + describe(exception));
            }
        }
    }

    /**
     * Converts an axe-core rule violation into a concise string that humans—and
     * future debugging sessions—can read.
     */
    protected static String formatViolation(final Rule rule) {
        var builder = new StringBuilder();
        var ruleId = Optional.ofNullable(rule.getId()).filter(s -> !s.isBlank()).orElse("rule");
        builder.append(ruleId);
        Optional.ofNullable(rule.getImpact())
                .filter(impact -> !impact.isBlank())
                .ifPresent(impact -> builder.append(" [").append(impact).append(']'));
        var summary = Optional.ofNullable(rule.getHelp())
                .filter(s -> !s.isBlank())
                .orElseGet(() -> Optional.ofNullable(rule.getDescription()).orElse("Accessibility violation"));
        builder.append(": ").append(summary);
        var nodes = safeNodes(rule.getNodes());
        if (!nodes.isEmpty()) {
            builder.append(" -> ").append(String.join("; ", nodes));
        }
        return builder.toString();
    }

    /**
     * Extracts node selectors from axe-core results while avoiding {@code null}
     * explosions.
     */
    protected static List<String> safeNodes(final List<CheckedNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        var selectorDescriptions = new ArrayList<String>();
        for (CheckedNode node : nodes) {
            if (node == null) {
                continue;
            }
            var target = formatTarget(node.getTarget());
            if (!target.isBlank()) {
                selectorDescriptions.add(target);
            }
        }
        return selectorDescriptions;
    }

    /**
     * Serializes the target selector(s) for logging. Lists are concatenated,
     * scalars are stringified as-is.
     */
    protected static String formatTarget(final Object target) {
        if (target == null) {
            return "";
        }
        if (target instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.joining(" "));
        }
        return String.valueOf(target);
    }

    /**
     * Builds a short, friendly description of the root cause for logging.
     */
    protected static String describe(final Throwable t) {
        if (t == null)
            return "Throwable: <null>";

        final List<String> out = new ArrayList<>();
        Throwable x = t;

        while (x != null) {
            final StringBuilder sb = new StringBuilder(48);

            sb.append(x.getClass().getSimpleName());

            final String msg = x.getMessage();
            if (msg != null && !msg.isBlank()) {
                sb.append(": ").append(msg.trim());
            }

            final StackTraceElement[] st = x.getStackTrace();
            if (st.length > 0) {
                final StackTraceElement e = st[0];
                sb.append(" @ ")
                        .append(e.getClassName())
                        .append('.')
                        .append(e.getMethodName())
                        .append(':')
                        .append(e.getLineNumber());
            }

            out.add(sb.toString());
            x = x.getCause();
        }

        return String.join(" -> ", out);
    }

    record BfsgResult(String status, List<String> issues) {
    }
}
