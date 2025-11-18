package org.nanonative.validation;

import berlin.yuna.typemap.model.TypeMap;
import org.nanonative.cli.ReportExporter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Fluent entry point for executing the validator directly from Java.
 */
public final class EmailHtmlValidatorRequest {

    private final String html;
    private boolean includeBfsg = true;
    private List<String> bfsgTags = List.of();
    private Path outputDir = Path.of("reports");
    private boolean exportReports = true;
    private List<String> ignoreSlugs = new ArrayList<>(HtmlValidator.DEFAULT_IGNORED_SLUGS);

    private EmailHtmlValidatorRequest(final String html) {
        this.html = html;
    }

    public static EmailHtmlValidatorRequest html(final String html) {
        return new EmailHtmlValidatorRequest(html);
    }

    public EmailHtmlValidatorRequest bfsg(final boolean enable) {
        this.includeBfsg = enable;
        return this;
    }

    public EmailHtmlValidatorRequest bfsgTags(final List<String> tags) {
        this.bfsgTags = tags == null ? List.of() : tags.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(tag -> !tag.isEmpty())
            .map(String::toLowerCase)
            .distinct()
            .toList();
        return this;
    }

    public EmailHtmlValidatorRequest outputDirectory(final Path directory) {
        this.outputDir = directory;
        return this;
    }

    public EmailHtmlValidatorRequest disableReportExport() {
        this.exportReports = false;
        return this;
    }

    public EmailHtmlValidatorRequest ignoreSlugs(final List<String> slugs) {
        this.ignoreSlugs = slugs == null ? List.of() : slugs.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(value -> value.toLowerCase(Locale.ROOT))
            .distinct()
            .toList();
        return this;
    }

    public TypeMap run() {
        Objects.requireNonNull(html, "html");
        var report = HtmlValidator.validate(html, includeBfsg, bfsgTags);
        var slugs = ignoreSlugs.isEmpty() ? HtmlValidator.DEFAULT_IGNORED_SLUGS : ignoreSlugs;
        HtmlValidator.ignoreSlugs(report, slugs);
        if (exportReports && outputDir != null) {
            new ReportExporter(outputDir).export(report);
        }
        return report;
    }
}
