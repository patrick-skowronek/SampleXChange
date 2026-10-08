package de.samply.samplexchange.terminology;

import de.samply.samplexchange.configuration.TargetFormat;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class CodeMap {
    private final String resource;
    private final String keyColumn;
    private final Map<String, Map<TargetFormat, String>> rows;
    private final Set<String> unmatched = new LinkedHashSet<>();

    private CodeMap(String resource, String keyColumn, Map<String, Map<TargetFormat, String>> rows) {
        this.resource = resource;
        this.keyColumn = keyColumn;
        this.rows = rows;
    }

    public static CodeMap load(String resource, String keyColumn) {
        List<String[]> lines = TableReader.readRows(resource);
        if (lines.isEmpty()) {
            throw new IllegalStateException("Terminology table " + resource + " is empty");
        }

        String[] header = lines.get(0);
        if (!header[0].equals(keyColumn)) {
            throw new IllegalStateException(
                    "Terminology table %s must start with column %s, found %s"
                            .formatted(resource, keyColumn, header[0]));
        }
        Map<TargetFormat, Integer> columns = TableReader.findColumnForEachTarget(resource, header);

        Map<String, Map<TargetFormat, String>> rows = new LinkedHashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] cells = lines.get(i);
            if (cells.length != header.length) {
                throw new IllegalStateException(
                        "Terminology table %s row %d has %d cells, expected %d"
                                .formatted(resource, i + 1, cells.length, header.length));
            }
            Map<TargetFormat, String> byTarget = new LinkedHashMap<>();
            columns.forEach((target, index) -> {
                String value = cells[index];
                if (value.isBlank()) {
                    throw new IllegalStateException(
                            "Terminology table %s has an empty %s value for key %s"
                                    .formatted(resource, target.column(), cells[0]));
                }
                byTarget.put(target, value);
            });
            if (rows.putIfAbsent(cells[0], byTarget) != null) {
                throw new IllegalStateException(
                        "Terminology table %s maps %s twice".formatted(resource, cells[0]));
            }
        }

        log.debug("Loaded {} rows from {} for {}", rows.size(), resource, columns.keySet());
        return new CodeMap(resource, keyColumn, rows);
    }

    public Optional<String> findCodeFor(TargetFormat target, String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return Optional.empty();
        }
        Map<TargetFormat, String> row = rows.get(sourceCode);
        if (row == null) {
            unmatched.add(sourceCode);
            return Optional.empty();
        }
        return Optional.ofNullable(row.get(target));
    }

    public Set<String> codesWithNoRow() {
        return Set.copyOf(unmatched);
    }

    public int size() {
        return rows.size();
    }

    @Override
    public String toString() {
        return "CodeMap[%s, key=%s, rows=%d]".formatted(resource, keyColumn, rows.size());
    }

    static final class TableReader {
        private TableReader() {
        }

        static List<String[]> readRows(String resource) {
            try (InputStream in = CodeMap.class.getClassLoader().getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IllegalStateException("Terminology table not found: " + resource);
                }
                List<String[]> rows = new ArrayList<>();
                try (BufferedReader reader =
                             new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                            continue;
                        }
                        rows.add(Arrays.stream(trimmed.split(",", -1)).map(String::trim)
                                .toArray(String[]::new));
                    }
                }
                return rows;
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read terminology table " + resource, e);
            }
        }

        static Map<TargetFormat, Integer> findColumnForEachTarget(String resource, String[] header) {
            Map<TargetFormat, Integer> columns = new LinkedHashMap<>();
            for (TargetFormat target : TargetFormat.values()) {
                int index = -1;
                for (int i = 0; i < header.length; i++) {
                    if (header[i].equals(target.column())) {
                        index = i;
                        break;
                    }
                }
                if (index < 0) {
                    throw new IllegalStateException(
                            "Terminology table %s has no column %s".formatted(resource, target.column()));
                }
                columns.put(target, index);
            }
            return columns;
        }
    }
}
