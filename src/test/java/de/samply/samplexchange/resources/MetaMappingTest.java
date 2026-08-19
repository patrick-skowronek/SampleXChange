package de.samply.samplexchange.resources;

import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ported from TransFAIR (resources/MetaMappingTest), tag prefix adapted to this project.
 */
class MetaMappingTest {

    @Test
    void tagResourceAddsVersionedTag() {
        MetaMapping metaMapping = new MetaMapping("0.0.1", "test");
        Patient patient = new Patient();

        metaMapping.tagResource(patient);

        assertEquals("SampleXChange 0.0.1 - " + LocalDate.now() + " - test",
                patient.getMeta().getTag().get(0).getCode());
    }
}
