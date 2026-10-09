package de.samply.samplexchange.source.mii;

import ca.uhn.fhir.context.FhirContext;
import de.samply.samplexchange.FileUtils;
import de.samply.samplexchange.domain.BiobankDirectory;
import de.samply.samplexchange.domain.Contact;
import de.samply.samplexchange.domain.SampleCollection;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.MarkdownType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MII uses one Organization profile for biobanks and collections, so these tests are about which
 * is which and how a collection finds its biobank.
 */
class MiiOrganizationReaderTest {

    private static final FhirContext CTX = FhirContext.forR4();

    private static Organization organization(String id, String partOf) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setName(id);
        organization.setMeta(new Meta().addProfile(MiiOrganizationReader.ORGANIZATION_PROFILE));
        if (partOf != null) {
            organization.setPartOf(new Reference("Organization/" + partOf));
        }
        return organization;
    }

    private static SampleCollection collection(BiobankDirectory directory, String id) {
        return directory.collections().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void theOfficialExamplesAreOneBiobankWithOneCollection() {
        List<Organization> examples = List.of("BiobankMusterstadt", "Mustersammlung").stream()
                .map(id -> CTX.newJsonParser().parseResource(Organization.class,
                        FileUtils.readResourceFile("mii2026/Organization-" + id + ".json")))
                .toList();

        BiobankDirectory directory = MiiOrganizationReader.toDirectory(examples);

        assertEquals(List.of("BiobankMusterstadt"), directory.biobanks().stream().map(b -> b.id()).toList());
        SampleCollection collection = collection(directory, "Mustersammlung");
        assertEquals("BiobankMusterstadt", collection.biobankId());
        assertNull(collection.parentCollectionId());
        assertEquals("de-12345", directory.biobanks().get(0).bbmriEricId());
        assertEquals(List.of("LongitudinalCohort", "RoutineHealthCare"),
                collection.types().stream().map(t -> t.code()).sorted().toList());
        assertEquals("Forschungskoordinatorin", collection.contacts().get(0).role());
    }

    @Test
    void aNestedCollectionBelongsToTheBiobankAtTheTopAndNamesItsParent() {
        BiobankDirectory directory = MiiOrganizationReader.toDirectory(List.of(
                organization("biobank", null),
                organization("collection", "biobank"),
                organization("sub-collection", "collection")));

        SampleCollection sub = collection(directory, "sub-collection");
        assertEquals("biobank", sub.biobankId());
        assertEquals("collection", sub.parentCollectionId());
        assertNull(collection(directory, "collection").parentCollectionId());
    }

    @Test
    void aCollectionWhoseBiobankIsMissingKeepsNoBiobank() {
        // A reference the target could not resolve is worse than none.
        BiobankDirectory directory = MiiOrganizationReader.toDirectory(List.of(
                organization("orphan", "not-in-the-source")));

        assertNull(collection(directory, "orphan").biobankId());
        assertTrue(directory.biobanks().isEmpty());
    }

    @Test
    void aPartOfCycleDoesNotLoop() {
        BiobankDirectory directory = MiiOrganizationReader.toDirectory(List.of(
                organization("a", "b"), organization("b", "a")));

        assertNull(collection(directory, "a").biobankId());
        assertNull(collection(directory, "b").biobankId());
    }

    @Test
    void organizationsWithoutTheMiiBiobankProfileAreIgnored() {
        // A source server also holds hospitals, departments and so on.
        Organization hospital = new Organization();
        hospital.setId("hospital");

        BiobankDirectory directory = MiiOrganizationReader.toDirectory(List.of(hospital, organization("biobank", null)));

        assertEquals(List.of("biobank"), directory.biobanks().stream().map(b -> b.id()).toList());
    }

    @Test
    void aContactWithoutAFamilyNameIsLeftOut() {
        Organization biobank = organization("biobank", null);
        biobank.addContact().getName().addGiven("Max");
        biobank.addContact().getName().setFamily("Mustermann");

        assertEquals(List.of("Mustermann"), MiiOrganizationReader.toDirectory(List.of(biobank))
                .biobanks().get(0).contacts().stream().map(c -> c.family()).toList());
    }

    @Test
    void partsWithoutAValueAreLeftOutAndTheContactIsKept() {
        // An element can carry only an extension, for example a data absent reason, and no value.
        Organization biobank = organization("biobank", null);
        Organization.OrganizationContactComponent contact = biobank.addContact();
        contact.getName().setFamily("Mustermann").addGivenElement()
                .addExtension("http://hl7.org/fhir/StructureDefinition/data-absent-reason", new CodeType("unknown"));
        contact.addTelecom().setSystem(ContactPoint.ContactPointSystem.EMAIL)
                .addExtension("http://hl7.org/fhir/StructureDefinition/data-absent-reason", new CodeType("masked"));
        contact.getAddress().addLine("Musterstrasse 3").addLineElement();

        Contact read = MiiOrganizationReader.toDirectory(List.of(biobank)).biobanks().get(0).contacts().get(0);

        assertEquals("Mustermann", read.family());
        assertEquals(List.of(), read.given());
        assertNull(read.email());
        assertEquals(List.of("Musterstrasse 3"), read.addressLines());
    }

    @Test
    void theMii2025DescriptionExtensionIsReadToo() {
        Organization biobank = organization("biobank", null);
        biobank.addExtension(
                "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/BeschreibungSammlung",
                new MarkdownType("Beschreibung aus 2025"));

        assertEquals("Beschreibung aus 2025",
                MiiOrganizationReader.toDirectory(List.of(biobank)).biobanks().get(0).description());
    }

    @Test
    void theMiabisExtensionsAreReadWithTheFhirPathSegmentToo() {
        // The MII 2026 profile spells the MIABIS canonicals with /fhir/; the examples do not.
        Organization collection = organization("collection", "biobank");
        CodeableConcept design = new CodeableConcept();
        design.getCodingFirstRep()
                .setSystem("https://fhir.bbmri-eric.eu/fhir/CodeSystem/miabis-collection-design-cs")
                .setCode("CaseControl");
        collection.addExtension(
                "https://fhir.bbmri-eric.eu/fhir/StructureDefinition/miabis-collection-design-extension", design);

        BiobankDirectory directory = MiiOrganizationReader.toDirectory(
                List.of(organization("biobank", null), collection));

        assertEquals("CaseControl", collection(directory, "collection").types().get(0).code());
    }
}
