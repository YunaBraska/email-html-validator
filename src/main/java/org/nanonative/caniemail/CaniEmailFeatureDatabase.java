package org.nanonative.caniemail;

import berlin.yuna.typemap.model.TypeMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CaniEmailFeatureDatabase {

    private static final TypeMap RAW_DATASET = loadDataset();
    private static final Map<String, TypeMap> FEATURE_LOOKUP = buildLookup();
    private static final int KNOWN_FEATURE_COUNT;
    private static final int KNOWN_CLIENT_COUNT;
    private static final int KNOWN_OS_COUNT;
    private static final List<String> KNOWN_CLIENTS;
    private static final List<String> KNOWN_OPERATING_SYSTEMS;

    static {
        KNOWN_FEATURE_COUNT = RAW_DATASET.size();
        var clients = new LinkedHashSet<String>();
        var os = new LinkedHashSet<String>();
        RAW_DATASET.values().forEach(value -> {
            var entry = toTypeMap(value);
            entry.asMap("stats").forEach((client, payload) -> collectClientInfo(String.valueOf(client), payload, clients, os));
        });
        KNOWN_CLIENTS = List.copyOf(clients);
        KNOWN_OPERATING_SYSTEMS = List.copyOf(os);
        KNOWN_CLIENT_COUNT = KNOWN_CLIENTS.size();
        KNOWN_OS_COUNT = KNOWN_OPERATING_SYSTEMS.size();
    }

    private CaniEmailFeatureDatabase() {
    }

    public static Map<String, TypeMap> lookup() {
        return FEATURE_LOOKUP;
    }

    public static int featureCount() {
        return KNOWN_FEATURE_COUNT;
    }

    public static int clientCount() {
        return KNOWN_CLIENT_COUNT;
    }

    public static int operatingSystemCount() {
        return KNOWN_OS_COUNT;
    }

    public static List<String> operatingSystems() {
        return KNOWN_OPERATING_SYSTEMS;
    }

    private static Map<String, TypeMap> buildLookup() {
        var lookup = new LinkedHashMap<String, TypeMap>();
        for (Map.Entry<Object, Object> entry : CaniEmailFeatureDatabase.RAW_DATASET.entrySet()) {
            var slug = String.valueOf(entry.getKey());
            var typeMap = toTypeMap(entry.getValue());
            typeMap.putIfAbsent("slug", slug);
            deriveFeatureNames(slug, typeMap).forEach(name -> lookup.put(name, typeMap));
        }
        return lookup;
    }

    private static void collectClientInfo(final String client, final Object payload, final LinkedHashSet<String> clients, final LinkedHashSet<String> os) {
        clients.add(client.toLowerCase(Locale.ROOT));
        if (payload instanceof Map<?, ?> map) {
            map.keySet().forEach(platform -> os.add(String.valueOf(platform).toLowerCase(Locale.ROOT)));
        }
    }

    private static List<String> deriveFeatureNames(final String slug, final TypeMap entry) {
        var names = new ArrayList<String>();
        var category = entry.asStringOpt("category").orElse("");
        var title = entry.asStringOpt("title").orElse(slug);
        if ("css".equalsIgnoreCase(category)) {
            names.add("css:" + cleanPrefix(slug, "css-"));
            return names;
        }
        if ("html".equalsIgnoreCase(category)) {
            if (looksLikeAttribute(title, slug)) {
                determineAttributeNames(slug, title).forEach(name -> names.add("attribute:" + name));
            } else {
                determineTagNames(slug, title).forEach(name -> names.add("tag:" + name));
            }
            return names;
        }
        names.add("feature:" + slug);
        return names;
    }

    private static List<String> determineAttributeNames(final String slug, final String title) {
        var names = extractBacktickNames(title);
        if (!names.isEmpty()) {
            return names;
        }
        return List.of(cleanAttributeName(slug));
    }

    private static List<String> determineTagNames(final String slug, final String title) {
        var names = extractTagNames(title);
        if (!names.isEmpty()) {
            return names;
        }
        var cleaned = cleanPrefix(slug, "html-");
        var headings = expandHeadingRange(cleaned);
        if (!headings.isEmpty()) {
            return headings;
        }
        return List.of(cleaned);
    }

    private static boolean looksLikeAttribute(final String title, final String slug) {
        var normalizedTitle = title.toLowerCase(Locale.ROOT);
        var normalizedSlug = slug.toLowerCase(Locale.ROOT);
        return normalizedTitle.contains("attribute")
                || normalizedSlug.contains("attribute")
                || normalizedSlug.contains("aria-")
                || normalizedSlug.contains("data-");
    }

    private static List<String> extractTagNames(final String title) {
        var names = new LinkedHashSet<String>();
        int index = 0;
        while (index < title.length()) {
            char current = title.charAt(index);
            if (current == '<') {
                int cursor = index + 1;
                if (cursor < title.length() && title.charAt(cursor) == '/') {
                    cursor++;
                }
                int start = cursor;
                while (cursor < title.length()) {
                    char ch = title.charAt(cursor);
                    if (Character.isLetterOrDigit(ch) || ch == '-' || ch == ':') {
                        cursor++;
                    } else if (ch == '>') {
                        break;
                    } else {
                        break;
                    }
                }
                if (cursor > start) {
                    var tag = title.substring(start, cursor).toLowerCase(Locale.ROOT);
                    names.add(tag);
                }
                index = cursor;
            } else {
                index++;
            }
        }
        return List.copyOf(names);
    }

    private static List<String> extractBacktickNames(final String text) {
        var names = new LinkedHashSet<String>();
        int index = 0;
        while (index < text.length()) {
            if (text.charAt(index) == '`') {
                int cursor = index + 1;
                while (cursor < text.length() && text.charAt(cursor) != '`') {
                    cursor++;
                }
                if (cursor < text.length()) {
                    var name = text.substring(index + 1, cursor).toLowerCase(Locale.ROOT);
                    if (!name.isBlank()) {
                        names.add(name);
                    }
                    index = cursor + 1;
                } else {
                    break;
                }
            } else {
                index++;
            }
        }
        return List.copyOf(names);
    }

    private static List<String> expandHeadingRange(final String value) {
        int dashIndex = value.indexOf('-');
        if (dashIndex > 0 && dashIndex + 1 < value.length()) {
            var first = value.substring(0, dashIndex);
            var second = value.substring(dashIndex + 1);
            if (first.startsWith("h") && second.startsWith("h")) {
                try {
                    int start = Integer.parseInt(first.substring(1));
                    int end = Integer.parseInt(second.substring(1));
                    if (start <= end) {
                        var names = new ArrayList<String>();
                        for (int level = start; level <= end; level++) {
                            names.add("h" + level);
                        }
                        return names;
                    }
                } catch (NumberFormatException ignored) {
                    // ignored
                }
            }
        }
        return List.of();
    }

    private static String cleanAttributeName(final String slug) {
        var cleaned = cleanPrefix(slug, "html-");
        if (cleaned.endsWith("-attribute")) {
            return cleaned.substring(0, cleaned.length() - "-attribute".length());
        }
        return cleaned;
    }

    private static String cleanPrefix(final String slug, final String prefix) {
        var value = slug.startsWith(prefix) ? slug.substring(prefix.length()) : slug;
        if (value.endsWith("-element")) {
            value = value.substring(0, value.length() - "-element".length());
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static TypeMap loadDataset() {
        return new TypeMap(readResource("caniemail/features-database.json"));
    }

    private static TypeMap toTypeMap(final Object value) {
        if (value instanceof TypeMap typeMap) {
            return typeMap;
        }
        if (value instanceof Map<?, ?> map) {
            return new TypeMap(map);
        }
        if (value instanceof String json && !json.isBlank()) {
            return new TypeMap(json);
        }
        return new TypeMap();
    }

    private static String readResource(final String resourcePath) {
        try (InputStream stream = classLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Missing resource: " + resourcePath);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read resource " + resourcePath, exception);
        }
    }

    private static ClassLoader classLoader() {
        var loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = CaniEmailFeatureDatabase.class.getClassLoader();
        }
        return loader;
    }
}
