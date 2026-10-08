package de.samply.samplexchange.terminology;

import de.samply.samplexchange.configuration.TargetFormat;
import de.samply.samplexchange.domain.TemperatureRange;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class TemperatureTable {
    private static final String CLOSEST_FOR_COLUMN = "closest_for";

    private final List<Row> rows;
    private final Set<String> warnedAbout = ConcurrentHashMap.newKeySet();

    private TemperatureTable(List<Row> rows) {
        this.rows = rows;
    }

    public static TemperatureTable load(String resource) {
        List<String[]> lines = CodeMap.TableReader.readRows(resource);
        if (lines.isEmpty()) {
            throw new IllegalStateException("Terminology table " + resource + " is empty");
        }
        String[] header = lines.get(0);
        if (!header[0].equals("low") || !header[1].equals("high")) {
            throw new IllegalStateException(
                    "Terminology table " + resource + " must start with columns low,high");
        }
        Map<TargetFormat, Integer> columns = CodeMap.TableReader.findColumnForEachTarget(resource, header);
        int closestForColumn = Arrays.asList(header).indexOf(CLOSEST_FOR_COLUMN);

        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] cells = lines.get(i);
            if (cells.length != header.length) {
                throw new IllegalStateException(
                        "Terminology table %s row %d has %d cells, expected %d"
                                .formatted(resource, i + 1, cells.length, header.length));
            }
            long low = Long.parseLong(cells[0]);
            long high = Long.parseLong(cells[1]);
            if (low > high) {
                throw new IllegalStateException(
                        "Terminology table %s row %d has low %d above high %d"
                                .formatted(resource, i + 1, low, high));
            }
            Map<TargetFormat, String> byTarget = new LinkedHashMap<>();
            int rowNumber = i + 1;
            columns.forEach((target, index) -> {
                if (cells[index].isBlank()) {
                    throw new IllegalStateException(
                            "Terminology table %s row %d has an empty %s value"
                                    .formatted(resource, rowNumber, target.column()));
                }
                byTarget.put(target, cells[index]);
            });
            Set<TargetFormat> closestFor = closestForColumn < 0
                    ? Set.of() : parseTargets(resource, rowNumber, cells[closestForColumn]);
            rows.add(new Row(low, high, byTarget, closestFor));
        }
        log.debug("Loaded {} temperature rows from {}", rows.size(), resource);
        return new TemperatureTable(rows);
    }

    public Optional<String> findBucketFor(TargetFormat target, TemperatureRange range) {
        if (range == null || !range.isComplete()) {
            return Optional.empty();
        }
        for (Row row : rows) {
            if (range.high() <= row.high() && range.low() >= row.low()) {
                String code = row.byTarget().get(target);
                if (row.closestFor().contains(target)
                        && warnedAbout.add(target + " " + range.low() + ".." + range.high())) {
                    log.warn("Storage temperature {} to {} C has no exact {} bucket, using the closest, {}",
                            range.low(), range.high(), target, code);
                }
                return Optional.ofNullable(code);
            }
        }
        return Optional.empty();
    }

    public int size() {
        return rows.size();
    }

    private static Set<TargetFormat> parseTargets(String resource, int rowNumber, String cell) {
        Set<TargetFormat> targets = EnumSet.noneOf(TargetFormat.class);
        for (String column : cell.split(";")) {
            if (column.isBlank()) {
                continue;
            }
            targets.add(Arrays.stream(TargetFormat.values())
                    .filter(target -> target.column().equals(column.trim()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Terminology table %s row %d names unknown target %s in %s"
                                    .formatted(resource, rowNumber, column.trim(), CLOSEST_FOR_COLUMN))));
        }
        return targets;
    }

    private record Row(long low, long high, Map<TargetFormat, String> byTarget, Set<TargetFormat> closestFor) {
    }
}
