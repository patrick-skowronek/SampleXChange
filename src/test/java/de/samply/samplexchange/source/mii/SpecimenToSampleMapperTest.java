package de.samply.samplexchange.source.mii;

import ca.uhn.fhir.context.FhirContext;
import de.samply.samplexchange.FileUtils;
import de.samply.samplexchange.domain.Sample;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Range;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Specimen;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A sample takes its values from the whole specimen tree rather than from one resource, so these
 * tests are mostly about which specimen each value is read from.
 */
class SpecimenToSampleMapperTest {

    private static final String SNOMED = "http://snomed.info/sct";
    private static final String TEMPERATURE_EXTENSION_URL =
            "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/Temperaturbedingungen";
    private static final FhirContext CTX = FhirContext.forR4();

    private final MiiSpecimenHierarchyResolver resolver = new MiiSpecimenHierarchyResolver();
    private final SpecimenToSampleMapper specimenToSampleMapper = new SpecimenToSampleMapper();

    private static Specimen specimen(String id, String parentId) {
        Specimen specimen = new Specimen();
        specimen.setId(id);
        if (parentId != null) {
            specimen.addParent(new Reference("Specimen/" + parentId));
        }
        return specimen;
    }

    private static Specimen.SpecimenProcessingComponent storageStep(String start, int high, int low) {
        Specimen.SpecimenProcessingComponent step = new Specimen.SpecimenProcessingComponent();
        step.getProcedure().getCodingFirstRep().setSystem(SNOMED).setCode("1186936003");
        if (start != null) {
            step.setTime(new Period().setStartElement(new DateTimeType(start)));
        }
        Extension temperature = new Extension();
        temperature.setUrl(TEMPERATURE_EXTENSION_URL);
        temperature.setValue(new Range().setHigh(new Quantity(high)).setLow(new Quantity(low)));
        step.addExtension(temperature);
        return step;
    }

    private Sample mapTheOnlyExportedSpecimen(List<Specimen> input) {
        List<SpecimenNode> exportable = resolver.resolveExportable(input);
        assertEquals(1, exportable.size(), "expected exactly one exported node");
        return specimenToSampleMapper.toSample(exportable.get(0));
    }

    @Test
    void idIsThePlainIdPartNotTheServerUrl() {
        // Read from a server, Specimen.getId() is an absolute URL with a history version. That
        // must not end up in the exported resource.
        Specimen group = specimen("http://example.com/fhir/Specimen/group-1/_history/2", null);

        Sample sample = mapTheOnlyExportedSpecimen(List.of(group));

        assertEquals("group-1", sample.id());
    }

    @Test
    void donorIsInheritedFromTheMotherWhenTheGroupHasNone() {
        Specimen mother = specimen("mother", null);
        mother.setSubject(new Reference("Patient/donor-7"));
        Specimen group = specimen("group", "mother");
        Specimen aliquot = specimen("aliquot", "group");

        Sample sample = mapTheOnlyExportedSpecimen(List.of(mother, group, aliquot));

        assertEquals("group", sample.id());
        assertEquals("donor-7", sample.donorId());
    }

    @Test
    void theManagingCollectionIsInheritedFromTheNearestAncestorThatNamesOne() {
        // In the MII examples only the primary samples name VerwaltendeOrganisation.
        Specimen mother = specimen("mother", null);
        mother.addExtension(SpecimenToSampleMapper.MANAGING_ORGANIZATION_EXTENSION_URL,
                new Reference("Organization/Mustersammlung"));
        Specimen group = specimen("group", "mother");
        Specimen aliquot = specimen("aliquot", "group");

        assertEquals("Mustersammlung", mapTheOnlyExportedSpecimen(List.of(mother, group, aliquot)).collectionId());
    }

