package de.samply.samplexchange.transform;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import de.samply.samplexchange.FileUtils;
import de.samply.samplexchange.configuration.SourceFormat;
import de.samply.samplexchange.configuration.TargetFormat;
import de.samply.samplexchange.domain.BiobankDirectory;
import de.samply.samplexchange.source.SourceSession;
import de.samply.samplexchange.source.mii.Mii2025Reader;
import de.samply.samplexchange.source.mii.Mii2026Reader;
import de.samply.samplexchange.source.mii.MiiSpecimenHierarchyResolver;
import de.samply.samplexchange.source.mii.SpecimenToSampleMapper;
import de.samply.samplexchange.target.bbmri.BbmriDeWriter;
import de.samply.samplexchange.target.miabis.MiabisV3Writer;
import de.samply.samplexchange.terminology.Terminology;
import de.samply.samplexchange.utils.fhir.FhirTransfer;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Specimen;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the official MII KDS Biobank 2026 examples through MII 2026 to bbmri.de and checks every
 * attribute bbmri.de marks Must Support on its Specimen, Patient, Biobank and Collection profiles.
 *
 * <p>The examples are the Simplifier project MedizininformatikInitiative-ModulBiobank: eight
 * Specimens, the collection {@code Mustersammlung} and the biobank {@code BiobankMusterstadt},
 * bundled with the example patient in {@code mii2026-bundle.json}. The three {@code input-*}
 * files in that project are IG build inputs (a terminology manifest and two navigation menus),
 * not data, so they are not part of the fixture.
 *
 * <p>The bundle copy of {@code MusterprobeGewebe} drops one extension: its {@code Diagnose}
 * reference points at {@code Diagnose/...}, which is not a FHIR resource type, and Blaze rejects
 * the whole transaction for it. The standalone copy under {@code mii2026/} is verbatim.
 *
 * <p>Runs without a FHIR server: the reader is given a {@link FhirTransfer} backed by the bundle.
 * {@link de.samply.samplexchange.systemtest.TransformationSystemTest} covers the same pair
 * against Blaze. Each test checks one attribute for every exported sample with {@code assertAll},
 * so a failure lists all affected samples at once.
 */
class Mii2026ToBbmriExamplesTest {

    private static final FhirContext CTX = FhirContext.forR4();

    private static final String DONOR_ID = "mii-exa-test-data-patient-1";
    private static final String V2_0916 = "http://terminology.hl7.org/CodeSystem/v2-0916";
    private static final String CUSTODIAN_EXTENSION = "https://fhir.bbmri.de/StructureDefinition/Custodian";

    private static final List<String> EXPORTED_SAMPLES = List.of(
            "AliquotgruppeBuffyCoat", "AliquotgruppeDNA", "AliquotgruppePlasma", "MusterprobeGewebe");

    private static List<Resource> written;

    @BeforeAll
    static void transferTheExamples() {
        Bundle source = (Bundle) CTX.newJsonParser()
                .parseResource(FileUtils.readResourceFile("mii2026-bundle.json"));
        SourceSession session = new SourceSession(null, new BundleBackedTransfer(source));

        Terminology terminology = new Terminology();
        MiiSpecimenHierarchyResolver resolver = new MiiSpecimenHierarchyResolver();
        SpecimenToSampleMapper specimenToSampleMapper = new SpecimenToSampleMapper();
        Transformation transformation = new TransformationRegistry(
                List.of(new Mii2025Reader(resolver, specimenToSampleMapper),
                        new Mii2026Reader(resolver, specimenToSampleMapper)),
                List.of(new BbmriDeWriter(terminology), new MiabisV3Writer(terminology)))
                .findTransformationFor(SourceFormat.MII_2026, TargetFormat.BBMRI_DE);

        // Same order as TransferPipeline: biobanks and collections first, then the donors.
        BiobankDirectory directory = transformation.reader().readDirectory(session);
        session = session.withDirectory(directory);
        written = new ArrayList<>(transformation.writer().write(directory));

        Set<String> donorIds = transformation.reader().donorIds(session);
        assertEquals(Set.of(DONOR_ID), donorIds, "the examples share one donor");

        for (String donorId : donorIds) {
            written.addAll(transformation.writer().write(transformation.reader().read(session, donorId)));
        }
    }

