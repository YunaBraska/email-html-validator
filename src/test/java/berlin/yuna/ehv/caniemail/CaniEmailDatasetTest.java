package berlin.yuna.ehv.caniemail;

import berlin.yuna.typemap.logic.JsonEncoder;
import berlin.yuna.typemap.model.TypeMap;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CaniEmailDatasetTest {

    private static final Path FEATURES_SOURCE = Path.of("src", "test", "resources", "caniemail", "_features");
    private static final Path GENERATED_DATASET = Path.of("target", "generated-resources", "caniemail", "features-database.json");
    private static final Path PACKAGED_DATASET = Path.of("src", "main", "resources", "caniemail", "features-database.json");

    @Test
    void shouldGenerateDatasetFromMarkdownSources() throws IOException {
        var dataset = buildDataset();
        var generatedJson = JsonEncoder.toJson(dataset);

        Files.createDirectories(GENERATED_DATASET.getParent());
        Files.writeString(GENERATED_DATASET, generatedJson, StandardCharsets.UTF_8);

        var packagedJson = Files.readString(PACKAGED_DATASET, StandardCharsets.UTF_8).trim();

        var generatedMap = new TypeMap(generatedJson);
        var packagedMap = new TypeMap(packagedJson);

        assertEquals(packagedMap, generatedMap, () ->
            """
                Packaged CaniEmail dataset is outdated.
                Generated snapshot written to %s
                Copy it to %s to update the knowledge base.
                """.formatted(GENERATED_DATASET, PACKAGED_DATASET).strip()
        );
    }

    private static Map<String, Object> buildDataset() throws IOException {
        var dataset = new LinkedHashMap<String, Object>();
        try (var stream = Files.list(FEATURES_SOURCE)) {
            var featureFiles = stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".md"))
                .sorted()
                .toList();

            for (Path featureFile : featureFiles) {
                var slug = slugOf(featureFile);
                var frontMatter = parseFrontMatter(featureFile);
                frontMatter.put("slug", slug);
                dataset.put(slug, frontMatter);
            }
        }
        return dataset;
    }

    private static String slugOf(Path featureFile) {
        var name = featureFile.getFileName().toString();
        int extensionIndex = name.lastIndexOf('.');
        return extensionIndex > 0 ? name.substring(0, extensionIndex) : name;
    }

    private static LinkedHashMap<String, Object> parseFrontMatter(Path featureFile) throws IOException {
        var lines = Files.readAllLines(featureFile, StandardCharsets.UTF_8);
        int start = findDelimiterIndex(lines, 0);
        if (start == -1) {
            throw new IllegalStateException("Missing front matter start delimiter in " + featureFile);
        }
        int end = findDelimiterIndex(lines, start + 1);
        if (end == -1) {
            throw new IllegalStateException("Missing front matter end delimiter in " + featureFile);
        }
        var frontMatterLines = lines.subList(start + 1, end);
        var parser = new FrontMatterParser(String.join("\n", frontMatterLines));
        return parser.parse();
    }

    private static int findDelimiterIndex(List<String> lines, int fromIndex) {
        for (int index = fromIndex; index < lines.size(); index++) {
            if ("---".equals(lines.get(index).trim())) {
                return index;
            }
        }
        return -1;
    }

    private static final class FrontMatterParser {
        private final String data;
        private int index;

        private FrontMatterParser(String data) {
            this.data = data;
        }

        LinkedHashMap<String, Object> parse() {
            var map = new LinkedHashMap<String, Object>();
            while (true) {
                skipWhitespace();
                if (index >= data.length()) {
                    break;
                }
                var key = readKey();
                if (key.isEmpty()) {
                    break;
                }
                skipWhitespace();
                requireColon(key);
                var value = parseValue();
                map.put(key, value);
            }
            return map;
        }

        private Object parseValue() {
            skipWhitespace();
            if (index >= data.length()) {
                return "";
            }
            char current = data.charAt(index);
            if (current == '"') {
                return parseQuotedString();
            }
            if (current == '{') {
                index++;
                return parseObject();
            }
            if (current == '[') {
                index++;
                return parseArray();
            }
            if (current == '\n' || current == '\r') {
                return "";
            }
            var builder = new StringBuilder();
            while (index < data.length()) {
                current = data.charAt(index);
                if (current == '\n' || current == '\r') {
                    break;
                }
                builder.append(current);
                index++;
            }
            return builder.toString().trim();
        }

        private LinkedHashMap<String, Object> parseObject() {
            var map = new LinkedHashMap<String, Object>();
            while (true) {
                skipWhitespace();
                if (index >= data.length()) {
                    break;
                }
                char current = data.charAt(index);
                if (current == '}') {
                    index++;
                    break;
                }
                var key = readKey();
                skipWhitespace();
                requireColon(key);
                var value = parseValue();
                map.put(key, value);
                skipWhitespace();
                if (index < data.length() && data.charAt(index) == ',') {
                    index++;
                }
            }
            return map;
        }

        private List<Object> parseArray() {
            var list = new ArrayList<Object>();
            while (true) {
                skipWhitespace();
                if (index >= data.length()) {
                    break;
                }
                char current = data.charAt(index);
                if (current == ']') {
                    index++;
                    break;
                }
                list.add(parseValue());
                skipWhitespace();
                if (index < data.length() && data.charAt(index) == ',') {
                    index++;
                }
            }
            return list;
        }

        private String readKey() {
            skipWhitespace();
            if (index >= data.length()) {
                return "";
            }
            if (data.charAt(index) == '"') {
                return parseQuotedString();
            }
            int start = index;
            while (index < data.length()) {
                char current = data.charAt(index);
                if (current == ':' || current == '\n' || current == '\r') {
                    break;
                }
                index++;
            }
            return data.substring(start, index).trim();
        }

        private String parseQuotedString() {
            if (data.charAt(index) != '"') {
                throw new IllegalStateException("Expected quote at position " + index);
            }
            index++;
            var builder = new StringBuilder();
            while (index < data.length()) {
                char current = data.charAt(index++);
                if (current == '\\' && index < data.length()) {
                    builder.append(data.charAt(index++));
                } else if (current == '"') {
                    break;
                } else {
                    builder.append(current);
                }
            }
            return builder.toString();
        }

        private void skipWhitespace() {
            while (index < data.length()) {
                char current = data.charAt(index);
                if (current == ' ' || current == '\t' || current == '\n' || current == '\r') {
                    index++;
                } else {
                    break;
                }
            }
        }

        private void requireColon(String key) {
            skipWhitespace();
            if (index >= data.length()) {
                throw new IllegalStateException("Expected ':' after key '" + key + "' but reached end of data");
            }
            if (data.charAt(index) != ':') {
                throw new IllegalStateException("Expected ':' after key '" + key + "' at index " + index + " but found " + data.charAt(index));
            }
            index++;
        }
    }
}
