package org.nanonative.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailHtmlValidatorRequestTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldProduceReportAndPersistArtifacts() {
        var html = "<html><body><p>hello</p></body></html>";
        var report = EmailHtmlValidatorRequest.html(html)
            .bfsg(false)
            .outputDirectory(tempDir)
            .run();

        assertThat(report).isNotNull();
        assertThat(report.containsKey("accepted")).isTrue();
        assertThat(tempDir.resolve("report.json")).exists();
        assertThat(tempDir.resolve("report.html")).exists();
    }

    @Test
    void shouldSkipReportExportWhenDisabled() {
        var html = "<html><body><p>skip export</p></body></html>";
        var output = EmailHtmlValidatorRequest.html(html)
            .bfsg(false)
            .outputDirectory(tempDir)
            .disableReportExport()
            .run();

        assertThat(output).isNotNull();
        assertThat(Files.exists(tempDir.resolve("report.json"))).isFalse();
    }

    @Test
    void shouldNormalizeBfsgTags() throws Exception {
        var request = EmailHtmlValidatorRequest.html("<html><body></body></html>")
            .bfsg(false)
            .bfsgTags(new java.util.ArrayList<>(java.util.Arrays.asList(" wcag2AA", null, "best-practice", "BEST-practice")))
            .disableReportExport();

        var field = EmailHtmlValidatorRequest.class.getDeclaredField("bfsgTags");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var normalized = (List<String>) field.get(request);
        assertThat(normalized).containsExactly("wcag2aa", "best-practice");
    }

    @Test
    void shouldHandleNullOutputDirWhenExportDisabled() {
        var html = "<html><body><p>null dir</p></body></html>";
        var result = EmailHtmlValidatorRequest.html(html)
            .bfsg(false)
            .outputDirectory(null)
            .disableReportExport()
            .run();

        assertThat(result).isNotNull();
    }

    @Test
    void shouldFailWhenHtmlIsNull() {
        assertThatThrownBy(() -> EmailHtmlValidatorRequest.html(null).run())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldAllowCustomIgnoredSlugs() {
        var html = "<html><body><meta name='viewport'></body></html>";
        var report = EmailHtmlValidatorRequest.html(html)
            .bfsg(false)
            .ignoreFeatures(List.of("tag:meta"))
            .disableReportExport()
            .run();

        assertThat(report.asList(String.class, HtmlValidator.FIELD_UNKNOWN)).doesNotContain("tag:meta");
    }
}
