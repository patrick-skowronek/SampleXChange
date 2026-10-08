package de.samply.samplexchange.resources;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.BaseResource;

import java.time.LocalDate;

public class MetaMapping {
    private final String version;

    private final String mapping;

    public MetaMapping(String version, String mapping) {
        this.version = version;
        this.mapping = mapping;
    }

    public IBaseResource tagResource(BaseResource base) {
        base.getMeta().addTag()
                .setCode("SampleXChange " + version + " - " + LocalDate.now() + " - " + mapping);
        return base;
    }

}