    // --- which resources are written ----------------------------------------------------------

    @Test
    void exactlyTheAliquotGroupsBecomeSamples() {
        // MusterprobeFluessig is a primary sample with children, AliquotBuffyCoat1/2 are aliquots,
        // and OrganoidLunge has no bbmri.de material type, so none of the four is written.
        assertEquals(EXPORTED_SAMPLES, specimens().stream().map(Mii2026ToBbmriExamplesTest::idOf)
                .sorted().toList());
    }

    @Test
    void exactlyOneDonorIsWritten() {
        assertEquals(List.of(DONOR_ID), ofType(Patient.class).stream()
                .map(Mii2026ToBbmriExamplesTest::idOf).toList());
    }
    
    @Test
    void noDiagnosisOrCauseOfDeathIsInventedWhenTheSourceHasNone() {
        assertEquals(0, ofType(Condition.class).size());
        assertEquals(0, written.stream().filter(r -> r.fhirType().equals("Observation")).count());
    }

    // --- Patient ------------------------------------------------------------------------------

    @Test
    void donorCarriesProfileGenderAndBirthDate() {
        Patient donor = ofType(Patient.class).get(0);

        assertAll(
                () -> assertTrue(hasProfile(donor, BbmriDeWriter.PATIENT_PROFILE)),
                () -> assertEquals(AdministrativeGender.FEMALE, donor.getGender()),
                () -> assertEquals(LocalDate.of(1975, 4, 12),
                        donor.getBirthDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()),
                () -> assertFalse(donor.hasDeceased()));
    }

    // --- Specimen -----------------------------------------------------------------------------

    @Test
    void everySampleHasTheBbmriSpecimenProfile() {
        forEachSample((id, specimen) -> () ->
                assertTrue(hasProfile(specimen, BbmriDeWriter.SPECIMEN_PROFILE), id));
    }

    @Test
    void everySampleReferencesTheDonor() {
        forEachSample((id, specimen) -> () ->
                assertEquals("Patient/" + DONOR_ID, specimen.getSubject().getReference(), id));
    }

    @Test
    void sampleTypeIsTranslatedToTheBbmriMaterialType() {
        // MusterprobeGewebe is SNOMED 16214371000119104, which this project treats as fresh frozen
        // tissue; the example itself codes it as MIABIS TissueFreshFrozen.
        Map<String, String> expected = Map.of(
                "AliquotgruppeBuffyCoat", "buffy-coat",
                "AliquotgruppeDNA", "dna",
                "AliquotgruppePlasma", "blood-plasma",
                "MusterprobeGewebe", "tissue-frozen");

        forEachSample((id, specimen) -> () -> {
            Coding type = specimen.getType().getCodingFirstRep();
            assertEquals(BbmriDeWriter.SAMPLE_MATERIAL_TYPE_SYSTEM, type.getSystem(), id);
            assertEquals(expected.get(id), type.getCode(), id);
        });
    }

    @Test
    void collectionDateIsKeptAsTheSameInstant() {
        Map<String, Instant> expected = Map.of(
                "AliquotgruppeBuffyCoat", Instant.parse("2018-06-07T14:54:00Z"),
                "AliquotgruppeDNA", Instant.parse("2018-06-07T14:54:00Z"),
                "AliquotgruppePlasma", Instant.parse("2018-06-07T14:54:00Z"),
                "MusterprobeGewebe", Instant.parse("2018-06-08T14:34:00Z"));

        forEachSample((id, specimen) -> () -> {
            assertTrue(specimen.getCollection().hasCollectedDateTimeType(), id);
            assertEquals(expected.get(id),
                    specimen.getCollection().getCollectedDateTimeType().getValue().toInstant(), id);
        });
    }

