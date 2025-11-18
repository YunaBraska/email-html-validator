package berlin.yuna.ehv.validation;

import berlin.yuna.typemap.model.TypeMap;
import berlin.yuna.ehv.caniemail.CaniEmailFeatureDatabase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static berlin.yuna.ehv.cli.EmailHtmlValidatorCli.PLAYWRIGHT_VERSION;

/**
 * Weighs HTML features against the Can I Email dataset and keeps score. If
 * something looks suspicious, this class tattles via TypeMap.
 */
public class HtmlValidator {

    public static final String FIELD_TOTAL = "totalFeatures";
    public static final String FIELD_PARTIAL_NOTES = "partialNotes";
    public static final String FIELD_UNKNOWN = "unknownFeatures";
    public static final String FIELD_PARTIAL_CLIENTS = "partialClients";
    public static final String FIELD_REJECTED_CLIENTS = "rejectedClients";
    public static final String FIELD_FEATURE_COUNT = "featureCount";
    public static final String FIELD_CLIENT_COUNT = "clientCount";
    public static final String FIELD_OPERATING_SYSTEM_COUNT = "operatingSystemCount";
    public static final String FIELD_REFERENCE_URL = "caniemailUrl";
    public static final String FIELD_BFSG_STATUS = "bfsgStatus";
    public static final String FIELD_BFSG_ISSUES = "bfsgIssues";
    public static final String FIELD_BFSG_ISSUE_COUNT = "bfsgIssueCount";
    public static final String BFSG_STATUS_ERROR = BfsgComplianceValidator.STATUS_ERROR;
    public static final String FIELD_PLAYWRIGHT_VERSION = "playwrightVersion";
    public static final String FIELD_IGNORED_FEATURES = "ignoredFeatures";
    public static final String FIELD_IGNORED_COUNT = "ignoredFeatureCount";
    public static final String LEVEL_ACCEPTED = "accepted";
    public static final String LEVEL_PARTIAL = "partial";
    public static final String LEVEL_REJECTED = "rejected";
    private static final String LEVEL_UNKNOWN = "unknown";
    private static final String FIELD_STATS = "stats";
    private static final String FIELD_NOTES_BY_NUM = "notes_by_num";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_NOTES = "notes";

    private static final TypeMap FEATURE_LOOKUP = ValidatorHelper.buildLookup();
    public static final String WWW_CANIEMAIL_COM = "https://www.caniemail.com";
    public static final List<String> DEFAULT_IGNORED_SLUGS = List.of("tag:html", "tag:head", "tag:body");

    /**
     * Utility class – existence is optional, instantiation is forbidden.
     */
    protected HtmlValidator() {
    }

    public static TypeMap validate(final String html) {
        return validate(html, false);
    }

    public static TypeMap validate(final String html, final boolean includeBfsg) {
        return validate(html, includeBfsg, List.of());
    }

