package de.samply.samplexchange.target;

import de.samply.samplexchange.configuration.TargetFormat;
import de.samply.samplexchange.domain.BiobankDirectory;
import de.samply.samplexchange.domain.DonorRecord;
import org.hl7.fhir.r4.model.Resource;

import java.util.List;

public interface TargetWriter {
    TargetFormat format();

    /** The biobanks and collections, written before any donor so samples can reference them. */
    List<Resource> write(BiobankDirectory directory);

    List<Resource> write(DonorRecord record);
}
