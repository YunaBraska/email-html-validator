package org.nanonative.cli;

import berlin.yuna.typemap.model.TypeMap;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.nanonative.caniemail.CaniEmailFeatureDatabase;
import org.nanonative.validation.HtmlValidator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmailHtmlValidatorCliBlackBoxTest {

    private Path workspace;
    private final List<String> knownOperatingSystems = CaniEmailFeatureDatabase.operatingSystems();

    @BeforeAll
    void setUpWorkspace() throws IOException {
        workspace = Files.createTempDirectory("email-validator-tests");
    }

    @AfterAll
    void cleanWorkspace() throws IOException {
        if (workspace == null || Files.notExists(workspace)) {
            return;
        }
        try (var stream = Files.walk(workspace)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            });
        }
    }

    @Test
    void shouldValidateInlineHtml(final TestInfo info) {
        var dir = reportDir(info);
        var output = runCli(dir, "<html><body><table style='color:#fff'></table></body></html>");
        assertConsole(output, 5, "83.54", "7.32", "9.15");
        assertReport(dir, percentage("83.54"), percentage("7.32"), percentage("9.15"), "tag:html");
    }

    @Test
    void shouldValidateTokenizedInlineHtml(final TestInfo info) {
        var dir = reportDir(info);
        var output = runCli(dir, "<html", "><body><p>split</p></body></html>");
        assertConsole(output, 3, "67.07", "14.63", "18.29");
        assertReport(dir, percentage("67.07"), percentage("14.63"), percentage("18.29"), "tag:html");
    }

    @Test
    void shouldValidateFileInput(final TestInfo info) throws IOException {
        var dir = reportDir(info);
        var htmlFile = Files.createTempFile(dir, "sample", ".html");
        Files.writeString(htmlFile, "<html><body><video controls></video></body></html>", StandardCharsets.UTF_8);
        var output = runCli(dir, htmlFile.toString());
        assertConsole(output, 4, "26.83", "20.73", "52.44");
        assertReport(dir, percentage("26.83"), percentage("20.73"), percentage("52.44"), "tag:html", "attribute:controls");
    }

    @Test
    void shouldValidateRemoteUrl(final TestInfo info) throws Exception {
        var dir = reportDir(info);
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> respond(exchange, "<html><body><audio autoplay></audio></body></html>"));
        server.start();
        try {
            var remoteUrl = URI.create("http://localhost:" + server.getAddress().getPort() + "/").toString();
            var output = runCli(dir, remoteUrl);
            assertConsole(output, 4, "27.79", "18.21", "54.01");
            assertReport(dir, percentage("27.79"), percentage("18.21"), percentage("54.01"), "tag:html", "attribute:autoplay");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldHandleTemplateInputs(final TestInfo info) {
        var dir = reportDir(info);
        var template = """
            <mjml>
              <mj-body>
                <mj-text th:text="${message}">
                  Hello {{name}}
                </mj-text>
              </mj-body>
            </mjml>
            """;
        var output = runCli(dir, template);
        assertConsole(output, 4, "0.00", "0.00", "0.00");
        assertReport(dir, percentage("0.00"), percentage("0.00"), percentage("0.00"), "tag:mjml", "tag:mj-body", "tag:mj-text", "attribute:th:text");
    }

    @Test
    void shouldReportBrokenHtml(final TestInfo info) {
        var dir = reportDir(info);
        var broken = "<div><span class='item' style='display:flex; color:#000' {{broken}}></span>";
        var output = runCli(dir, broken);
        assertConsole(output, 7, "93.50", "6.50", "0.00");
        assertReport(dir, percentage("93.50"), percentage("6.50"), percentage("0.00"), "attribute:{{broken}}");
    }

    @Test
    void shouldExportArtifactsWhenOutputDirProvided(final TestInfo info) {
        var dir = reportDir(info);
        var output = runCli(dir, "<html><body><p>parallel</p></body></html>");
        assertConsole(output, 3, "67.07", "14.63", "18.29");
        assertReport(dir, percentage("67.07"), percentage("14.63"), percentage("18.29"), "tag:html");
    }

    @Test
    void shouldReadFromStdin(final TestInfo info) {
        var dir = reportDir(info);
        var output = runCliWithStdin(dir, "<html><body><p>stdin</p></body></html>", "-");
        assertConsole(output, 3, "67.07", "14.63", "18.29");
        assertReport(dir, percentage("67.07"), percentage("14.63"), percentage("18.29"), "tag:html");
    }

    @Test
    void shouldEvaluateMediaQueries(final TestInfo info) {
        var dir = reportDir(info);
        var template = """
            <html>
              <head>
                <style>
                  @media screen { .content { color: red; } }
                  @media (-webkit-device-pixel-ratio: 2) { .content { font-size: 18px; } }
                </style>
              </head>
              <body>
                <div class="content">hello</div>
              </body>
            </html>
            """;
        var output = runCli(dir, template);
        assertConsole(output, 10, "62.67", "17.72", "19.61");
        var report = assertReport(dir, percentage("62.67"), percentage("17.72"), percentage("19.61"),
            "tag:html", "tag:head", "css:@media screen { .content { color", "css:} }\n      @media (-webkit-device-pixel-ratio");
        var notes = report.asMap(HtmlValidator.FIELD_PARTIAL_NOTES);
        assertThat(notes).containsKeys("css:at-media", "css:at-media-device-pixel-ratio", "tag:body", "tag:style");
    }

    @Test
    void shouldReportInputErrors() {
        var result = runCliWithStatus(1, "--output-dir");
        assertThat(result[0]).isEmpty();
        assertThat(result[1]).contains("Input error: Missing path for --output-dir");
    }

    @Test
    void shouldRequireSourceArgument() {
        var result = runCliWithStatus(1);
        assertThat(result[0]).isEmpty();
        assertThat(result[1]).contains("Input error: Missing HTML source");
    }

    @Test
    void shouldReportRuntimeErrors(final TestInfo info) throws Exception {
        var dir = reportDir(info);
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            var payload = "boom".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        });
        server.start();
        try {
            var remoteUrl = URI.create("http://localhost:" + server.getAddress().getPort() + "/").toString();
            var result = runCliWithStatus(2, "--output-dir", dir.toString(), remoteUrl);
            assertThat(result[0]).isEmpty();
            assertThat(result[1]).contains("Unable to validate HTML");
            assertThat(dir.resolve("report.json")).doesNotExist();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldIgnoreAutoGeneratedTags(final TestInfo info) {
        var dir = reportDir(info);
        var output = runCli(dir, "<div style='color:black;'></div>");
        assertConsole(output, 3, "100.00", "0.00", "0.00");
        var report = assertReport(dir, percentage("100.00"), percentage("0.00"), percentage("0.00"));
        assertThat(report.asMap(HtmlValidator.FIELD_PARTIAL_NOTES).keySet()).doesNotContain("tag:body");
    }

    @Test
    void shouldValidateUserProvidedHeadTags(final TestInfo info) {
        var dir = reportDir(info);
        var html = "<head><meta name='viewport' content='width=device-width'></head>";
        var output = runCli(dir, html);
        assertConsole(output, 4, "0.00", "0.00", "0.00");
        var report = assertReport(dir, percentage("0.00"), percentage("0.00"), percentage("0.00"));
        assertThat(report.asList(String.class, HtmlValidator.FIELD_UNKNOWN))
            .contains("tag:head", "tag:meta", "attribute:name", "attribute:content");
    }

    @Test
    void shouldReportBfsgComplianceWhenRequested(final TestInfo info) {
        var dir = reportDir(info);
        var html = "<html><body><img src='hero.png'><a href=''>empty</a><a href='http://example.com'></a></body></html>";
        var output = runCli(dir, "--bfsg", html);
        assertThat(output).contains("BFSG compliance: fail");
        var report = readJson(dir.resolve("report.json"));
        assertThat(report.asStringOpt(HtmlValidator.FIELD_BFSG_STATUS)).contains("fail");
        var issueCount = report.asLongOpt(HtmlValidator.FIELD_BFSG_ISSUE_COUNT).orElse(0L);
        assertThat(issueCount).isGreaterThan(0L);
        var issues = report.asList(String.class, HtmlValidator.FIELD_BFSG_ISSUES);
        assertThat(issues).isNotEmpty();
        assertThat(issues.stream().anyMatch(issue -> issue.contains("img"))).isTrue();
        assertThat(issues.stream().anyMatch(issue -> issue.contains("link"))).isTrue();
    }

    @Test
    void shouldSkipBfsgWhenDisabled(final TestInfo info) {
        var dir = reportDir(info);
        var output = runCli(dir, "--no-bfsg", "<html><body><p>skip</p></body></html>");
        assertThat(output).doesNotContain("BFSG compliance:");
        var report = readJson(dir.resolve("report.json"));
        assertThat(report.containsKey(HtmlValidator.FIELD_BFSG_STATUS)).isFalse();
    }

    private void respond(final com.sun.net.httpserver.HttpExchange exchange, final String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String runCliWithStdin(final Path outputDir, final String stdin, final String... args) {
        var originalIn = System.in;
        System.setIn(new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)));
        try {
            return runCli(outputDir, args);
        } finally {
            System.setIn(originalIn);
        }
    }

    private String runCli(final Path outputDir, final String... args) {
        var merged = new String[args.length + 2];
        merged[0] = "--output-dir";
        merged[1] = outputDir.toString();
        System.arraycopy(args, 0, merged, 2, args.length);
        var capture = runCliWithStatus(0, merged);
        return capture[0] + capture[1];
    }

    private String[] runCliWithStatus(final int expectedStatus, final String... args) {
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        try (var outPrinter = new PrintStream(stdout, true, StandardCharsets.UTF_8);
             var errPrinter = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            var status = EmailHtmlValidatorCli.execute(args, outPrinter, errPrinter);
            assertThat(status).isEqualTo(expectedStatus);
        }
        return new String[]{
            stdout.toString(StandardCharsets.UTF_8),
            stderr.toString(StandardCharsets.UTF_8)
        };
    }

    private TypeMap assertReport(final Path dir, final BigDecimal expectedFull, final BigDecimal expectedPartial, final BigDecimal expectedRejected, final String... expectedUnknown) {
        var jsonFile = dir.resolve("report.json");
        var htmlFile = dir.resolve("report.html");
        var mdFile = dir.resolve("report.md");
        var xmlFile = dir.resolve("report.xml");
        assertThat(jsonFile).exists();
        assertThat(htmlFile).exists();
        assertThat(mdFile).exists();
        assertThat(xmlFile).exists();
        assertThat(jsonFile).content().isNotEmpty();
        assertThat(htmlFile).content().isNotEmpty();
        assertThat(mdFile).content().isNotEmpty();
        assertThat(xmlFile).content().isNotEmpty();
        var json = readJson(jsonFile);
        assertThat(json.asBigDecimalOpt(HtmlValidator.LEVEL_ACCEPTED).orElse(BigDecimal.ZERO)).isEqualByComparingTo(expectedFull);
        assertThat(json.asBigDecimalOpt(HtmlValidator.LEVEL_PARTIAL).orElse(BigDecimal.ZERO)).isEqualByComparingTo(expectedPartial);
        assertThat(json.asBigDecimalOpt(HtmlValidator.LEVEL_REJECTED).orElse(BigDecimal.ZERO)).isEqualByComparingTo(expectedRejected);
        if (expectedUnknown.length > 0) {
            assertThat(json.asList(String.class, HtmlValidator.FIELD_UNKNOWN)).containsExactly(expectedUnknown);
        }
        assertThat(json.asLongOpt(HtmlValidator.FIELD_FEATURE_COUNT)).contains((long) CaniEmailFeatureDatabase.featureCount());
        assertThat(json.asLongOpt(HtmlValidator.FIELD_CLIENT_COUNT)).contains((long) CaniEmailFeatureDatabase.clientCount());
        assertThat(json.asLongOpt(HtmlValidator.FIELD_OPERATING_SYSTEM_COUNT)).contains((long) CaniEmailFeatureDatabase.operatingSystemCount());
        assertThat(json.asStringOpt(HtmlValidator.FIELD_REFERENCE_URL)).contains("https://www.caniemail.com");
        assertClients(json.asList(String.class, HtmlValidator.FIELD_PARTIAL_CLIENTS));
        assertClients(json.asList(String.class, HtmlValidator.FIELD_REJECTED_CLIENTS));
        return json;
    }

    private TypeMap readJson(final Path file) {
        try {
            return new TypeMap(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read report " + file, exception);
        }
    }

    private Path reportDir(final TestInfo info) {
        var name = info.getDisplayName().replaceAll("[^a-zA-Z0-9]+", "-");
        var dir = workspace.resolve(name);
        try {
            return Files.createDirectories(dir);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to prepare report directory for " + info.getDisplayName(), exception);
        }
    }

    private BigDecimal percentage(final String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    private void assertConsole(final String output, final long total, final String accepted, final String partial, final String rejected) {
        assertThat(output).contains("Features evaluated: " + total);
        assertThat(output).contains("  - accepted: " + accepted + "%");
        assertThat(output).contains("  - partial: " + partial + "%");
        assertThat(output).contains("  - rejected: " + rejected + "%");
        assertThat(output).contains("BFSG compliance:");
        var datasetSummary = String.format(
            Locale.ROOT,
            "Dataset: %d features, %d clients, %d operating systems",
            CaniEmailFeatureDatabase.featureCount(),
            CaniEmailFeatureDatabase.clientCount(),
            CaniEmailFeatureDatabase.operatingSystemCount()
        );
        assertThat(output).contains(datasetSummary);
        assertThat(output).contains("Reference: https://www.caniemail.com");
        assertThat(output).contains("Findings:");
    }
    private void assertClients(final List<String> clients) {
        if (clients.isEmpty()) {
            return;
        }
        var operatingSystems = new java.util.HashSet<>(knownOperatingSystems);
        clients.forEach(entry -> {
            assertThat(entry).contains(":");
            var parts = entry.split(":", 2);
            assertThat(parts[0]).isIn(operatingSystems);
            assertThat(parts[1]).isNotEmpty();
        });
    }
}
