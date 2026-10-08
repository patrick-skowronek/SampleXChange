package de.samply.samplexchange.target.bbmri;

import de.samply.samplexchange.domain.Biobank;
import de.samply.samplexchange.domain.CauseOfDeath;
import de.samply.samplexchange.domain.CodedValue;
import de.samply.samplexchange.domain.Contact;
import de.samply.samplexchange.domain.Diagnosis;
import de.samply.samplexchange.domain.Donor;
import de.samply.samplexchange.domain.DonorRecord;
import de.samply.samplexchange.domain.Sample;
import de.samply.samplexchange.domain.SampleCollection;
import de.samply.samplexchange.domain.TemperatureRange;
import de.samply.samplexchange.terminology.Terminology;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Specimen;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One test per field mapping, so a single transformation can be checked without building a whole
 * resource or reading a JSON diff.
 */
class BbmriDeWriterTest {

    private static final String SNOMED = "http://snomed.info/sct";
    private static final String ICD_10_GM = "http://fhir.de/CodeSystem/bfarm/icd-10-gm";

    private final BbmriDeWriter writer = new BbmriDeWriter(new Terminology());

    // --- field mappings -----------------------------------------------------------------------

    @Test
    void sampleTypeUsesTheBbmriMaterialTypeSystem() {
        CodeableConcept type = writer.toSampleType(List.of(new CodedValue(SNOMED, "119361006")));

        assertEquals(BbmriDeWriter.SAMPLE_MATERIAL_TYPE_SYSTEM, type.getCodingFirstRep().getSystem());
        assertEquals("blood-plasma", type.getCodingFirstRep().getCode());
    }

    @Test
    void anUnmappedSampleTypeFallsBackToDerivativeOther() {
        CodeableConcept type = writer.toSampleType(List.of(new CodedValue(SNOMED, "73211009")));

        assertEquals("derivative-other", type.getCodingFirstRep().getCode());
    }

    @Test
    void aMissingSampleTypeStillProducesTheFallback() {
        assertEquals("derivative-other", writer.toSampleType(List.of()).getCodingFirstRep().getCode());
    }

    @Test
    void bodySiteIsReSystemedToTheIcdO3Oid() {
        CodeableConcept site = BbmriDeWriter.toBodySite("sample",
                new CodedValue("http://terminology.hl7.org/CodeSystem/icd-o-3", "8148/2"));

        assertEquals(BbmriDeWriter.BODY_SITE_SYSTEM, site.getCodingFirstRep().getSystem());
        assertEquals("8148/2", site.getCodingFirstRep().getCode());
    }

    @Test
    void aSnomedBodySiteIsLeftOutRatherThanRelabelled() {
        // MII 2026 body sites are SNOMED CT. Writing 14559000 under the ICD-O-3 OID would claim a
        // topography code that does not exist.
        assertNull(BbmriDeWriter.toBodySite("sample", new CodedValue(SNOMED, "14559000")));
    }

    @Test
    void aMissingBodySiteProducesNothing() {
        assertNull(BbmriDeWriter.toBodySite("sample", null));
    }

    @Test
    void fastingStatusKeepsItsSourceSystem() {
        // Previously read from MII and then never written at all.
        CodeableConcept fasting = BbmriDeWriter.toFastingStatus(
                new CodedValue("http://terminology.hl7.org/CodeSystem/v2-0916", "F"));

        assertEquals("http://terminology.hl7.org/CodeSystem/v2-0916",
                fasting.getCodingFirstRep().getSystem());
        assertEquals("F", fasting.getCodingFirstRep().getCode());
    }

    @Test
    void storageTemperatureBecomesABucketExtension() {
        Extension extension = writer.toStorageTemperatureExtension(new TemperatureRange(-195L, -160L));

        assertEquals(BbmriDeWriter.STORAGE_TEMPERATURE_EXTENSION, extension.getUrl());
        CodeableConcept value = (CodeableConcept) extension.getValue();
        assertEquals(BbmriDeWriter.STORAGE_TEMPERATURE_SYSTEM, value.getCodingFirstRep().getSystem());
        assertEquals("temperatureGN", value.getCodingFirstRep().getCode());
    }

    @Test
    void anUncoveredTemperatureProducesNoExtension() {
        assertNull(writer.toStorageTemperatureExtension(new TemperatureRange(50L, 100L)));
        assertNull(writer.toStorageTemperatureExtension(null));
    }

    @Test
    void aDiagnosisKeepsItsSourceCodingSystemAndVersion() {
        CodeableConcept code = BbmriDeWriter.toDiagnosisCode(
                new CodedValue(ICD_10_GM, "C61", "2022"));

        assertEquals(ICD_10_GM, code.getCodingFirstRep().getSystem());
        assertEquals("C61", code.getCodingFirstRep().getCode());
        assertEquals("2022", code.getCodingFirstRep().getVersion());
    }

