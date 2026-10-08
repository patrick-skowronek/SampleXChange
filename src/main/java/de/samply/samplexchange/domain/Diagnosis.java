package de.samply.samplexchange.domain;

import org.hl7.fhir.r4.model.DateTimeType;

public record Diagnosis(String id, String donorId, CodedValue code, DateTimeType onset) {
}
