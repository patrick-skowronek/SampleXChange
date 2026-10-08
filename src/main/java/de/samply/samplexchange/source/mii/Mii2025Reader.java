package de.samply.samplexchange.source.mii;

import de.samply.samplexchange.configuration.SourceFormat;
import org.springframework.stereotype.Component;

@Component
public class Mii2025Reader extends MiiSourceReader {
    public Mii2025Reader(SpecimenHierarchyResolver resolver, SpecimenToSampleMapper specimenToSampleMapper) {
        super(resolver, specimenToSampleMapper);
    }

    @Override
    public SourceFormat format() {
        return SourceFormat.MII_2025;
    }
}
