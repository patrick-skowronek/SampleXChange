package de.samply.samplexchange.domain;

import org.hl7.fhir.r4.model.DateTimeType;

import java.util.List;

/**
 * One exported sample. {@code collectionId} is the collection that manages it, or null when the
 * source names none or names one that was not read.
 */
public record Sample(
        String id,
        String donorId,
        List<CodedValue> types,
        DateTimeType collectedAt,
        CodedValue bodySite,
        CodedValue fastingStatus,
        TemperatureRange storageTemperature,
        boolean cellLineOrOrganoid,
        String collectionId) {
    public Sample {
        types = List.copyOf(types);
    }

    /** A sample with no known collection. */
    public Sample(String id, String donorId, List<CodedValue> types, DateTimeType collectedAt,
                  CodedValue bodySite, CodedValue fastingStatus, TemperatureRange storageTemperature,
                  boolean cellLineOrOrganoid) {
        this(id, donorId, types, collectedAt, bodySite, fastingStatus, storageTemperature,
                cellLineOrOrganoid, null);
    }

    public CodedValue type(String system) {
        return CodedValue.inSystem(types, system);
    }

    public Sample withoutCollection() {
        return new Sample(id, donorId, types, collectedAt, bodySite, fastingStatus, storageTemperature,
                cellLineOrOrganoid, null);
    }
}