    @Test
    void aSnomedDiagnosisIsExportedRatherThanDropped() {
        // The old ConditionMapping logged "not supported" and exported a Condition with no code.
        CodeableConcept code = BbmriDeWriter.toDiagnosisCode(new CodedValue(SNOMED, "399068003"));

        assertEquals(SNOMED, code.getCodingFirstRep().getSystem());
        assertEquals("399068003", code.getCodingFirstRep().getCode());
    }

    // --- resources ----------------------------------------------------------------------------

    @Test
    void specimenCarriesEveryMappedField() {
        Sample sample = new Sample(
                "group-1", "donor-7",
                List.of(new CodedValue(SNOMED, "119361006")),
                new DateTimeType("2018-06-07T15:54:00+01:00"),
                new CodedValue("http://terminology.hl7.org/CodeSystem/icd-o-3", "8148/2"),
                new CodedValue("http://terminology.hl7.org/CodeSystem/v2-0916", "F"),
                new TemperatureRange(-195L, -160L), false);

        Specimen specimen = writer.toSpecimen(sample);

        assertEquals("group-1", specimen.getIdElement().getIdPart());
        assertEquals("Patient/donor-7", specimen.getSubject().getReference());
        assertTrue(specimen.getMeta().getProfile().stream()
                .anyMatch(p -> p.asStringValue().equals(BbmriDeWriter.SPECIMEN_PROFILE)));
        assertEquals("blood-plasma", specimen.getType().getCodingFirstRep().getCode());
        assertEquals("2018-06-07T15:54:00+01:00",
                specimen.getCollection().getCollectedDateTimeType().getValueAsString());
        assertEquals("8148/2", specimen.getCollection().getBodySite().getCodingFirstRep().getCode());
        assertEquals("F", specimen.getCollection().getFastingStatusCodeableConcept()
                .getCodingFirstRep().getCode());
        assertEquals(1, specimen.getExtension().size());
    }

    @Test
    void theManagingCollectionBecomesTheCustodian() {
        Sample sample = new Sample("group-1", "donor-7", List.of(), null, null, null, null, false,
                "Mustersammlung");

        Extension custodian = writer.toSpecimen(sample).getExtensionByUrl(BbmriDeWriter.CUSTODIAN_EXTENSION);

        assertEquals("Organization/Mustersammlung", ((Reference) custodian.getValue()).getReference());
    }

    @Test
    void aCollectionIsPartOfItsBiobankAndNamesItsParentCollection() {
        Organization collection = writer.toCollection(new SampleCollection("sub", "biobank", "parent",
                null, "Sub", null, null, List.of(), List.of()));

        assertEquals(BbmriDeWriter.COLLECTION_PROFILE, collection.getMeta().getProfile().get(0).getValue());
        assertEquals("Organization/biobank", collection.getPartOf().getReference());
        assertEquals("Organization/parent", ((Reference) collection
                .getExtensionByUrl(BbmriDeWriter.PARENT_COLLECTION_EXTENSION).getValue()).getReference());
    }

    @Test
    void aCollectionWithNoBiobankOrTypeGetsNeitherInvented() {
        // The writer warns about the gaps instead of filling them. Only the data category is
        // always set, because an MII collection is a collection of samples by definition.
        Organization collection = writer.toCollection(new SampleCollection("orphan", null, null,
                null, "Orphan", null, null,
                List.of(new CodedValue("https://fhir.bbmri-eric.eu/CodeSystem/miabis-sample-collection-setting-cs",
                        "Museum")),
                List.of()));

        assertFalse(collection.hasPartOf());
        assertFalse(collection.hasIdentifier());
        assertNull(collection.getExtensionByUrl(BbmriDeWriter.COLLECTION_TYPE_EXTENSION));
        assertEquals("BIOLOGICAL_SAMPLES", ((CodeableConcept) collection
                .getExtensionByUrl(BbmriDeWriter.DATA_CATEGORY_EXTENSION).getValue()).getCodingFirstRep().getCode());
    }

    @Test
    void aBiobankKeepsItsBbmriEricIdAndResearchContact() {
        Organization biobank = writer.toBiobank(new Biobank("biobank", "de-12345", "Biobank", "BB",
                "Beschreibung", List.of(new Contact("Direktor", "Mustermann", List.of("Max"), List.of("Prof."),
                        "max@example.org", null, List.of("Musterstrasse 3"), "Musterstadt", "00000", null))));

        assertEquals(BbmriDeWriter.BIOBANK_PROFILE, biobank.getMeta().getProfile().get(0).getValue());
        assertEquals(BbmriDeWriter.BBMRI_ERIC_ID_SYSTEM, biobank.getIdentifierFirstRep().getSystem());
        assertEquals("de-12345", biobank.getIdentifierFirstRep().getValue());
        assertEquals("BB", biobank.getAlias().get(0).getValue());
        assertEquals("RESEARCH", biobank.getContactFirstRep().getPurpose().getCodingFirstRep().getCode());
        assertEquals("Mustermann", biobank.getContactFirstRep().getName().getFamily());
        // MII has no head contact, juridical person or country; none is made up.
        assertEquals(1, biobank.getContact().size());
        assertFalse(biobank.hasAddress());
    }