    @Test
    void fastingStatusIsInheritedFromThePrimarySample() {
        // The three blood derivatives inherit NG from MusterprobeFluessig, AliquotgruppeDNA across
        // two levels. MusterprobeGewebe states a fasting duration, which the bbmri.de
        // CodeableConcept cannot hold, so it has none.
        Map<String, String> expected = Map.of(
                "AliquotgruppeBuffyCoat", "NG",
                "AliquotgruppeDNA", "NG",
                "AliquotgruppePlasma", "NG");

        forEachSample((id, specimen) -> () -> {
            if (!expected.containsKey(id)) {
                assertFalse(specimen.getCollection().hasFastingStatus(), id);
                return;
            }
            Coding fasting = specimen.getCollection().getFastingStatusCodeableConcept().getCodingFirstRep();
            assertEquals(V2_0916, fasting.getSystem(), id);
            assertEquals(expected.get(id), fasting.getCode(), id);
        });
    }

    @Test
    void storageTemperatureIsTheBucketTheSampleIsStoredInNow() {
        // AliquotgruppeBuffyCoat sits at -196 to -150 C, which the example itself codes as MIABIS
        // LN; bbmri.de has no exact bucket, so it gets the closest, LN. Its aliquot AliquotBuffyCoat2
        // was later thawed and used up for DNA extraction, so only AliquotBuffyCoat1, still in
        // nitrogen, counts for the group.
        Map<String, String> expected = Map.of(
                "AliquotgruppeBuffyCoat", "temperatureLN",
                "AliquotgruppeDNA", "temperature-60to-85",
                "AliquotgruppePlasma", "temperature-60to-85",
                "MusterprobeGewebe", "temperature-60to-85");

        forEachSample((id, specimen) -> () -> {
            Extension temperature = specimen.getExtensionByUrl(BbmriDeWriter.STORAGE_TEMPERATURE_EXTENSION);
            assertNotNull(temperature, id + " has no storage temperature");
            Coding code = ((CodeableConcept) temperature.getValue()).getCodingFirstRep();
            assertEquals(BbmriDeWriter.STORAGE_TEMPERATURE_SYSTEM, code.getSystem(), id);
            assertEquals(expected.get(id), code.getCode(), id);
        });
    }

    @Test
    void aSnomedBodySiteIsLeftOut() {
        // bbmri.de only takes ICD-O-3 topography. All four examples state their body site in
        // SNOMED CT (128553008 antecubital vein, 14559000 apex of left lung), and there is no
        // SNOMED to ICD-O-3 table yet, so the writer warns and leaves the body site out.
        forEachSample((id, specimen) -> () ->
                assertFalse(specimen.getCollection().hasBodySite(), id + " should have no body site"));
    }

    @Test
    void custodianIsTheManagingCollection() {
        // MusterprobeFluessig and MusterprobeGewebe name Organization/Mustersammlung as
        // VerwaltendeOrganisation; the blood derivatives inherit it from MusterprobeFluessig.
        assertTrue(source(Organization.class).stream().anyMatch(o -> idOf(o).equals("Mustersammlung")),
                "fixture must contain the collection");

        forEachSample((id, specimen) -> () -> {
            Extension custodian = specimen.getExtensionByUrl(CUSTODIAN_EXTENSION);
            assertNotNull(custodian, id + " has no custodian");
            assertEquals("Organization/Mustersammlung", ((Reference) custodian.getValue()).getReference(), id);
        });
    }

