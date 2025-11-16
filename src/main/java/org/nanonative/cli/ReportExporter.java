package org.nanonative.cli;

import berlin.yuna.typemap.model.TypeMap;
import org.nanonative.validation.HtmlValidator;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReportExporter {

    private static final List<String> LEVELS = List.of(
            HtmlValidator.LEVEL_ACCEPTED,
            HtmlValidator.LEVEL_PARTIAL,
            HtmlValidator.LEVEL_REJECTED
    );
    private static final String NL = System.lineSeparator();

    private final Path directory;

    public ReportExporter(final Path directory) {
        this.directory = directory;
    }

    public static void printConsole(final TypeMap report, final PrintStream out) {
        var total = report.asLongOpt(HtmlValidator.FIELD_TOTAL).orElse(0L);
        var featureCount = report.asLongOpt(HtmlValidator.FIELD_FEATURE_COUNT).orElse(0L);
        var clientCount = report.asLongOpt(HtmlValidator.FIELD_CLIENT_COUNT).orElse(0L);
        var osCount = report.asLongOpt(HtmlValidator.FIELD_OPERATING_SYSTEM_COUNT).orElse(0L);
        var reference = report.asStringOpt(HtmlValidator.FIELD_REFERENCE_URL).orElse("https://www.caniemail.com");
        out.printf(Locale.ROOT, "Features evaluated: %d%n", total);

        for (var level : LEVELS) {
            var percentage = report.asBigDecimalOpt(level).orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            out.printf(Locale.ROOT, "  - %s: %s%%%n", level, percentage.toPlainString());
        }

        var notes = report.asMap(HtmlValidator.FIELD_PARTIAL_NOTES);
        out.println("Findings:");
        if (notes.isEmpty()) {
            out.println("  (none)");
        } else {
            notes.entrySet().stream()
                    .map(entry -> Map.entry(
                            entry.getKey().toString(),
                            notes.asList(String.class, entry.getKey())
                    ))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        var suffix = entry.getValue().isEmpty() ? "" : " (" + String.join("; ", entry.getValue()) + ")";
                        out.printf(Locale.ROOT, "  * %s -> %s%s%n", entry.getKey(), HtmlValidator.LEVEL_PARTIAL, suffix);
                    });
        }

        var unknown = report.asList(String.class, HtmlValidator.FIELD_UNKNOWN).stream()
                .sorted()
                .toList();
        if (!unknown.isEmpty()) {
            out.println("Unknown:");
            unknown.forEach(feature -> out.println("  * " + feature));
        }

        var partialClients = report.asList(String.class, HtmlValidator.FIELD_PARTIAL_CLIENTS);
        if (!partialClients.isEmpty()) {
            out.println("Partial clients:");
            partialClients.forEach(client -> out.println("  * " + client));
        }
        var rejectedClients = report.asList(String.class, HtmlValidator.FIELD_REJECTED_CLIENTS);
        if (!rejectedClients.isEmpty()) {
            out.println("Rejected clients:");
            rejectedClients.forEach(client -> out.println("  * " + client));
        }
        report.asStringOpt(HtmlValidator.FIELD_BFSG_STATUS).ifPresent(status -> {
            long issueCount = report.asLongOpt(HtmlValidator.FIELD_BFSG_ISSUE_COUNT).orElse(0L);
            out.printf(Locale.ROOT, "BFSG compliance: %s (%d issues)%n", status, issueCount);
            var issues = report.asList(String.class, HtmlValidator.FIELD_BFSG_ISSUES);
            if (!issues.isEmpty()) {
                issues.forEach(issue -> out.println("  - " + issue));
            }
        });
        out.printf(Locale.ROOT, "Dataset: %d features, %d clients, %d operating systems%n", featureCount, clientCount, osCount);
        out.println("Reference: " + reference);
    }

    public void export(final TypeMap report) {
        try {
            Files.createDirectories(directory);
            write("report.json", report.toJson());
            write("report.xml", toXml(report));
            write("report.html", toHtml(report));
            write("report.md", toMarkdown(report));
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write report files", exception);
        }
    }

    private void write(final String fileName, final String content) throws IOException {
        Files.writeString(directory.resolve(fileName), content, StandardCharsets.UTF_8);
    }

    private String toXml(final TypeMap report) {
        var builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>").append(NL);
        appendXml("report", report, builder, 0);
        return builder.toString();
    }

    private void appendXml(final String name, final Object value, final StringBuilder builder, final int indent) {
        builder.append(indent(indent)).append('<').append(name).append('>');
        if (value instanceof Map<?, ?> map) {
            builder.append(NL);
            map.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                    .forEach(entry -> appendXml(entry.getKey().toString(), entry.getValue(), builder, indent + 2));
            builder.append(indent(indent));
        } else if (value instanceof List<?> list) {
            builder.append(NL);
            list.forEach(item -> appendXml("item", item, builder, indent + 2));
            builder.append(indent(indent));
        } else if (value != null) {
            builder.append(escapeXml(value));
        }
        builder.append("</").append(name).append('>').append(NL);
    }

    private String toHtml(final TypeMap report) {
        var partialNotes = report.asMap(HtmlValidator.FIELD_PARTIAL_NOTES);
        var unknown = report.asList(String.class, HtmlValidator.FIELD_UNKNOWN);
        var partialClients = report.asList(String.class, HtmlValidator.FIELD_PARTIAL_CLIENTS);
        var rejectedClients = report.asList(String.class, HtmlValidator.FIELD_REJECTED_CLIENTS);
        var bfsgStatus = report.asStringOpt(HtmlValidator.FIELD_BFSG_STATUS);
        var bfsgIssues = report.asList(String.class, HtmlValidator.FIELD_BFSG_ISSUES);
        var bfsgIssueCount = report.asLongOpt(HtmlValidator.FIELD_BFSG_ISSUE_COUNT).orElse(0L);
        var featureCount = report.asLongOpt(HtmlValidator.FIELD_FEATURE_COUNT).orElse(0L);
        var clientCount = report.asLongOpt(HtmlValidator.FIELD_CLIENT_COUNT).orElse(0L);
        var osCount = report.asLongOpt(HtmlValidator.FIELD_OPERATING_SYSTEM_COUNT).orElse(0L);
        var reference = report.asStringOpt(HtmlValidator.FIELD_REFERENCE_URL).orElse("https://www.caniemail.com");
        var builder = new StringBuilder();
        builder.append("<!doctype html>").append(NL)
                .append("<html lang=\"en\">").append(NL)
                .append("<head>").append(NL)
                .append("<meta charset=\"UTF-8\">").append(NL)
                .append("<title>Email HTML Validator Report</title>").append(NL)
                .append("<style>").append(NL)
                .append("body{font-family:Roboto,Arial,sans-serif;margin:0;background:#f5f5f5;color:#212121;}").append(NL)
                .append("header{background:#1976d2;color:#fff;padding:24px 32px;box-shadow:0 2px 4px rgba(0,0,0,0.2);}").append(NL)
                .append(".container{max-width:900px;margin:32px auto;padding:0 16px;}").append(NL)
                .append(".card{background:#fff;border-radius:12px;padding:24px;margin-bottom:24px;box-shadow:0 2px 8px rgba(0,0,0,0.1);}").append(NL)
                .append(".card h2{margin-top:0;font-size:1.3rem;color:#1976d2;}").append(NL)
                .append(".summary-list{display:flex;gap:16px;flex-wrap:wrap;list-style:none;padding:0;margin:0;}").append(NL)
                .append(".summary-list li{flex:1;min-width:120px;background:#e3f2fd;border-radius:8px;padding:16px;text-align:center;font-weight:600;}").append(NL)
                .append(".unknown-list,.notes-list{list-style:none;padding-left:0;margin:0;}").append(NL)
                .append(".unknown-list li,.notes-list li{padding:8px 0;border-bottom:1px solid #e0e0e0;}").append(NL)
                .append(".unknown-list li:last-child,.notes-list li:last-child{border-bottom:none;}").append(NL)
                .append(".chip{display:inline-block;background:#bbdefb;color:#0d47a1;padding:4px 10px;border-radius:999px;font-size:0.9rem;margin:4px 4px 0 0;}").append(NL)
                .append("</style>").append(NL)
                .append("</head>").append(NL)
                .append("<body>").append(NL)
                .append("<header><h1>Email HTML Validator Report</h1><p>Features evaluated: ")
                .append(report.asLongOpt(HtmlValidator.FIELD_TOTAL).orElse(0L))
                .append("</p></header>").append(NL)
                .append("<div class=\"container\">").append(NL);

        builder.append("<section class=\"card\"><h2>Summary</h2><ul class=\"summary-list\">").append(NL);
        for (var level : LEVELS) {
            var percentage = report.asBigDecimalOpt(level).orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            builder.append("<li>")
                    .append(level)
                    .append(": ")
                    .append(percentage.toPlainString())
                    .append("%</li>")
                    .append(NL);
        }
        builder.append("</ul></section>").append(NL);

        builder.append("<section class=\"card\"><h2>Partial notes</h2>").append(NL);
        if (partialNotes.isEmpty()) {
            builder.append("<p>(none)</p>").append(NL);
        } else {
            builder.append("<ul class=\"notes-list\">").append(NL);
            partialNotes.entrySet().stream()
                    .map(entry -> Map.entry(
                            entry.getKey().toString(),
                            partialNotes.asList(String.class, entry.getKey())
                    ))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> builder.append("<li><code>")
                            .append(escapeHtml(entry.getKey()))
                            .append("</code>: ")
                            .append(escapeHtml(String.join("; ", entry.getValue())))
                            .append("</li>")
                            .append(NL));
            builder.append("</ul>").append(NL);
        }
        builder.append("</section>").append(NL);

        builder.append("<section class=\"card\"><h2>Unknown features</h2>").append(NL);
        if (unknown.isEmpty()) {
            builder.append("<p>(none)</p>").append(NL);
        } else {
            builder.append("<div>").append(NL);
            unknown.stream()
                    .sorted()
                    .forEach(item -> builder.append("<span class=\"chip\">").append(escapeHtml(item)).append("</span>").append(NL));
            builder.append("</div>").append(NL);
        }
        builder.append("</section>").append(NL);

        builder.append("<section class=\"card\"><h2>Problematic clients</h2>").append(NL)
                .append("<h3>Partial</h3>").append(NL);
        if (partialClients.isEmpty()) {
            builder.append("<p>(none)</p>").append(NL);
        } else {
            builder.append("<div>").append(NL);
            partialClients.forEach(client -> builder.append("<span class=\"chip\">").append(escapeHtml(client)).append("</span>").append(NL));
            builder.append("</div>").append(NL);
        }
        builder.append("<h3>Rejected</h3>").append(NL);
        if (rejectedClients.isEmpty()) {
            builder.append("<p>(none)</p>").append(NL);
        } else {
                builder.append("<div>").append(NL);
                rejectedClients.forEach(client -> builder.append("<span class=\"chip\">").append(escapeHtml(client)).append("</span>").append(NL));
                builder.append("</div>").append(NL);
        }
        builder.append("</section>").append(NL);

        bfsgStatus.ifPresent(status -> {
            builder.append("<section class=\"card\"><h2>BFSG compliance</h2>").append(NL);
            builder.append("<p>Status: ").append(escapeHtml(status)).append("</p>").append(NL);
            builder.append("<p>Issues: ").append(String.valueOf(bfsgIssueCount)).append("</p>").append(NL);
            if (bfsgIssues.isEmpty()) {
                builder.append("<p>(none)</p>").append(NL);
            } else {
                builder.append("<ul class=\"notes-list\">").append(NL);
                bfsgIssues.forEach(issue -> builder.append("<li>").append(escapeHtml(issue)).append("</li>").append(NL));
                builder.append("</ul>").append(NL);
            }
            builder.append("</section>").append(NL);
        });

        builder.append("<section class=\"card\"><h2>Dataset</h2>")
                .append("<p>Total features known: ").append(featureCount).append("</p>")
                .append("<p>Total clients known: ").append(clientCount).append("</p>")
                .append("<p>Total operating systems: ").append(osCount).append("</p>")
                .append("<p>Reference: <a href=\"").append(reference).append("\" target=\"_blank\" rel=\"noopener noreferrer\">")
                .append(reference).append("</a></p></section>")
                .append(NL);

        builder.append("</div>").append(NL)
                .append("</body>").append(NL)
                .append("</html>").append(NL);
        return builder.toString();
    }

    private String toMarkdown(final TypeMap report) {
        var partialNotes = report.asMap(HtmlValidator.FIELD_PARTIAL_NOTES);
        var unknown = report.asList(String.class, HtmlValidator.FIELD_UNKNOWN);
        var partialClients = report.asList(String.class, HtmlValidator.FIELD_PARTIAL_CLIENTS);
        var rejectedClients = report.asList(String.class, HtmlValidator.FIELD_REJECTED_CLIENTS);
        var bfsgStatus = report.asStringOpt(HtmlValidator.FIELD_BFSG_STATUS);
        var bfsgIssues = report.asList(String.class, HtmlValidator.FIELD_BFSG_ISSUES);
        var bfsgIssueCount = report.asLongOpt(HtmlValidator.FIELD_BFSG_ISSUE_COUNT).orElse(0L);
        var builder = new StringBuilder();
        builder.append("# Email HTML Validator Report").append(NL).append(NL);
        builder.append("- Features evaluated: ").append(report.asLongOpt(HtmlValidator.FIELD_TOTAL).orElse(0L)).append(NL).append(NL);

        builder.append("## Summary").append(NL);
        for (var level : LEVELS) {
            var percentage = report.asBigDecimalOpt(level).orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            builder.append("- ")
                    .append(level)
                    .append(": ")
                    .append(percentage.toPlainString())
                    .append('%')
                    .append(NL);
        }
        builder.append(NL);

        builder.append("## Partial notes").append(NL);
        if (partialNotes.isEmpty()) {
            builder.append("- (none)").append(NL).append(NL);
        } else {
            partialNotes.keySet().stream()
                    .map(object -> Map.entry(
                            object.toString(),
                            partialNotes.asList(String.class, object)
                    ))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> builder.append("- `")
                            .append(entry.getKey())
                            .append("`: ")
                            .append(String.join("; ", entry.getValue()))
                            .append(NL));
            builder.append(NL);
        }

        builder.append("## Unknown").append(NL);
        if (unknown.isEmpty()) {
            builder.append("- (none)").append(NL);
        } else {
            unknown.stream()
                    .sorted()
                    .forEach(entry -> builder.append("- ").append(entry).append(NL));
        }
        builder.append(NL).append("## Problematic clients").append(NL);
        builder.append("### Partial").append(NL);
        if (partialClients.isEmpty()) {
            builder.append("- (none)").append(NL);
        } else {
            partialClients.forEach(client -> builder.append("- ").append(client).append(NL));
        }
        builder.append("### Rejected").append(NL);
        if (rejectedClients.isEmpty()) {
            builder.append("- (none)").append(NL);
        } else {
            rejectedClients.forEach(client -> builder.append("- ").append(client).append(NL));
        }
        bfsgStatus.ifPresent(status -> {
            builder.append("\n## BFSG compliance\n");
            builder.append("- Status: ").append(status).append(NL);
            builder.append("- Issues: ").append(bfsgIssueCount).append(NL);
            if (bfsgIssues.isEmpty()) {
                builder.append("- (none)").append(NL);
            } else {
                bfsgIssues.forEach(issue -> builder.append("- ").append(issue).append(NL));
            }
        });
        builder.append(NL).append("## Dataset").append(NL);
        builder.append("- Features known: ").append(report.asLongOpt(HtmlValidator.FIELD_FEATURE_COUNT).orElse(0L)).append(NL);
        builder.append("- Clients known: ").append(report.asLongOpt(HtmlValidator.FIELD_CLIENT_COUNT).orElse(0L)).append(NL);
        builder.append("- Operating systems: ").append(report.asLongOpt(HtmlValidator.FIELD_OPERATING_SYSTEM_COUNT).orElse(0L)).append(NL);
        builder.append("- Reference: ").append(report.asStringOpt(HtmlValidator.FIELD_REFERENCE_URL).orElse("https://www.caniemail.com")).append(NL);
        return builder.toString();
    }

    private static String escapeHtml(final String value) {
        if (value == null) {
            return "";
        }
        var builder = new StringBuilder();
        for (char ch : value.toCharArray()) {
            switch (ch) {
                case '&' -> builder.append("&amp;");
                case '<' -> builder.append("&lt;");
                case '>' -> builder.append("&gt;");
                default -> builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static String escapeXml(final Object value) {
        if (value == null) {
            return "";
        }
        var text = value.toString();
        var builder = new StringBuilder();
        for (char ch : text.toCharArray()) {
            switch (ch) {
                case '&' -> builder.append("&amp;");
                case '<' -> builder.append("&lt;");
                case '>' -> builder.append("&gt;");
                case '"' -> builder.append("&quot;");
                case '\'' -> builder.append("&apos;");
                default -> builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static String indent(final int spaces) {
        if (spaces <= 0) {
            return "";
        }
        return " ".repeat(spaces);
    }
}