    @Test
    void collectionDetailsAreInheritedFromTheNearestAncestorThatHasThem() {
        Specimen mother = specimen("mother", null);
        mother.getCollection().setCollected(new DateTimeType("2018-06-07T15:54:00+01:00"));
        mother.getCollection().getBodySite().getCodingFirstRep()
                .setSystem("http://terminology.hl7.org/CodeSystem/icd-o-3").setCode("8148/2");
        mother.getCollection().getFastingStatusCodeableConcept().getCodingFirstRep()
                .setSystem("http://terminology.hl7.org/CodeSystem/v2-0916").setCode("F");

        Specimen group = specimen("group", "mother");
        Specimen aliquot = specimen("aliquot", "group");

        Sample sample = mapTheOnlyExportedSpecimen(List.of(mother, group, aliquot));

        assertEquals("2018-06-07T15:54:00+01:00", sample.collectedAt().getValueAsString());
        assertEquals("8148/2", sample.bodySite().code());
        assertEquals("F", sample.fastingStatus().code());
    }

    @Test
    void theGroupsOwnValuesWinOverTheAncestors() {
        Specimen mother = specimen("mother", null);
        mother.getCollection().getBodySite().getCodingFirstRep().setCode("from-mother");
        Specimen group = specimen("group", "mother");
        group.getCollection().getBodySite().getCodingFirstRep().setCode("from-group");
        Specimen aliquot = specimen("aliquot", "group");

        assertEquals("from-group", mapTheOnlyExportedSpecimen(List.of(mother, group, aliquot)).bodySite().code());
    }

    @Test
    void anIcdO3BodySiteIsPreferredOverASnomedOneListedFirst() {
        // MII 2025 has a SNOMED and an ICD-O-3 slice. Taking the first coding would hand bbmri.de
        // a SNOMED code it has to drop while the ICD-O-3 one sits right behind it.
        Specimen group = specimen("group", null);
        group.getCollection().getBodySite().addCoding()
                .setSystem("http://snomed.info/sct").setCode("14559000");
        group.getCollection().getBodySite().addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/icd-o-3").setCode("C34.1");
        Specimen aliquot = specimen("aliquot", "group");

        assertEquals("C34.1", mapTheOnlyExportedSpecimen(List.of(group, aliquot)).bodySite().code());
    }

    @Test
    void storageTemperatureComesFromTheAliquotsBelow() {
        Specimen group = specimen("group", null);
        Specimen aliquot = specimen("aliquot", "group");
        aliquot.addProcessing(storageStep("2018-06-07T16:51:00+01:00", -160, -195));

        Sample sample = mapTheOnlyExportedSpecimen(List.of(group, aliquot));

        assertNotNull(sample.storageTemperature());
        assertEquals(-160L, sample.storageTemperature().high());
        assertEquals(-195L, sample.storageTemperature().low());
    }

    @Test
    void aUsedUpAliquotDoesNotDecideTheStorageTemperature() {
        // The used-up aliquot was thawed later, which is the newest step overall. The group is
        // still in liquid nitrogen through the aliquot that is left.
        Specimen group = specimen("group", null);
        Specimen left = specimen("aliquot-left", "group");
        left.addProcessing(storageStep("2018-06-07T17:07:00+01:00", -150, -196));
        Specimen usedUp = specimen("aliquot-used", "group").setStatus(Specimen.SpecimenStatus.UNAVAILABLE);
        usedUp.addProcessing(storageStep("2018-09-07T13:02:00+01:00", 25, 15));

        Sample sample = mapTheOnlyExportedSpecimen(List.of(group, left, usedUp));

        assertEquals(-150L, sample.storageTemperature().high());
        assertEquals(-196L, sample.storageTemperature().low());
    }

    @Test
    void theLatestStorageStepWinsRegardlessOfDocumentOrder() {
        // The old code compared every step against a 1900 sentinel it never advanced, so the last
        // step in the array won. Here the later step is written first.
        Specimen group = specimen("group", null);
        group.addProcessing(storageStep("2018-06-07T18:00:00+01:00", -160, -195));
        group.addProcessing(storageStep("2018-06-07T09:00:00+01:00", 10, 2));
        Specimen aliquot = specimen("aliquot", "group");

        Sample sample = mapTheOnlyExportedSpecimen(List.of(group, aliquot));

        assertEquals(-160L, sample.storageTemperature().high());
        assertEquals(-195L, sample.storageTemperature().low());
    }

