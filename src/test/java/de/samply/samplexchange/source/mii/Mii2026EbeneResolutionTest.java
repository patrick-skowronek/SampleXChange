package de.samply.samplexchange.source.mii;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Specimen;
import org.junit.jupiter.api.Test;

import ca.uhn.fhir.context.FhirContext;
import de.samply.samplexchange.FileUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class Mii2026EbeneResolutionTest {

    private static final FhirContext CTX = FhirContext.forR4();

    private static final List<String> EXAMPLES = List.of(
            "MusterprobeFluessig", "MusterprobeGewebe",
            "AliquotgruppeBuffyCoat", "AliquotgruppePlasma", "AliquotgruppeDNA",
            "AliquotBuffyCoat1", "AliquotBuffyCoat2", "OrganoidLunge");

    private final MiiSpecimenHierarchyResolver resolver = new MiiSpecimenHierarchyResolver();

    private static List<Specimen> exampleSpecimens() {
        return EXAMPLES.stream()
                .map(id -> CTX.newJsonParser().parseResource(Specimen.class,
                        FileUtils.readResourceFile("mii2026/Specimen-" + id + ".json")))
                .toList();
    }

    private Map<String, SampleLevel> levels(List<Specimen> specimens) {
        return resolver.resolve(specimens).stream()
                .collect(Collectors.toMap(SpecimenNode::getId, SpecimenNode::getLevel));
    }

    @Test
    void declaredLevelsAreHonoured() {
        Map<String, SampleLevel> levels = levels(exampleSpecimens());

        assertEquals(SampleLevel.MOTHER, levels.get("MusterprobeFluessig"));
        assertEquals(SampleLevel.ALIQUOT_GROUP, levels.get("MusterprobeGewebe"));
        assertEquals(SampleLevel.ALIQUOT_GROUP, levels.get("AliquotgruppeBuffyCoat"));
        assertEquals(SampleLevel.ALIQUOT_GROUP, levels.get("AliquotgruppePlasma"));
        assertEquals(SampleLevel.ALIQUOT_GROUP, levels.get("AliquotgruppeDNA"));
        assertEquals(SampleLevel.ALIQUOT, levels.get("AliquotBuffyCoat1"));
        assertEquals(SampleLevel.ALIQUOT, levels.get("AliquotBuffyCoat2"));
        assertEquals(SampleLevel.STANDALONE, levels.get("OrganoidLunge"));
    }

    @Test
    void exportedSetIsExactlyTheDeclaredAliquotGroups() {
        List<String> exported = resolver.resolveExportable(exampleSpecimens()).stream()
                .map(SpecimenNode::getId).sorted().toList();

        // The organoid is structurally standalone and comes through here. bbmri.de drops it later
        // because it has no code for a cell culture; MIABIS keeps it.
        assertEquals(List.of(
                "AliquotgruppeBuffyCoat", "AliquotgruppeDNA",
                "AliquotgruppePlasma", "MusterprobeGewebe", "OrganoidLunge"), exported);
    }

    @Test
    void inferenceWouldDisagreeWhereTheExtensionIsAuthoritative() {
        // Same graph, extensions stripped. Documents why the explicit signal is required rather
        // than a nicety: inference both under-exports (an aliquot group with no aliquots yet looks
        // like an aliquot) and over-exports (a primary sample with a leaf child looks like a group).
        List<Specimen> stripped = exampleSpecimens().stream()
                .map(s -> {
                    Specimen copy = s.copy();
                    copy.getExtension().removeIf(e -> MiiSpecimenHierarchyResolver
                            .SAMPLE_LEVEL_EXTENSION_URL.equals(e.getUrl()));
                    return copy;
                })
                .toList();

        Map<String, SampleLevel> inferred = levels(stripped);

        assertEquals(SampleLevel.ALIQUOT_GROUP, inferred.get("MusterprobeFluessig"));
        assertNotEquals(SampleLevel.MOTHER, inferred.get("MusterprobeFluessig"));
        assertEquals(SampleLevel.ALIQUOT, inferred.get("AliquotgruppePlasma"));
        assertEquals(SampleLevel.ALIQUOT, inferred.get("AliquotgruppeDNA"));
        assertEquals(SampleLevel.ALIQUOT_GROUP, inferred.get("AliquotBuffyCoat2"));

        List<String> exportedByInference = resolver.resolveExportable(stripped).stream()
                .map(SpecimenNode::getId).sorted().toList();
        assertNotEquals(List.of(
                "AliquotgruppeBuffyCoat", "AliquotgruppeDNA",
                "AliquotgruppePlasma", "MusterprobeGewebe"), exportedByInference);
    }

    @Test
    void declaredPrimarySampleWithNoAliquotsIsStillExported() {
        Specimen primary = new Specimen();
        primary.setId("lonely-primary");
        primary.addExtension(ebene("PRIMÄRPROBE"));

        SpecimenNode node = resolver.resolve(List.of(primary)).get(0);

        assertEquals(SampleLevel.STANDALONE, node.getLevel());
        assertEquals(List.of("lonely-primary"),
                resolver.resolveExportable(List.of(primary)).stream().map(SpecimenNode::getId).toList());
    }

    @Test
    void unknownLevelCodeFallsBackToInference() {
        Specimen group = new Specimen();
        group.setId("group");
        group.addExtension(ebene("SOMETHING_ELSE"));
        Specimen aliquot = new Specimen();
        aliquot.setId("aliquot");
        aliquot.addParent(new Reference("Specimen/group"));

        Map<String, SampleLevel> levels = levels(List.of(group, aliquot));

        assertEquals(SampleLevel.ALIQUOT_GROUP, levels.get("group"));
        assertEquals(SampleLevel.ALIQUOT, levels.get("aliquot"));
    }

    @Test
    void nonCodingValueFallsBackToInference() {
        Specimen solo = new Specimen();
        solo.setId("solo");
        Extension wrongType = new Extension();
        wrongType.setUrl(MiiSpecimenHierarchyResolver.SAMPLE_LEVEL_EXTENSION_URL);
        wrongType.setValue(new org.hl7.fhir.r4.model.StringType("ALIQUOTGRUPPE"));
        solo.addExtension(wrongType);

        assertEquals(SampleLevel.STANDALONE, resolver.resolve(List.of(solo)).get(0).getLevel());
    }

    private static Extension ebene(String code) {
        Extension extension = new Extension();
        extension.setUrl(MiiSpecimenHierarchyResolver.SAMPLE_LEVEL_EXTENSION_URL);
        extension.setValue(new Coding()
                .setSystem(MiiSpecimenHierarchyResolver.SAMPLE_LEVEL_CODE_SYSTEM)
                .setCode(code));
        return extension;
    }
}
