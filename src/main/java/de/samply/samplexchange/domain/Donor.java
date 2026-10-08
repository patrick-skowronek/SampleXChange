package de.samply.samplexchange.domain;

import org.hl7.fhir.r4.model.DateTimeType;

import java.util.Date;

public record Donor(
        String id,
        Date birthDate,
        String gender,
        boolean deceased,
        DateTimeType deceasedDateTime) {
}