    public static TypeMap validate(final String html, final boolean includeBfsg, final List<String> bfsgTags) {
        Objects.requireNonNull(html, "html");
        final var normalizedTags = bfsgTags == null ? List.<String>of() : bfsgTags;
        final var partialNotes = new TypeMap();
        final var unknown = new ArrayList<String>();
        final var weightedTotals = new BigDecimal[]{zero(), zero(), zero()};
        long weightedFeatures = 0;
        final Set<String> partialClients = new TreeSet<>();
        final Set<String> rejectedClients = new TreeSet<>();

        var features = ValidatorHelper.listElements(html).stream()
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .distinct()
                .toList();
        long total = features.size();

        for (String feature : features) {
            var entry = new TypeMap(FEATURE_LOOKUP.asMap(feature));
            if (entry.isEmpty()) {
                entry = fallbackEntry(feature);
            }
            if (entry.isEmpty()) {
                unknown.add(feature);
                continue;
            }
            var evaluation = evaluateFeature(entry);
            var status = evaluation.asString(FIELD_STATUS);
            if (LEVEL_UNKNOWN.equals(status)) {
                unknown.add(feature);
            } else if (!LEVEL_ACCEPTED.equals(status) && !LEVEL_REJECTED.equals(status)) {
                var notes = evaluation.asList(String.class, FIELD_NOTES);
                if (!notes.isEmpty()) {
                    partialNotes.put(feature, notes);
                }
            }

            var stats = entry.asMap(FIELD_STATS);
            var clients = clientStatuses(stats);
            if (!clients.isEmpty()) {
                long acceptedClients = clients.values().stream().filter(LEVEL_ACCEPTED::equals).count();
                long partialClientsCount = clients.values().stream().filter(LEVEL_PARTIAL::equals).count();
                long rejectedClientsCount = clients.values().stream().filter(LEVEL_REJECTED::equals).count();
                long totalClients = acceptedClients + partialClientsCount + rejectedClientsCount;
                if (totalClients > 0) {
                    weightedFeatures++;
                    var totalDecimal = BigDecimal.valueOf(totalClients);
                    weightedTotals[0] = weightedTotals[0].add(BigDecimal.valueOf(acceptedClients).divide(totalDecimal, 8, RoundingMode.HALF_UP));
                    weightedTotals[1] = weightedTotals[1].add(BigDecimal.valueOf(partialClientsCount).divide(totalDecimal, 8, RoundingMode.HALF_UP));
                    weightedTotals[2] = weightedTotals[2].add(BigDecimal.valueOf(rejectedClientsCount).divide(totalDecimal, 8, RoundingMode.HALF_UP));
                }
                clients.forEach((clientName, clientStatus) -> {
                    if (LEVEL_PARTIAL.equals(clientStatus)) {
                        partialClients.add(clientName);
                    } else if (LEVEL_REJECTED.equals(clientStatus)) {
                        rejectedClients.add(clientName);
                    }
                });
            }
        }

        var percentages = weightedFeatures == 0
                ? new BigDecimal[]{zero(), zero(), zero()}
                : percentages(weightedTotals, weightedFeatures);
        var report = new TypeMap();
        report.put(FIELD_TOTAL, total);
        report.put(LEVEL_ACCEPTED, percentages[0]);
        report.put(LEVEL_PARTIAL, percentages[1]);
        report.put(LEVEL_REJECTED, percentages[2]);
        report.put(FIELD_PARTIAL_NOTES, partialNotes);
        report.put(FIELD_UNKNOWN, unknown);
        report.put(FIELD_PARTIAL_CLIENTS, new ArrayList<>(partialClients));
        report.put(FIELD_REJECTED_CLIENTS, new ArrayList<>(rejectedClients));
        report.put(FIELD_REFERENCE_URL, WWW_CANIEMAIL_COM);
        report.put(FIELD_PLAYWRIGHT_VERSION, PLAYWRIGHT_VERSION);
        report.put(FIELD_FEATURE_COUNT, CaniEmailFeatureDatabase.featureCount());
        report.put(FIELD_CLIENT_COUNT, CaniEmailFeatureDatabase.clientCount());
        report.put(FIELD_OPERATING_SYSTEM_COUNT, CaniEmailFeatureDatabase.operatingSystemCount());
        if (includeBfsg) {
            var bfsgResult = BfsgComplianceValidator.evaluate(html, normalizedTags);
            report.put(FIELD_BFSG_STATUS, bfsgResult.status());
            report.put(FIELD_BFSG_ISSUE_COUNT, bfsgResult.issues().size());
            report.put(FIELD_BFSG_ISSUES, bfsgResult.issues());
        }
        return report;
    }

    /**
     * Computes accepted/partial/rejected ratios across all inspected features.
     */
    protected static BigDecimal[] percentages(final BigDecimal[] totals, final long count) {
        var denominator = BigDecimal.valueOf(count);
        return new BigDecimal[]{
                percentage(totals[0], denominator),
                percentage(totals[1], denominator),
                percentage(totals[2], denominator)
        };
    }

    /**
     * Converts a dataset feature entry into a simplified result with notes.
     */
    protected static TypeMap evaluateFeature(final TypeMap entry) {
        var result = new TypeMap();
        result.put(FIELD_STATUS, LEVEL_UNKNOWN);
        var counts = new TypeMap();
        accumulate(entry.asMap(FIELD_STATS), counts);
        result.put(FIELD_STATUS, classify(counts));
        var notes = collectNotes(entry.asMap(FIELD_NOTES_BY_NUM));
        if (!notes.isEmpty()) {
            result.put(FIELD_NOTES, notes);
        }
        return result;
    }

    /**
     * Walks a nested stats structure and tallies acceptance levels.
     */
    protected static void accumulate(final Map<?, ?> node, final TypeMap counts) {
        for (Object value : node.values()) {
            accumulateNode(value, counts);
        }
    }

    protected static void accumulateNode(final Object node, final TypeMap counts) {
        switch (node) {
            case Map<?, ?> map -> map.values().forEach(value -> accumulateNode(value, counts));
            case List<?> list -> list.forEach(value -> accumulateNode(value, counts));
            default -> accumulateValue(node, counts);
        }
    }

    protected static void accumulateValue(final Object value, final TypeMap counts) {
        if (value == null) {
            return;
        }
        var code = String.valueOf(value).trim();
        if (code.isEmpty()) {
            increment(counts, LEVEL_PARTIAL);
            return;
        }
        switch (Character.toLowerCase(code.charAt(0))) {
            case 'y' -> increment(counts, LEVEL_ACCEPTED);
            case 'n' -> increment(counts, LEVEL_REJECTED);
            default -> increment(counts, LEVEL_PARTIAL);
        }
    }

