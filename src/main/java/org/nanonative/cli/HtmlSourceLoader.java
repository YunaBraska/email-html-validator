package org.nanonative.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;

public class HtmlSourceLoader {

    private HtmlSourceLoader() {
    }

    public static String load(final String source) {
        return load(source, HtmlSourceLoader::fetchRemote);
    }

    public static String load(final String source, final Function<URI, String> fetcher) {
        Objects.requireNonNull(fetcher, "fetcher");
        if (source == null || source.isBlank() || "-".equals(source.trim())) {
            return readStdIn();
        }
        if (looksLikeInlineHtml(source)) {
            return source;
        }
        var path = Path.of(source);
        if (Files.exists(path)) {
            return readFile(path);
        }
        var uri = tryCreateUri(source);
        if (uri != null) {
            return fetcher.apply(uri);
        }
        throw new IllegalArgumentException("Unable to interpret HTML source: " + source);
    }

    private static boolean looksLikeInlineHtml(final String source) {
        var trimmed = source.stripLeading();
        return trimmed.startsWith("<");
    }

    private static URI tryCreateUri(final String input) {
        try {
            var uri = URI.create(input.trim());
            var scheme = uri.getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                return uri;
        } catch (IllegalArgumentException ignored) {
            // intentional
        }
        return null;
    }

    private static String readFile(final Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read HTML file " + path, exception);
        }
    }

    private static String readStdIn() {
        try {
            return new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read HTML from stdin", exception);
        }
    }

    private static String fetchRemote(final URI uri) {
        var client = httpClient();
        var request = HttpRequest.newBuilder(uri.normalize())
            .GET()
            .timeout(Duration.ofSeconds(15))
            .header("User-Agent", "email-html-validator/0.1")
            .build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Failed to fetch HTML (" + response.statusCode() + ") from " + uri);
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while fetching HTML from " + uri, exception);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to fetch HTML from " + uri, exception);
        }
    }

    private static HttpClient httpClient() {
        return HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }
}
