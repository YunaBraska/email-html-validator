package org.nanonative.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//./mvnw -Dsync.playwright=true -Dtest=org.nanonative.cli.PlaywrightVersionAlignmentTest test
@EnabledIfSystemProperty(named = "sync.playwright", matches = "true")
class PlaywrightVersionAlignmentTest {

    @Test
    void shouldAlignPlaywrightVersionWithDequeDependency() throws IOException {
        var dequeVersion = detectDequePlaywrightVersion();
        var pomPath = Path.of("pom.xml");
        var original = Files.readString(pomPath, StandardCharsets.UTF_8);
        var updated = updatePlaywrightDependencies(original, dequeVersion);
        if (!original.equals(updated))
            Files.writeString(pomPath, updated, StandardCharsets.UTF_8);
    }

    private static String detectDequePlaywrightVersion() throws IOException {
        var resource = "META-INF/maven/com.deque.html.axe-core/playwright/pom.xml";
        try (var stream = PlaywrightVersionAlignmentTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Unable to locate axe-core Playwright POM (" + resource + ")");
            }
            var pomXml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            var matcher = Pattern.compile(
                "<dependency>\\s*<groupId>com\\.microsoft\\.playwright</groupId>\\s*<artifactId>playwright</artifactId>\\s*<version>([^<]+)</version>",
                Pattern.DOTALL
            ).matcher(pomXml);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
            throw new IllegalStateException("axe-core Playwright module does not declare com.microsoft.playwright dependency");
        }
    }

    private static String updatePlaywrightDependencies(final String pom, final String version) {
        var updated = replaceDependencyVersion(pom, "playwright", version);
        updated = replaceDependencyVersion(updated, "driver", version);
        updated = replaceDependencyVersion(updated, "driver-bundle", version);
        return updated;
    }

    private static String replaceDependencyVersion(final String pom, final String artifactId, final String version) {
        var pattern = Pattern.compile(
            "(<groupId>com\\.microsoft\\.playwright</groupId>\\s*<artifactId>" + Pattern.quote(artifactId)
                + "</artifactId>\\s*<version>)([^<]+)(</version>)",
            Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(pom);
        if (!matcher.find()) {
            return pom;
        }
        var current = matcher.group(2).trim();
        if (current.equals(version)) {
            return pom;
        }
        return matcher.replaceFirst(matcher.group(1) + version + matcher.group(3));
    }
}
