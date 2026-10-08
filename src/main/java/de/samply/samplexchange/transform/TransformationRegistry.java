package de.samply.samplexchange.transform;

import de.samply.samplexchange.configuration.SourceFormat;
import de.samply.samplexchange.configuration.TargetFormat;
import de.samply.samplexchange.source.SourceReader;
import de.samply.samplexchange.target.TargetWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TransformationRegistry {
    private static final Set<Pair> SUPPORTED = new LinkedHashSet<>(List.of(
            new Pair(SourceFormat.MII_2025, TargetFormat.BBMRI_DE),
            new Pair(SourceFormat.MII_2026, TargetFormat.BBMRI_DE),
            new Pair(SourceFormat.MII_2025, TargetFormat.MIABIS_V3),
            new Pair(SourceFormat.MII_2026, TargetFormat.MIABIS_V3)));

    private final Map<SourceFormat, SourceReader> readers = new EnumMap<>(SourceFormat.class);
    private final Map<TargetFormat, TargetWriter> writers = new EnumMap<>(TargetFormat.class);

    public TransformationRegistry(List<SourceReader> readers, List<TargetWriter> writers) {
        readers.forEach(reader -> {
            if (this.readers.putIfAbsent(reader.format(), reader) != null) {
                throw new IllegalStateException("Two readers claim " + reader.format());
            }
        });
        writers.forEach(writer -> {
            if (this.writers.putIfAbsent(writer.format(), writer) != null) {
                throw new IllegalStateException("Two writers claim " + writer.format());
            }
        });
        log.debug("Registered readers {} and writers {}", this.readers.keySet(), this.writers.keySet());
    }

    public Transformation findTransformationFor(SourceFormat source, TargetFormat target) {
        if (!SUPPORTED.contains(new Pair(source, target))) {
            throw new IllegalArgumentException(
                    "%s to %s is not supported. Supported: %s".formatted(source, target, listSupportedPairs()));
        }
        SourceReader reader = readers.get(source);
        TargetWriter writer = writers.get(target);
        if (reader == null || writer == null) {
            throw new IllegalArgumentException(
                    "%s to %s is declared supported but has no %s".formatted(
                            source, target, reader == null ? "reader" : "writer"));
        }
        return new Transformation(reader, writer);
    }

    public String listSupportedPairs() {
        return SUPPORTED.stream()
                .map(pair -> pair.source() + " to " + pair.target())
                .collect(Collectors.joining(", "));
    }

    private record Pair(SourceFormat source, TargetFormat target) {
    }
}