    @Test
    void aSampleNamingACollectionThatWasNotReadGetsNoCustodian() {
        // Read the donor without the directory, as if Mustersammlung were missing from the
        // source. A custodian would then point at nothing and the target would reject the bundle.
        Bundle source = (Bundle) CTX.newJsonParser()
                .parseResource(FileUtils.readResourceFile("mii2026-bundle.json"));
        SourceSession withoutDirectory = new SourceSession(null, new BundleBackedTransfer(source));

        List<Resource> resources = new BbmriDeWriter(new Terminology()).write(
                new Mii2026Reader(new MiiSpecimenHierarchyResolver(), new SpecimenToSampleMapper())
                        .read(withoutDirectory, DONOR_ID));

        assertTrue(resources.stream().filter(Specimen.class::isInstance).map(Specimen.class::cast)
                .noneMatch(s -> s.hasExtension(CUSTODIAN_EXTENSION)));
    }

    // --- Biobank and Collection ---------------------------------------------------------------

    @Test
    void theBiobankAndTheCollectionAreBothWritten() {
        // Mustersammlung is part of BiobankMusterstadt; BiobankMusterstadt is part of nothing.
        assertEquals(List.of("BiobankMusterstadt", "Mustersammlung"), ofType(Organization.class).stream()
                .map(Mii2026ToBbmriExamplesTest::idOf).sorted().toList());
        assertTrue(hasProfile(organization("BiobankMusterstadt"), BbmriDeWriter.BIOBANK_PROFILE));
        assertTrue(hasProfile(organization("Mustersammlung"), BbmriDeWriter.COLLECTION_PROFILE));
    }

    @Test
    void biobankCarriesItsIdNameAndDescription() {
        Organization biobank = organization("BiobankMusterstadt");

        assertAll(
                () -> assertEquals(BbmriDeWriter.BBMRI_ERIC_ID_SYSTEM, biobank.getIdentifierFirstRep().getSystem()),
                () -> assertEquals("de-12345", biobank.getIdentifierFirstRep().getValue()),
                () -> assertEquals("Biobank Musterstadt", biobank.getName()),
                () -> assertEquals("Biobank des Krankenhauses Musterstadt.", descriptionOf(biobank)));
    }

    @Test
    void collectionIsPartOfTheBiobankWithItsTypeAndDataCategory() {
        // MIABIS LongitudinalCohort and RoutineHealthCare become LONGITUDINAL and HOSPITAL.
        Organization collection = organization("Mustersammlung");

        assertAll(
                () -> assertEquals("Organization/BiobankMusterstadt", collection.getPartOf().getReference()),
                () -> assertEquals("Mustersammlung", collection.getName()),
                () -> assertEquals("Sammlung mit im Rahmen der Versorgung gewonnenen Proben.",
                        descriptionOf(collection)),
                () -> assertEquals(Set.of("LONGITUDINAL", "HOSPITAL"),
                        codesOf(collection, BbmriDeWriter.COLLECTION_TYPE_EXTENSION)),
                () -> assertEquals(Set.of("BIOLOGICAL_SAMPLES"),
                        codesOf(collection, BbmriDeWriter.DATA_CATEGORY_EXTENSION)),
                // The example has no BBMRI-ERIC id for the collection, and none is made up.
                () -> assertFalse(collection.hasIdentifier()));
    }

    @Test
    void researchContactKeepsNameRoleEmailAndAddress() {
        Organization.OrganizationContactComponent contact = organization("Mustersammlung").getContactFirstRep();

        assertAll(
                () -> assertEquals(BbmriDeWriter.CONTACT_TYPE_SYSTEM, contact.getPurpose().getCodingFirstRep().getSystem()),
                () -> assertEquals("RESEARCH", contact.getPurpose().getCodingFirstRep().getCode()),
                () -> assertEquals("Forschungskoordinatorin",
                        contact.getExtensionByUrl(BbmriDeWriter.CONTACT_ROLE_EXTENSION).getValue().primitiveValue()),
                () -> assertEquals("Musterfrau", contact.getName().getFamily()),
                () -> assertEquals("Tina", contact.getName().getGivenAsSingleString()),
                () -> assertEquals("Dr.", contact.getName().getPrefixAsSingleString()),
                () -> assertEquals("musterfrau@biobank.uk-musterstadt.de", contact.getTelecomFirstRep().getValue()),
                () -> assertEquals("Musterweg 10", contact.getAddress().getLine().get(0).getValue()),
                () -> assertEquals("00000", contact.getAddress().getPostalCode()),
                () -> assertEquals("Musterstadt", contact.getAddress().getCity()));
    }

