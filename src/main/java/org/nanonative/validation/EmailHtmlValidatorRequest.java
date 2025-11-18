package org.nanonative.validation;

import berlin.yuna.typemap.model.TypeMap;
import org.nanonative.cli.ReportExporter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Fluent entry point for invoking the validator from Java tests or apps.
 * Think of it as a polite turret: it will shoot down bad HTML but also
 * tell you exactly why.
 */
public final class EmailHtmlValidatorRequest {

    private final String html;
    private boolean includeBfsg = true;
    private List<String> bfsgTags = List.of();
    private Path outputDir = Path.of("reports");
    private boolean exportReports = true;
    private List<String> ignoredFeatures = new ArrayList<>(HtmlValidator.DEFAULT_IGNORED_SLUGS);

    /**
     * Instantiates the request for a particular HTML snippet.
     */
    protected EmailHtmlValidatorRequest(final String html) {
        this.html = html;
    }

    /**
     * Starts a request for the provided HTML content.
     */
    public static EmailHtmlValidatorRequest html(final String html) {
        return new EmailHtmlValidatorRequest(html);
    }

    /**
     * Enables or disables BFSG accessibility checks.
     */
    public EmailHtmlValidatorRequest bfsg(final boolean enable) {
        this.includeBfsg = enable;
        return this;
    }

    /**
     * Restricts BFSG to the provided axe-core tags.
     */
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

    /**
     * Overrides the report output directory.
     */
    public EmailHtmlValidatorRequest outputDirectory(final Path directory) {
        this.outputDir = directory;
        return this;
    }

    /**
     * Suppresses file exports when you only need the TypeMap.
     */
    public EmailHtmlValidatorRequest disableReportExport() {
        this.exportReports = false;
        return this;
    }

    /**
     * Provides a custom list of features to ignore (case-insensitive).
     */
    public EmailHtmlValidatorRequest ignoreFeatures(final List<String> features) {
        this.ignoredFeatures = features == null ? List.of() : features.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(value -> value.toLowerCase(Locale.ROOT))
            .distinct()
            .toList();
        return this;
    }

    /**
     * Runs the validator and returns the resulting TypeMap.
     */
    public TypeMap run() {
        Objects.requireNonNull(html, "html");
        var report = HtmlValidator.validate(html, includeBfsg, bfsgTags);
        var features = ignoredFeatures.isEmpty() ? HtmlValidator.DEFAULT_IGNORED_SLUGS : ignoredFeatures;
        HtmlValidator.ignoreSlugs(report, features);
        if (exportReports && outputDir != null) {
            new ReportExporter(outputDir).export(report);
        }
        return report;
    }
}