    @Test
    void specimenIdIsAPlainIdNotAServerUrl() {
        Sample sample = new Sample("group-1", "donor-7", List.of(), null, null, null, null, false);

        assertEquals("group-1", writer.toSpecimen(sample).getIdElement().getIdPart());
        assertFalse(writer.toSpecimen(sample).getId().contains("_history"));
    }

    @Test
    void patientCarriesGenderBirthDateAndDeath() {
        DateTimeType died = new DateTimeType("2020-01-02");
        Donor donor = new Donor("donor-7", new DateTimeType("1970-03-04").getValue(),
                "female", true, died);

        Patient patient = writer.toPatient(donor);

        assertEquals("donor-7", patient.getIdElement().getIdPart());
        assertEquals(Enumerations.AdministrativeGender.FEMALE, patient.getGender());
        assertTrue(patient.hasBirthDate());
        assertEquals("2020-01-02", patient.getDeceasedDateTimeType().getValueAsString());
    }

    @Test
    void aLivingDonorHasNoDeceasedValue() {
        Donor donor = new Donor("donor-7", null, "male", false, null);

        assertFalse(writer.toPatient(donor).hasDeceased());
    }

    @Test
    void causeOfDeathIsAnObservationCarryingTheIcd10GmCode() {
        // MII codes cause of death in ICD-10-GM. The old mapping only read plain ICD-10 and
        // exported the Observation with no value at all.
        CauseOfDeath cause = new CauseOfDeath("death-1", "donor-7",
                new CodedValue(ICD_10_GM, "R96.1", "2021"));

        Observation observation = writer.toCauseOfDeathObservation(cause);

        assertEquals("68343-3", observation.getCode().getCodingFirstRep().getCode());
        assertEquals("Patient/donor-7", observation.getSubject().getReference());
        CodeableConcept value = observation.getValueCodeableConcept();
        assertEquals(ICD_10_GM, value.getCodingFirstRep().getSystem());
        assertEquals("R96.1", value.getCodingFirstRep().getCode());
    }

    @Test
    void conditionCarriesOnsetAndCode() {
        Condition condition = writer.toCondition(new Diagnosis("cond-1", "donor-7",
                new CodedValue(ICD_10_GM, "C61", "2022"), new DateTimeType("2018-06-07")));

        assertEquals("cond-1", condition.getIdElement().getIdPart());
        assertEquals("Patient/donor-7", condition.getSubject().getReference());
        assertEquals("C61", condition.getCode().getCodingFirstRep().getCode());
        assertEquals("2018-06-07", condition.getOnsetDateTimeType().getValueAsString());
    }

    @Test
    void writeProducesOneResourcePerDomainObject() {
        DonorRecord record = new DonorRecord(
                new Donor("donor-7", null, "male", false, null),
                List.of(new Sample("group-1", "donor-7", List.of(), null, null, null, null, false),
                        new Sample("group-2", "donor-7", List.of(), null, null, null, null, false)),
                List.of(new Diagnosis("cond-1", "donor-7", new CodedValue(ICD_10_GM, "C61"), null)),
                List.of(new CauseOfDeath("death-1", "donor-7", new CodedValue(ICD_10_GM, "R96.1"))));

        List<Resource> resources = writer.write(record);

        assertEquals(5, resources.size());
        assertEquals(1, resources.stream().filter(Patient.class::isInstance).count());
        assertEquals(2, resources.stream().filter(Specimen.class::isInstance).count());
        assertEquals(1, resources.stream().filter(Condition.class::isInstance).count());
        assertEquals(1, resources.stream().filter(Observation.class::isInstance).count());
    }

    @Test
    void cellLinesAndOrganoidsAreSkippedBecauseBbmriHasNoCodeForThem() {
        // bbmri.de SampleMaterialType has nothing for a cell culture, so exporting one could only
        // label it derivative-other. MIABIS does have codes and keeps them.
        Sample organoid = new Sample("OrganoidLunge", "donor-7", List.of(), null, null, null, null, true);
        Sample plasma = new Sample("group-1", "donor-7",
                List.of(new CodedValue(SNOMED, "119361006")), null, null, null, null, false);
        DonorRecord record = new DonorRecord(
                new Donor("donor-7", null, "male", false, null),
                List.of(organoid, plasma), List.of(), List.of());

        List<Resource> resources = writer.write(record);

        List<Specimen> specimens = resources.stream()
                .filter(Specimen.class::isInstance).map(Specimen.class::cast).toList();
        assertEquals(1, specimens.size());
        assertEquals("group-1", specimens.get(0).getIdElement().getIdPart());
    }
}