    @Test
    void aStorageStepWithoutATimeIsStillUsedWhenNothingElseExists() {
        // The old code skipped these outright and silently lost the temperature.
        Specimen group = specimen("group", null);
        group.addProcessing(storageStep(null, -60, -85));
        Specimen aliquot = specimen("aliquot", "group");

        Sample sample = mapTheOnlyExportedSpecimen(List.of(group, aliquot));

        assertEquals(-60L, sample.storageTemperature().high());
        assertEquals(-85L, sample.storageTemperature().low());
    }

    @Test
    void aTimedStepIsPreferredOverAnUntimedOne() {
        Specimen group = specimen("group", null);
        group.addProcessing(storageStep(null, -60, -85));
        group.addProcessing(storageStep("2018-06-07T09:00:00+01:00", 10, 2));
        Specimen aliquot = specimen("aliquot", "group");

        assertEquals(10L, mapTheOnlyExportedSpecimen(List.of(group, aliquot)).storageTemperature().high());
    }

    @Test
    void nonStorageProcessingStepsAreIgnored() {
        Specimen group = specimen("group", null);
        Specimen.SpecimenProcessingComponent centrifuging =
                storageStep("2018-06-07T16:27:00+01:00", 10, 2);
        centrifuging.getProcedure().getCodingFirstRep().setCode("73373003");
        group.addProcessing(centrifuging);
        Specimen aliquot = specimen("aliquot", "group");

        assertNull(mapTheOnlyExportedSpecimen(List.of(group, aliquot)).storageTemperature());
    }

    @Test
    void missingAttributesStayNullRatherThanBecomingEmptyValues() {
        Sample sample = mapTheOnlyExportedSpecimen(List.of(specimen("solo", null)));

        assertEquals("solo", sample.id());
        assertNull(sample.donorId());
        assertTrue(sample.types().isEmpty());
        assertNull(sample.collectedAt());
        assertNull(sample.bodySite());
        assertNull(sample.fastingStatus());
        assertNull(sample.storageTemperature());
    }

    @Test
    void mapsTheOfficialMii2026Hierarchy() {
        List<String> ids = List.of(
                "MusterprobeFluessig", "MusterprobeGewebe",
                "AliquotgruppeBuffyCoat", "AliquotgruppePlasma", "AliquotgruppeDNA",
                "AliquotBuffyCoat1", "AliquotBuffyCoat2", "OrganoidLunge");
        List<Specimen> specimens = ids.stream()
                .map(id -> CTX.newJsonParser().parseResource(Specimen.class,
                        FileUtils.readResourceFile("mii2026/Specimen-" + id + ".json")))
                .toList();

        List<Sample> samples = specimenToSampleMapper.toSamples(resolver.resolveExportable(specimens));
        Map<String, Sample> byId = samples.stream()
                .collect(Collectors.toMap(Sample::id, Function.identity()));

        // Four aliquot groups plus the organoid, which each target then decides on.
        assertEquals(5, samples.size());
        // Every exported sample resolves a donor, the aliquot groups by inheriting it.
        samples.forEach(sample -> assertNotNull(sample.donorId(), sample.id() + " has no donor"));
        assertFalse(byId.get("AliquotgruppeBuffyCoat").types().isEmpty());
    }

    @Test
    void readsStorageTemperatureFromRealMii2026Data() {
        // 2026 added a MIABIS storage temperature extension alongside Temperaturbedingungen rather
        // than replacing it, so the existing lookup still applies. AliquotgruppePlasma has two
        // storage steps, at 16:37 (15 to 25) and 17:17 (-85 to -75). The later one wins, and the
        // source agrees: its MIABIS coded extension on that step reads -60to-85.
        Specimen group = CTX.newJsonParser().parseResource(Specimen.class,
                FileUtils.readResourceFile("mii2026/Specimen-AliquotgruppePlasma.json"));

        Sample sample = specimenToSampleMapper.toSample(resolver.resolve(List.of(group)).get(0));

        assertNotNull(sample.storageTemperature(), "2026 storage temperature should be readable");
        assertEquals(-75L, sample.storageTemperature().high());
        assertEquals(-85L, sample.storageTemperature().low());
    }
}
