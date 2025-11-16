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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

class BfsgComplianceValidator {

    private BfsgComplianceValidator() {
    }

    static BfsgResult evaluate(final String html) {
        if (html == null || html.isBlank()) {
            return new BfsgResult("pass", List.of());
        }
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
             BrowserContext context = browser.newContext()) {
            try (Page page = context.newPage()) {
                page.setContent(html, new Page.SetContentOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
                AxeResults results = new AxeBuilder(page).analyze();
                if (results == null) {
                    return new BfsgResult("error", List.of("axe-core returned no result"));
                }
                var violations = Optional.ofNullable(results.getViolations()).orElse(List.of());
                var issues = violations.stream()
                        .map(BfsgComplianceValidator::formatViolation)
                        .toList();
                return new BfsgResult(issues.isEmpty() ? "pass" : "fail", issues);
            }
        } catch (Exception exception) {
            return new BfsgResult("error", List.of("axe-core failure: " + exception.getMessage()));
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

    record BfsgResult(String status, List<String> issues) {
    }
}
