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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

class BfsgComplianceValidator {

    private static final boolean NATIVE_IMAGE = System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    private static final AtomicBoolean RESOURCE_FS_READY = new AtomicBoolean(false);
    private static final Set<String> FRAGMENT_DOCUMENT_RULES = Set.of(
            "document-title",
            "html-has-lang",
            "landmark-one-main",
            "page-has-heading-one"
    );

    private BfsgComplianceValidator() {
    }

    static BfsgResult evaluate(final String html) {
        return evaluate(html, List.of());
    }

    static BfsgResult evaluate(final String html, final List<String> tags) {
        if (html == null || html.isBlank()) {
            return new BfsgResult("pass", List.of());
        }
        var fsError = mountNativeResourceFileSystem();
        if (fsError.isPresent()) {
            return new BfsgResult("error", List.of(fsError.get()));
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
                    return new BfsgResult("error", List.of("axe-core returned no result"));
                }
                var violations = Optional.ofNullable(results.getViolations()).orElse(List.of());
                var issues = violations.stream()
                        .filter(rule -> !shouldIgnoreRule(rule, htmlIsFragment))
                        .map(BfsgComplianceValidator::formatViolation)
                        .toList();
                return new BfsgResult(issues.isEmpty() ? "pass" : "fail", issues);
            }
        } catch (Exception exception) {
            return new BfsgResult("error", List.of("axe-core failure: " + describe(exception)));
        }
    }

    private static boolean shouldIgnoreRule(final Rule rule, final boolean htmlIsFragment) {
        if (!htmlIsFragment || rule == null) {
            return false;
        }
        var ruleId = Optional.ofNullable(rule.getId())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .orElse("");
        return FRAGMENT_DOCUMENT_RULES.contains(ruleId);
    }

    private static boolean isHtmlFragment(final String html) {
        return html == null
                || !html.toLowerCase(Locale.ROOT).contains("<html");
    }

    private static List<String> normalizeTags(final List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
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

    private static Optional<String> mountNativeResourceFileSystem() {
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

    private static String formatViolation(final Rule rule) {
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

    private static List<String> safeNodes(final List<CheckedNode> nodes) {
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

    private static String formatTarget(final Object target) {
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

    private static String describe(final Throwable throwable) {
        var messages = new ArrayList<String>();
        Throwable current = throwable;
        while (current != null) {
            var message = current.getClass().getSimpleName() + ": " + Optional.ofNullable(current.getMessage()).orElse("");
            messages.add(message.trim());
            current = current.getCause();
        }
        return String.join(" -> ", messages);
    }

    record BfsgResult(String status, List<String> issues) {
    }
}
