package de.samply.samplexchange.source;

import de.samply.samplexchange.configuration.SourceFormat;
import de.samply.samplexchange.domain.BiobankDirectory;
import de.samply.samplexchange.domain.DonorRecord;

import java.util.Set;

public interface SourceReader {
    SourceFormat format();

    /** Every biobank and collection in the source. Read once per run, before any donor. */
    BiobankDirectory readDirectory(SourceSession session);

    Set<String> donorIds(SourceSession session);

    DonorRecord read(SourceSession session, String donorId);
}
