package de.samply.samplexchange.transform;

import de.samply.samplexchange.source.SourceReader;
import de.samply.samplexchange.target.TargetWriter;

public record Transformation(SourceReader reader, TargetWriter writer) {
    public String describe() {
        return reader.format() + " to " + writer.format();
    }
}
