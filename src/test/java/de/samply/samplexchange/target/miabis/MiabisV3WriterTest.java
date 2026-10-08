package de.samply.samplexchange.target.miabis;

import de.samply.samplexchange.domain.CauseOfDeath;
import de.samply.samplexchange.domain.CodedValue;
import de.samply.samplexchange.domain.Diagnosis;
import de.samply.samplexchange.domain.Donor;
import de.samply.samplexchange.domain.DonorRecord;
import de.samply.samplexchange.domain.Sample;
import de.samply.samplexchange.domain.TemperatureRange;
import de.samply.samplexchange.terminology.Terminology;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Specimen;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One test per field mapping, checked against package eu.miabis.r4 1.3.0.
 */
class MiabisV3WriterTest {

    private static final String SNOMED = "http://snomed.info/sct";
    private static final String ICD_10_GM = "http://fhir.de/CodeSystem/bfarm/icd-10-gm";

    private final MiabisV3Writer writer = new MiabisV3Writer(new Terminology());

    @Test
    void sampleTypeUsesTheMiabisDetailedList() {
        // Specimen.type is bound to miabis-detailed-sample-type-vs, not the coarse material type.
        CodeableConcept type = writer.toSampleType(List.of(new CodedValue(SNOMED, "119361006")));

        assertEquals(MiabisV3Writer.DETAILED_SAMPLE_TYPE_SYSTEM,
                type.getCodingFirstRep().getSystem());
        assertEquals("Plasma", type.getCodingFirstRep().getCode());
    }

    @Test
    void tissueUsesTheDetailedNamesNotTheCoarseOnes() {
        // The coarse list says TissueFFPE and TissueFrozen; the detailed list says TissueFixed and
        // TissueFreshFrozen. Specimen.type binds to the detailed one.
        assertEquals("TissueFixed",
                writer.toSampleType(List.of(new CodedValue(SNOMED, "441652008"))).getCodingFirstRep().getCode());
        assertEquals("TissueFreshFrozen",
                writer.toSampleType(List.of(new CodedValue(SNOMED, "16214131000119104"))).getCodingFirstRep().getCode());
    }

    @Test
    void anUnmappedSampleTypeFallsBackToOther() {
        assertEquals("Other",
                writer.toSampleType(List.of(new CodedValue(SNOMED, "73211009"))).getCodingFirstRep().getCode());
    }

    @Test
    void storageTemperatureSitsOnProcessingNotOnTheSpecimenRoot() {
        Sample sample = new Sample("s1", "d1", List.of(), null, null, null,
                new TemperatureRange(-209L, -196L), false);

        Specimen specimen = writer.toSample(sample);

        assertTrue(specimen.getExtension().isEmpty(), "the root carries no temperature extension");
        assertEquals(1, specimen.getProcessing().size());
        Extension extension = specimen.getProcessingFirstRep().getExtensionFirstRep();
        assertEquals(MiabisV3Writer.STORAGE_TEMPERATURE_EXTENSION, extension.getUrl());
        CodeableConcept value = (CodeableConcept) extension.getValue();
        assertEquals(MiabisV3Writer.STORAGE_TEMPERATURE_SYSTEM, value.getCodingFirstRep().getSystem());
        assertEquals("LN", value.getCodingFirstRep().getCode());
    }

    @Test
    void gaseousNitrogenBecomesOtherBecauseMiabisHasNoBucketForIt() {
        Extension extension = writer.toStorageTemperatureExtension(new TemperatureRange(-195L, -160L));

        CodeableConcept value = (CodeableConcept) extension.getValue();
        assertEquals("Other", value.getCodingFirstRep().getCode());
    }

    @Test
    void noTemperatureMeansNoProcessingStep() {
        Sample sample = new Sample("s1", "d1", List.of(), null, null, null, null, false);

        assertTrue(writer.toSample(sample).getProcessing().isEmpty());
        assertNull(writer.toStorageTemperatureExtension(null));
    }

    @Test
    void samplesAndDonorsCarryTheRequiredIdentifier() {
        // miabis-sample and miabis-sample-donor both require identifier 1..1.
        Specimen specimen = writer.toSample(new Sample("s1", "d1", List.of(), null, null, null, null, false));
        Patient patient = writer.toDonor(new Donor("d1", null, "female", false, null));

        assertEquals("s1", specimen.getIdentifierFirstRep().getValue());
        assertEquals("d1", patient.getIdentifierFirstRep().getValue());
    }

    @Test
    void aDonorWithoutGenderBecomesUnknownBecauseMiabisRequiresOne() {
        Patient patient = writer.toDonor(new Donor("d1", null, null, false, null));

        assertEquals(Enumerations.AdministrativeGender.UNKNOWN, patient.getGender());
    }

    @Test
    void aKnownGenderIsPassedThrough() {
        assertEquals(Enumerations.AdministrativeGender.MALE,
                writer.toDonor(new Donor("d1", null, "male", false, null)).getGender());
    }

