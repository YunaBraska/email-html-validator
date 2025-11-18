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

/**
 * Resolves user-provided HTML inputs into actual markup. Accepts inline HTML,
 * files, stdin, or remote URLs without making you think too hard.
 */
public class HtmlSourceLoader {

    /**
     * Utility constructor. If you manage to instantiate this class you probably
     * deserve a cookie, but please don't.
     */
    protected HtmlSourceLoader() {
    }

    /**
     * Loads HTML from whatever the user hands us: inline markup, file path,
     * URL, or {@code -} for stdin.
     */
    public static String load(final String source) {
        return load(source, HtmlSourceLoader::fetchRemote);
    }

    /**
     * Same as {@link #load(String)} but allows overriding the HTTP fetcher for
     * tests that prefer to stay offline.
     */
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

    /**
     * Quick heuristics to see if the input is literal HTML rather than a path.
     */
    protected static boolean looksLikeInlineHtml(final String source) {
        var trimmed = source.stripLeading();
        return trimmed.startsWith("<");
    }

    /**
     * Attempts to parse the input as HTTP(S) URI. Returns {@code null} for
     * everything else so the caller can try other strategies.
     */
    protected static URI tryCreateUri(final String input) {
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

    /**
     * Reads the referenced file in UTF-8. Throws a friendly exception if the OS
     * says "no".
     */
    protected static String readFile(final Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read HTML file " + path, exception);
        }
    }

    /**
     * Slurps stdin until EOF. Handy for shell pipelines and impromptu magic.
     */
    protected static String readStdIn() {
        try {
            return new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read HTML from stdin", exception);
        }
    }

    /**
     * Fetches remote HTML over HTTP(S) with a polite user agent and timeout.
     */
    protected static String fetchRemote(final URI uri) {
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

    /**
     * Shared HTTP client tuned for short-lived CLI calls.
     */
    protected static HttpClient httpClient() {
        return HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }
}
