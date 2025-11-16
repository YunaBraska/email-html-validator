package org.nanonative.validation;

import berlin.yuna.typemap.model.TypeMap;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.nanonative.caniemail.CaniEmailFeatureDatabase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

class ValidatorHelper {

    private static final Pattern PROPERTY_SPLIT = Pattern.compile(";+");
    private static final Pattern DECLARED_TAG_PATTERN = Pattern.compile("<\\s*/?\\s*([a-zA-Z0-9:-]+)");
    private static final Pattern MEDIA_PATTERN = Pattern.compile("@media\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEDIA_DEVICE_PIXEL_PATTERN = Pattern.compile("@media[^\\{]*-webkit-device-pixel-ratio", Pattern.CASE_INSENSITIVE);
    private static final Set<String> IGNORED_TAGS = Set.of("#root");

    private ValidatorHelper() {
    }

    static TypeMap buildLookup() {
        var lookup = new TypeMap();
        for (Map.Entry<String, TypeMap> entry : CaniEmailFeatureDatabase.lookup().entrySet()) {
            lookup.put(entry.getKey(), entry.getValue());
        }
        return lookup;
    }

    static List<String> listElements(final String html) {
        var tokens = new ArrayList<String>();
        var declaredTags = declaredTags(html);
        var document = Jsoup.parse(html);
        for (Element element : document.getAllElements()) {
            var tagName = element.tagName().toLowerCase(Locale.ROOT);
            if (IGNORED_TAGS.contains(tagName)) {
                continue;
            }
            if (!shouldIncludeTag(tagName, declaredTags)) {
                continue;
            }
            tokens.add("tag:" + tagName);
            element.attributes().forEach(attribute -> {
                var key = attribute.getKey().toLowerCase(Locale.ROOT);
                tokens.add("attribute:" + key);
                if ("style".equals(key)) {
                    tokens.addAll(parseCss(attribute.getValue()));
                    collectAtRules(attribute.getValue(), tokens);
                }
            });
            if ("style".equalsIgnoreCase(element.tagName())) {
                tokens.addAll(parseCss(element.data()));
                collectAtRules(element.data(), tokens);
            }
        }
        return tokens;
    }

    private static boolean shouldIncludeTag(final String tagName, final Set<String> declaredTags) {
        if (declaredTags.isEmpty()) {
            return false;
        }
        return declaredTags.contains(tagName);
    }

    private static Set<String> declaredTags(final String html) {
        if (html == null || html.isBlank()) {
            return Set.of();
        }
        var tags = new HashSet<String>();
        var matcher = DECLARED_TAG_PATTERN.matcher(html);
        while (matcher.find()) {
            var name = matcher.group(1);
            if (name == null || name.isBlank()) {
                continue;
            }
            var normalized = name.toLowerCase(Locale.ROOT);
            if (normalized.startsWith("!") || normalized.startsWith("?")) {
                continue;
            }
            tags.add(normalized);
        }
        return tags;
    }

    private static List<String> parseCss(final String cssBlock) {
        if (cssBlock == null || cssBlock.isBlank()) {
            return List.of();
        }
        return PROPERTY_SPLIT.splitAsStream(cssBlock)
            .map(String::trim)
            .filter(chunk -> !chunk.isEmpty() && chunk.contains(":"))
            .map(chunk -> chunk.substring(0, chunk.indexOf(':')).trim().toLowerCase(Locale.ROOT))
            .filter(property -> !property.isEmpty())
            .map(property -> "css:" + property)
            .toList();
    }

    private static void collectAtRules(final String cssBlock, final List<String> tokens) {
        if (cssBlock == null || cssBlock.isBlank()) {
            return;
        }
        var lower = cssBlock.toLowerCase(Locale.ROOT);
        if (MEDIA_PATTERN.matcher(lower).find()) {
            tokens.add("css:at-media");
        }
        if (MEDIA_DEVICE_PIXEL_PATTERN.matcher(lower).find()) {
            tokens.add("css:at-media-device-pixel-ratio");
        }
    }
}
