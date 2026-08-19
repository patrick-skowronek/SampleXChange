package de.samply.samplexchange.resources;

import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.samply.samplexchange.utils.JsonUtils.compareFhirObjects;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Ported from TransFAIR (resources/CauseOfDeathTest).
 *
 * <p>Only the MII to bbmri.de direction survives. In MII a cause of death is a Condition, in
 * bbmri.de it is an Observation.
 */
class CauseOfDeathMappingTest {

    private Observation expectedBbmri;
    private Condition sourceMii;

    @BeforeEach
    void setup() {
        expectedBbmri = new Observation();
        expectedBbmri.setId("death");
        expectedBbmri.getMeta().setProfile(
                List.of(new CanonicalType(CauseOfDeathMapping.BBMRI_PROFILE_CAUSE_OF_DEATH)));
        expectedBbmri.getCode().getCodingFirstRep()
                .setSystem("http://loinc.org").setCode("68343-3");
        expectedBbmri.setSubject(new Reference().setReference("causeOfDeath"));
        CodeableConcept value = new CodeableConcept();
        value.getCodingFirstRep().setSystem(CauseOfDeathMapping.ICD_SYSTEM).setCode("C25.0");
        expectedBbmri.setValue(value);

        sourceMii = new Condition();
        sourceMii.setId("death");
        sourceMii.getMeta().setProfile(
                List.of(new CanonicalType(CauseOfDeathMapping.MII_PROFILE_CAUSE_OF_DEATH)));
        CodeableConcept loinc = new CodeableConcept();
        loinc.getCodingFirstRep().setSystem("http://loinc.org").setCode("79378-6");
        CodeableConcept snomed = new CodeableConcept();
        snomed.getCodingFirstRep().setSystem("http://snomed.info/sct").setCode("16100001");
        sourceMii.setCategory(List.of(loinc, snomed));
        sourceMii.setSubject(new Reference().setReference("causeOfDeath"));
        sourceMii.getCode().getCodingFirstRep()
                .setSystem(CauseOfDeathMapping.ICD_SYSTEM).setCode("C25.0");
    }

    @Test
    void convertFromMiiToBbmri() {
        CauseOfDeathMapping mapping = new CauseOfDeathMapping();

        mapping.fromMii(sourceMii);

        compareFhirObjects(mapping.toBbmri(), expectedBbmri);
    }

    @Test
    void emptyConditionYieldsNull() {
        // DIVERGENCE from TransFAIR, which returned an empty Observation here. This project
        // returns null when no id could be derived, and Mii2Bbmri null-checks the result.
        CauseOfDeathMapping mapping = new CauseOfDeathMapping();

        mapping.fromMii(new Condition());

        assertNull(mapping.toBbmri());
    }

    @Test
    void conditionWithWrongProfileIsIgnored() {
        // fromMii only reads resources carrying the MII Todesursache profile.
        Condition other = new Condition();
        other.setId("death");
        other.getMeta().setProfile(List.of(new CanonicalType("http://example.com/SomethingElse")));
        other.getCode().getCodingFirstRep()
                .setSystem(CauseOfDeathMapping.ICD_SYSTEM).setCode("C25.0");

        CauseOfDeathMapping mapping = new CauseOfDeathMapping();
        mapping.fromMii(other);

        assertNull(mapping.toBbmri());
    }
}
