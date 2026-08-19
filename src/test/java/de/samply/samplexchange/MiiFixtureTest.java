package de.samply.samplexchange;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Specimen;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the MII test fixture, ported from TransFAIR (src/test/resources/mii.json).
 *
 * <p>This documents what the fixture contains so the mapping tests that rely on it fail with a
 * clear reason if it is ever edited.
 */
class MiiFixtureTest {

    private static final FhirContext CTX = FhirContext.forR4();

    @Test
    void fixtureLoadsAsTransactionBundle() {
        Bundle bundle = (Bundle) CTX.newJsonParser()
                .parseResource(FileUtils.readResourceFile("mii.json"));

        assertEquals(Bundle.BundleType.TRANSACTION, bundle.getType());
        assertEquals(6, bundle.getEntry().size(), "fixture is expected to hold 6 resources");
    }

    @Test
    void specimenHasStorageProcessingButNoParent() {
        Bundle bundle = (Bundle) CTX.newJsonParser()
                .parseResource(FileUtils.readResourceFile("mii.json"));

        Specimen specimen = bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(Specimen.class::isInstance)
                .map(Specimen.class::cast)
                .findFirst()
                .orElseThrow();

        assertEquals("MusterprobeFluessig", specimen.getIdElement().getIdPart());
        assertEquals(4, specimen.getProcessing().size());

        // No parent: this fixture is a standalone / mother sample. Under the aliquot-group rule in
        // docs/adr/0001-mapping-architecture.md this is the "root with no children" edge case.
        assertFalse(specimen.hasParent(), "fixture specimen is expected to have no parent");

        long storageSteps = specimen.getProcessing().stream()
                .filter(p -> p.getProcedure().hasCoding("http://snomed.info/sct", "1186936003"))
                .count();
        assertEquals(3, storageSteps, "3 of 4 processing steps carry the storage procedure code");
        assertTrue(specimen.getProcessing().stream().allMatch(p -> p.getTimePeriod().hasStart()));
    }
}