    @Test
    void bodySiteKeepsItsSourceSystemBecauseMiabisFixesNone() {
        CodeableConcept site = MiabisV3Writer.toBodySite(
                new CodedValue("http://terminology.hl7.org/CodeSystem/icd-o-3", "8148/2"));

        assertEquals("http://terminology.hl7.org/CodeSystem/icd-o-3",
                site.getCodingFirstRep().getSystem());
        assertEquals("8148/2", site.getCodingFirstRep().getCode());
    }

    @Test
    void causeOfDeathIsWrittenAsAPatientCondition() {
        // MIABIS has no cause of death profile, and its Observation profile requires a specimen
        // reference, so the patient Condition is the only fit. Flagged for the profiling team.
        Condition condition = writer.toCauseOfDeathCondition(
                new CauseOfDeath("death-1", "d1", new CodedValue(ICD_10_GM, "R96.1", "2021")));

        assertTrue(condition.getMeta().getProfile().stream()
                .anyMatch(p -> p.asStringValue().equals(MiabisV3Writer.CONDITION_PROFILE)));
        assertEquals("Patient/d1", condition.getSubject().getReference());
        assertEquals("R96.1", condition.getCode().getCodingFirstRep().getCode());
    }

    @Test
    void sampleCarriesSubjectAndCollectionDate() {
        Sample sample = new Sample("s1", "d1",
                List.of(new CodedValue(SNOMED, "119297000")),
                new DateTimeType("2018-06-07T15:54:00+01:00"),
                null, null, null, false);

        Specimen specimen = writer.toSample(sample);

        assertEquals("Patient/d1", specimen.getSubject().getReference());
        assertEquals("2018-06-07T15:54:00+01:00",
                specimen.getCollection().getCollectedDateTimeType().getValueAsString());
        assertEquals("WholeBlood", specimen.getType().getCodingFirstRep().getCode());
    }

    @Test
    void writeProducesDonorSamplesAndConditions() {
        DonorRecord record = new DonorRecord(
                new Donor("d1", null, "male", false, null),
                List.of(new Sample("s1", "d1", List.of(), null, null, null, null, false)),
                List.of(new Diagnosis("c1", "d1", new CodedValue(ICD_10_GM, "C61"), null)),
                List.of(new CauseOfDeath("death-1", "d1", new CodedValue(ICD_10_GM, "R96.1"))));

        List<Resource> resources = writer.write(record);

        assertEquals(4, resources.size());
        assertEquals(1, resources.stream().filter(Patient.class::isInstance).count());
        assertEquals(1, resources.stream().filter(Specimen.class::isInstance).count());
        // Both the diagnosis and the cause of death are Conditions in MIABIS.
        assertEquals(2, resources.stream().filter(Condition.class::isInstance).count());
    }

    @Test
    void aNativeMiabisCodingIsUsedAsItStands() {
        // MII 2026 labels an organoid with a generic SNOMED code plus the MIABIS one. Only the
        // MIABIS code says which it is, so translating from SNOMED would lose the distinction.
        CodeableConcept type = writer.toSampleType(List.of(
                new CodedValue(SNOMED, "123038009"),
                new CodedValue(MiabisV3Writer.DETAILED_SAMPLE_TYPE_SYSTEM_AS_MII_SPELLS_IT, "Organoid")));

        assertEquals("Organoid", type.getCodingFirstRep().getCode());
        assertEquals(MiabisV3Writer.DETAILED_SAMPLE_TYPE_SYSTEM, type.getCodingFirstRep().getSystem());
    }

    @Test
    void theCanonicalMiabisSystemIsAcceptedToo() {
        CodeableConcept type = writer.toSampleType(List.of(
                new CodedValue(MiabisV3Writer.DETAILED_SAMPLE_TYPE_SYSTEM, "ImmortalizedCellLine")));

        assertEquals("ImmortalizedCellLine", type.getCodingFirstRep().getCode());
    }

    @Test
    void cellLinesAndOrganoidsAreExportedBecauseMiabisHasCodesForThem() {
        Sample organoid = new Sample("OrganoidLunge", "d1",
                List.of(new CodedValue(MiabisV3Writer.DETAILED_SAMPLE_TYPE_SYSTEM_AS_MII_SPELLS_IT, "Organoid")),
                null, null, null, null, true);
        DonorRecord record = new DonorRecord(
                new Donor("d1", null, "male", false, null), List.of(organoid), List.of(), List.of());

        List<Resource> resources = writer.write(record);

        assertEquals(1, resources.stream().filter(Specimen.class::isInstance).count());
        Specimen specimen = (Specimen) resources.stream()
                .filter(Specimen.class::isInstance).findFirst().orElseThrow();
        assertEquals("Organoid", specimen.getType().getCodingFirstRep().getCode());
    }
}