    // --- helpers ------------------------------------------------------------------------------

    private static Organization organization(String id) {
        return ofType(Organization.class).stream().filter(o -> idOf(o).equals(id)).findFirst().orElseThrow();
    }

    private static String descriptionOf(Organization organization) {
        return organization.getExtensionByUrl(BbmriDeWriter.DESCRIPTION_EXTENSION).getValue().primitiveValue();
    }

    private static Set<String> codesOf(Organization organization, String extensionUrl) {
        return organization.getExtensionsByUrl(extensionUrl).stream()
                .map(e -> ((CodeableConcept) e.getValue()).getCodingFirstRep().getCode())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static void forEachSample(BiFunction<String, Specimen, Executable> check) {
        List<Specimen> specimens = specimens();
        assertEquals(EXPORTED_SAMPLES.size(), specimens.size(), "exported samples");
        assertAll(specimens.stream().map(s -> check.apply(idOf(s), s)));
    }

    private static List<Specimen> specimens() {
        return ofType(Specimen.class);
    }

    private static <T extends Resource> List<T> ofType(Class<T> type) {
        return written.stream().filter(type::isInstance).map(type::cast).toList();
    }

    private static <T extends Resource> List<T> source(Class<T> type) {
        Bundle bundle = (Bundle) CTX.newJsonParser()
                .parseResource(FileUtils.readResourceFile("mii2026-bundle.json"));
        return bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(type::isInstance).map(type::cast).toList();
    }

    private static String idOf(Resource resource) {
        return resource.getIdElement().getIdPart();
    }

    private static boolean hasProfile(Resource resource, String profile) {
        return resource.getMeta().getProfile().stream().anyMatch(p -> p.asStringValue().equals(profile));
    }

    /** Answers the reader's queries from a bundle, the way a FHIR server holding it would. */
    private static final class BundleBackedTransfer extends FhirTransfer {
        private final List<Resource> resources;

        BundleBackedTransfer(Bundle bundle) {
            super(CTX);
            this.resources = bundle.getEntry().stream().map(Bundle.BundleEntryComponent::getResource).toList();
        }

        @Override
        public Set<String> fetchDonorReferencesFromSpecimens(IGenericClient client) {
            Set<String> references = new LinkedHashSet<>();
            all(Specimen.class).forEach(s -> references.add(s.getSubject().getReference()));
            return references;
        }

        @Override
        public <T extends IBaseResource> T fetchResource(IGenericClient client, Class<T> type, String id) {
            return all(type).stream()
                    .filter(r -> id.equals(r.getIdElement().getIdPart()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No " + type.getSimpleName() + "/" + id));
        }

        @Override
        public List<IBaseResource> fetchOrganizations(IGenericClient client) {
            return all(Organization.class).stream().map(IBaseResource.class::cast).toList();
        }

        @Override
        public List<Specimen> fetchSpecimensOfDonor(IGenericClient client, String patientId) {
            return all(Specimen.class).stream()
                    .filter(s -> ("Patient/" + patientId).equals(s.getSubject().getReference()))
                    .toList();
        }

        @Override
        public List<IBaseResource> fetchConditionsOfDonor(IGenericClient client, String patientId) {
            return all(Condition.class).stream()
                    .filter(c -> ("Patient/" + patientId).equals(c.getSubject().getReference()))
                    .map(IBaseResource.class::cast)
                    .toList();
        }

        private <T extends IBaseResource> List<T> all(Class<T> type) {
            return resources.stream().filter(type::isInstance).map(type::cast).toList();
        }
    }
}
