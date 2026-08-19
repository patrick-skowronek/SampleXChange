package de.samply.samplexchange.resources;

import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Range;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Specimen;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.samply.samplexchange.utils.JsonUtils.compareFhirObjects;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Ported from TransFAIR (resources/SpecimenMappingTest), MII to bbmri.de direction only.
 *
 * <p>The MII fixture had to be reworked versus TransFAIR: this project only reads a storage
 * temperature from a processing step that carries SNOMED procedure code 1186936003 AND a
 * timePeriod start, whereas TransFAIR read the first processing extension unconditionally.
 * TransFAIR's fixture satisfies neither condition, so ported verbatim the temperature silently
 * would not map.
 */
class SpecimenMappingTest {

    private static final String MII_TEMPERATURE_EXTENSION_URL =
            "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/Temperaturbedingungen";
    private static final String STORAGE_PROCEDURE_CODE = "1186936003";
    private static final String ICD_O_3 = "http://terminology.hl7.org/CodeSystem/icd-o-3";

    private static Specimen.SpecimenProcessingComponent storageStep(String start, int high, int low) {
        Specimen.SpecimenProcessingComponent step = new Specimen.SpecimenProcessingComponent();
        step.getProcedure().getCodingFirstRep()
                .setSystem("http://snomed.info/sct").setCode(STORAGE_PROCEDURE_CODE);
        step.setTime(new Period().setStartElement(new DateTimeType(start)));
        Extension temperature = new Extension();
        temperature.setUrl(MII_TEMPERATURE_EXTENSION_URL);
        temperature.setValue(new Range().setHigh(new Quantity(high)).setLow(new Quantity(low)));
        step.addExtension(temperature);
        return step;
    }

    private static Specimen miiSpecimen() {
        Specimen specimen = new Specimen();
        specimen.setId("specimenId");
        specimen.getMeta().setProfile(List.of(new CanonicalType(
                "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/Specimen")));
        specimen.setSubject(new Reference().setReference("Patient/patientId"));
        specimen.getType().getCodingFirstRep()
                .setSystem("http://snomed.info/sct").setCode("119361006");
        specimen.getCollection().setCollected(new DateTimeType("2018-06-07T15:54:00+01:00"));
        specimen.getCollection().getBodySite().getCodingFirstRep()
                .setSystem(ICD_O_3).setCode("8148/2");
        specimen.getCollection().getFastingStatusCodeableConcept().getCodingFirstRep()
                .setSystem("http://terminology.hl7.org/CodeSystem/v2-0916").setCode("F");
        return specimen;
    }

    @Test
    void convertFromMiiToBbmri() {
        Specimen source = miiSpecimen();
        source.addProcessing(storageStep("2018-06-07T16:51:00+01:00", -160, -195));

        Specimen expected = new Specimen();
        expected.setId("specimenId");
        expected.getMeta().setProfile(
                List.of(new CanonicalType("https://fhir.bbmri.de/StructureDefinition/Specimen")));
        expected.setSubject(new Reference().setReference("Patient/patientId"));
        expected.getType().getCodingFirstRep()
                .setSystem("https://fhir.bbmri.de/CodeSystem/SampleMaterialType")
                .setCode("blood-plasma");
        expected.getCollection().setCollected(new DateTimeType("2018-06-07T15:54:00+01:00"));
        expected.getCollection().getBodySite().getCodingFirstRep()
                .setSystem("urn:oid:1.3.6.1.4.1.19376.1.3.11.36").setCode("8148/2");
        Extension storageTemperature = new Extension();
        storageTemperature.setUrl("https://fhir.bbmri.de/StructureDefinition/StorageTemperature");
        CodeableConcept temperatureCode = new CodeableConcept();
        temperatureCode.getCodingFirstRep()
                .setSystem("https://fhir.bbmri.de/CodeSystem/StorageTemperature")
                .setCode("temperatureGN");
        storageTemperature.setValue(temperatureCode);
        expected.addExtension(storageTemperature);
        // NOTE: no fastingStatus on the expected side. toBbmri never maps it even though the
        // source carries it and the deleted toMii did. Known gap, see the ADR appendix.

        SpecimenMapping mapping = new SpecimenMapping();
        mapping.fromMii(source);

        compareFhirObjects(mapping.toBbmri(), expected);
    }

    @Test
    void fastingStatusIsNotMapped() {
        // Pins the known gap explicitly so Phase 2 has to make a deliberate decision.
        Specimen source = miiSpecimen();

        SpecimenMapping mapping = new SpecimenMapping();
        mapping.fromMii(source);

        assertFalse(mapping.toBbmri().getCollection().hasFastingStatus());
    }

    @Test
    void storageTemperatureNeedsProcedureCode() {
        Specimen source = miiSpecimen();
        Specimen.SpecimenProcessingComponent step = storageStep("2018-06-07T16:51:00+01:00", -160, -195);
        // same step, but a non-storage procedure code
        step.getProcedure().getCodingFirstRep().setCode("73373003");
        source.addProcessing(step);

        SpecimenMapping mapping = new SpecimenMapping();
        mapping.fromMii(source);

        assertFalse(mapping.toBbmri().hasExtension(),
                "without the storage procedure code no temperature extension is produced");
    }

    @Test
    void storageTemperatureNeedsTimePeriod() {
        Specimen source = miiSpecimen();
        Specimen.SpecimenProcessingComponent step = storageStep("2018-06-07T16:51:00+01:00", -160, -195);
        step.setTime(null);
        source.addProcessing(step);

        SpecimenMapping mapping = new SpecimenMapping();
        mapping.fromMii(source);

        assertFalse(mapping.toBbmri().hasExtension(),
                "a storage step without timePeriod start is skipped, so temperature is dropped");
    }

    @Test
    void latestStorageStepIsChosenByDocumentOrderNotByTime() {
        // KNOWN BUG: fromMii compares every step against a fixed 1900 sentinel that it never
        // advances, so the LAST matching step in document order wins rather than the latest by
        // time. Here the earlier step comes second and therefore incorrectly wins.
        Specimen source = miiSpecimen();
        source.addProcessing(storageStep("2018-06-07T18:00:00+01:00", -160, -195)); // later  -> GN
        source.addProcessing(storageStep("2018-06-07T09:00:00+01:00", 10, 2));      // earlier -> 2to10

        SpecimenMapping mapping = new SpecimenMapping();
        mapping.fromMii(source);

        CodeableConcept value = (CodeableConcept)
                mapping.toBbmri().getExtension().get(0).getValue();
        assertEquals("temperature2to10", value.getCodingFirstRep().getCode(),
                "current behaviour picks the last step in document order, not the latest by time");
    }
}