    protected static void increment(final TypeMap counts, final String key) {
        var current = counts.asLongOpt(key).orElse(0L);
        counts.put(key, current + 1);
    }

    /**
     * Determines whether a feature is accepted, partial, rejected, or unknown.
     */
    protected static String classify(final TypeMap counts) {
        var full = counts.asLongOpt(LEVEL_ACCEPTED).orElse(0L);
        var partial = counts.asLongOpt(LEVEL_PARTIAL).orElse(0L);
        var none = counts.asLongOpt(LEVEL_REJECTED).orElse(0L);
        if (full > 0 && partial == 0 && none == 0) {
            return LEVEL_ACCEPTED;
        }
        if (none > 0 && full == 0) {
            return LEVEL_REJECTED;
        }
        return LEVEL_PARTIAL;
    }

    protected static List<String> collectNotes(final Map<?, ?> notesByNum) {
        if (notesByNum == null || notesByNum.isEmpty()) {
            return List.of();
        }
        return notesByNum.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .map(entry -> String.valueOf(entry.getValue()))
                .toList();
    }

    /**
     * Flattens the dataset matrix into platform:client -> status entries.
     */
    protected static Map<String, String> clientStatuses(final Map<?, ?> stats) {
        var statuses = new LinkedHashMap<String, String>();
        stats.forEach((clientName, payload) -> {
            if (payload instanceof Map<?, ?> platforms) {
                platforms.forEach((platform, data) -> statuses.put(formatClient(String.valueOf(platform), String.valueOf(clientName)), classifyStats(data)));
            } else {
                statuses.put(formatClient("generic", String.valueOf(clientName)), classifyStats(payload));
            }
        });
        return statuses;
    }

    protected static String classifyStats(final Object payload) {
        var counts = new TypeMap();
        accumulateNode(payload, counts);
        return classify(counts);
    }

    protected static BigDecimal percentage(final BigDecimal share, final BigDecimal denominator) {
        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return zero();
        }
        return share.divide(denominator, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    protected static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Provides synthetic entries for common plain HTML constructs.
     */
    protected static TypeMap fallbackEntry(final String feature) {
        return switch (feature) {
            case "attribute:style" -> syntheticEntry("html-inline-style", "Inline style attribute");
            case "attribute:class" -> syntheticEntry("html-class-attribute", "Class attribute");
            case "css:color" -> syntheticEntry("css-color", "color");
            default -> new TypeMap();
        };
    }

    protected static String formatClient(final String platform, final String client) {
        return sanitize(platform) + ":" + sanitize(client);
    }

    protected static String sanitize(final String value) {
        return value == null ? "unknown" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
    }

    protected static TypeMap syntheticEntry(final String slug, final String title) {
        var stats = new TypeMap();
        stats.put("synthetic", Map.of("global", Map.of("2024-01", "y")));
        var entry = new TypeMap();
        entry.put("slug", slug);
        entry.put("title", title);
        entry.put(FIELD_STATS, stats);
        return entry;
    }

    public static TypeMap ignoreSlugs(final TypeMap report, final List<String> slugs) {
        if (report == null || slugs == null || slugs.isEmpty()) {
            return report;
        }
        var normalized = slugs.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (normalized.isEmpty()) {
            return report;
        }
        var ignored = new ArrayList<String>();
        var unknown = report.asList(String.class, FIELD_UNKNOWN);
        if (!unknown.isEmpty()) {
            var filtered = unknown.stream()
                    .map(value -> value == null ? "" : value)
                    .filter(value -> {
                        var matches = normalized.contains(value.toLowerCase(Locale.ROOT));
                        if (matches) {
                            ignored.add(value);
                        }
                        return !matches;
                    })
                    .toList();
            report.put(FIELD_UNKNOWN, filtered);
        }
        var partialNotes = report.asMap(FIELD_PARTIAL_NOTES);
        if (!partialNotes.isEmpty()) {
            var keys = new ArrayList<>(partialNotes.keySet());
            for (Object key : keys) {
                var slug = String.valueOf(key);
                if (normalized.contains(slug.toLowerCase(Locale.ROOT))) {
                    ignored.add(slug);
                    partialNotes.remove(key);
                }
            }
        }
        if (!ignored.isEmpty()) {
            report.put(FIELD_IGNORED_FEATURES, List.copyOf(ignored));
            report.put(FIELD_IGNORED_COUNT, ignored.size());
        }
        return report;
    }
}
