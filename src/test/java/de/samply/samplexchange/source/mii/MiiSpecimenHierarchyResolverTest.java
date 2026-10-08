package de.samply.samplexchange.source.mii;

import ca.uhn.fhir.context.FhirContext;
import de.samply.samplexchange.FileUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Specimen;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the aliquot-group rule: the exported level is the set of parents of leaves, plus
 * standalone specimens. Depth is never assumed.
 */
class MiiSpecimenHierarchyResolverTest {

    private static final FhirContext CTX = FhirContext.forR4();

    /** The real 2026 examples, which form a genuine multi-level hierarchy. */
    private static final List<String> MII_2026_EXAMPLES = List.of(
            "MusterprobeFluessig", "MusterprobeGewebe",
            "AliquotgruppeBuffyCoat", "AliquotgruppePlasma", "AliquotgruppeDNA",
            "AliquotBuffyCoat1", "AliquotBuffyCoat2", "OrganoidLunge");

    private final MiiSpecimenHierarchyResolver resolver = new MiiSpecimenHierarchyResolver();

    private static List<Specimen> mii2026Examples() {
        return MII_2026_EXAMPLES.stream()
                .map(id -> CTX.newJsonParser().parseResource(Specimen.class,
                        FileUtils.readResourceFile("mii2026/Specimen-" + id + ".json")))
                .toList();
    }

    /** The single specimen in the 2025 fixture, which carries no sample level extension. */
    private static Specimen mii2025Specimen() {
        Bundle bundle = (Bundle) CTX.newJsonParser().parseResource(FileUtils.readResourceFile("mii.json"));
        return bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(Specimen.class::isInstance)
                .map(Specimen.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static List<Specimen> withoutLevelExtensions(List<Specimen> specimens) {
        return specimens.stream().map(original -> {
            Specimen copy = original.copy();
            copy.getExtension().removeIf(
                    e -> MiiSpecimenHierarchyResolver.SAMPLE_LEVEL_EXTENSION_URL.equals(e.getUrl()));
            return copy;
        }).toList();
    }

    private SpecimenNode node(List<SpecimenNode> nodes, String id) {
        return nodes.stream().filter(n -> n.getId().equals(id)).findFirst().orElseThrow();
    }

    private static Specimen specimen(String id, String parentId) {
        Specimen specimen = new Specimen();
        specimen.setId(id);
        if (parentId != null) {
            specimen.addParent(new Reference("Specimen/" + parentId));
        }
        return specimen;
    }

    private Map<String, SampleLevel> levels(List<Specimen> specimens) {
        return resolver.resolve(specimens).stream()
                .collect(Collectors.toMap(SpecimenNode::getId, SpecimenNode::getLevel));
    }

    private List<String> exportedIds(List<Specimen> specimens) {
        return resolver.resolveExportable(specimens).stream().map(SpecimenNode::getId).sorted().toList();
    }

    @Test
    void standaloneSpecimenIsExported() {
        // A sample that was never aliquoted is still a sample the biobank holds. This is the shape
        // of the mii.json fixture.
        List<Specimen> input = List.of(specimen("solo", null));

        assertEquals(Map.of("solo", SampleLevel.STANDALONE), levels(input));
        assertEquals(List.of("solo"), exportedIds(input));
    }

    @Test
    void twoLevelsMakeTheMotherTheAliquotGroup() {
        // mother -> aliquots. The mother is itself the last level before the aliquots.
        List<Specimen> input = List.of(
                specimen("mother", null),
                specimen("aliquot-1", "mother"),
                specimen("aliquot-2", "mother"));

        assertEquals(Map.of(
                "mother", SampleLevel.ALIQUOT_GROUP,
                "aliquot-1", SampleLevel.ALIQUOT,
                "aliquot-2", SampleLevel.ALIQUOT), levels(input));
        assertEquals(List.of("mother"), exportedIds(input));
    }

    @Test
    void threeLevelsExportOnlyTheGroup() {
        List<Specimen> input = List.of(
                specimen("mother", null),
                specimen("group", "mother"),
                specimen("aliquot-1", "group"),
                specimen("aliquot-2", "group"));

        assertEquals(Map.of(
                "mother", SampleLevel.MOTHER,
                "group", SampleLevel.ALIQUOT_GROUP,
                "aliquot-1", SampleLevel.ALIQUOT,
                "aliquot-2", SampleLevel.ALIQUOT), levels(input));
        assertEquals(List.of("group"), exportedIds(input));
    }

    @Test
    void fourLevelsBehaveIdenticallyBecauseDepthIsNotAssumed() {
        List<Specimen> input = List.of(
                specimen("mother", null),
                specimen("intermediate", "mother"),
                specimen("group", "intermediate"),
                specimen("aliquot", "group"));

        assertEquals(Map.of(
                "mother", SampleLevel.MOTHER,
                "intermediate", SampleLevel.INTERMEDIATE,
                "group", SampleLevel.ALIQUOT_GROUP,
                "aliquot", SampleLevel.ALIQUOT), levels(input));
        assertEquals(List.of("group"), exportedIds(input));
    }

    @Test
    void severalAliquotGroupsUnderOneMotherAreAllExported() {
        List<Specimen> input = List.of(
                specimen("mother", null),
                specimen("group-a", "mother"),
                specimen("group-b", "mother"),
                specimen("aliquot-a1", "group-a"),
                specimen("aliquot-b1", "group-b"));

        assertEquals(List.of("group-a", "group-b"), exportedIds(input));
        assertEquals(SampleLevel.MOTHER, levels(input).get("mother"));
    }

    @Test
    void mixedLeafAndNonLeafChildrenStillCountAsAliquotGroup() {
        // Ambiguous shape; the rule still applies and the resolver logs a warning.
        List<Specimen> input = List.of(
                specimen("mother", null),
                specimen("aliquot-direct", "mother"),
                specimen("group", "mother"),
                specimen("aliquot-nested", "group"));

        Map<String, SampleLevel> levels = levels(input);
        assertEquals(SampleLevel.ALIQUOT_GROUP, levels.get("mother"));
        assertEquals(SampleLevel.ALIQUOT_GROUP, levels.get("group"));
        assertEquals(List.of("group", "mother"), exportedIds(input));
    }

    @Test
    void aGroupWithOneAliquotLeftIsStillExported() {
        Specimen usedUp = specimen("aliquot-used", "group").setStatus(Specimen.SpecimenStatus.UNAVAILABLE);
        List<Specimen> input = List.of(specimen("group", null), usedUp, specimen("aliquot-left", "group"));

        assertEquals(List.of("group"), exportedIds(input));
        assertEquals(List.of("aliquot-left"), resolver.resolve(input).stream()
                .filter(n -> n.getId().equals("group")).findFirst().orElseThrow()
                .getRemainingAliquots().stream().map(SpecimenNode::getId).toList());
    }

    @Test
    void aGroupWhoseAliquotsAreAllUsedUpIsNotExported() {
        List<Specimen> input = List.of(
                specimen("group", null),
                specimen("aliquot-1", "group").setStatus(Specimen.SpecimenStatus.UNAVAILABLE),
                specimen("aliquot-2", "group").setStatus(Specimen.SpecimenStatus.ENTEREDINERROR));

        assertEquals(List.of(), exportedIds(input));
    }

    @Test
    void anAliquotWithAGroupBelowNoLongerCountsForItsOwnGroup() {
        // group -> aliquot -> derived-group -> derived-aliquot. The aliquot was processed into a
        // group of its own, so the upper group has nothing left; the derived group is exported.
        List<Specimen> input = List.of(
                specimen("group", null),
                specimen("aliquot", "group"),
                specimen("derived-group", "aliquot"),
                specimen("derived-aliquot", "derived-group"));

        assertEquals(List.of("derived-group"), exportedIds(input));
    }

    @Test
    void aDeclaredGroupWithNoAliquotsRecordedIsExported() {
        // AliquotgruppePlasma in the examples: four aliquots counted, none sent as a resource.
        // Missing aliquots are not used-up aliquots.
        assertTrue(resolver.resolveExportable(mii2026Examples()).stream()
                .anyMatch(n -> n.getId().equals("AliquotgruppePlasma")));
    }

    @Test
    void theRealBuffyCoatGroupKeepsOnlyTheAliquotStillInStorage() {
        // AliquotBuffyCoat2 is unavailable and AliquotgruppeDNA was derived from it.
        // AliquotBuffyCoat1 is left, so both the buffy coat group and the DNA group are exported.
        List<SpecimenNode> nodes = resolver.resolve(mii2026Examples());

        assertEquals(List.of("AliquotBuffyCoat1"), node(nodes, "AliquotgruppeBuffyCoat")
                .getRemainingAliquots().stream().map(SpecimenNode::getId).toList());
        assertTrue(node(nodes, "AliquotgruppeBuffyCoat").isExportable());
        assertTrue(node(nodes, "AliquotgruppeDNA").isExportable());
    }

    @Test
    void parentOutsideTheDonorSetIsTreatedAsRoot() {
        // Keeps the specimen in the export instead of dropping it silently.
        List<Specimen> input = List.of(specimen("orphan", "not-fetched"));

        assertEquals(Map.of("orphan", SampleLevel.STANDALONE), levels(input));
        assertEquals(List.of("orphan"), exportedIds(input));
    }

    @Test
    void selfReferencingParentIsIgnored() {
        List<Specimen> input = List.of(specimen("loop", "loop"));

        SpecimenNode node = resolver.resolve(input).get(0);
        assertNull(node.getParent());
        assertTrue(node.getChildren().isEmpty());
        assertEquals(SampleLevel.STANDALONE, node.getLevel());
    }

    @Test
    void specimenWithoutIdIsSkipped() {
        List<Specimen> input = List.of(new Specimen(), specimen("solo", null));

        assertEquals(List.of("solo"), exportedIds(input));
        assertEquals(1, resolver.resolve(input).size());
    }

    @Test
    void emptyInputYieldsNothing() {
        assertEquals(List.of(), resolver.resolve(List.of()));
        assertEquals(List.of(), resolver.resolveExportable(List.of()));
    }

    @Test
    void everyInputSpecimenAppearsExactlyOnceAndKeepsItsResource() {
        Specimen mother = specimen("mother", null);
        Specimen aliquot = specimen("aliquot", "mother");

        List<SpecimenNode> nodes = resolver.resolve(List.of(mother, aliquot));

        assertEquals(2, nodes.size());
        Map<String, SpecimenNode> byId = nodes.stream()
                .collect(Collectors.toMap(SpecimenNode::getId, Function.identity()));
        assertSame(mother, byId.get("mother").getSpecimen());
        assertSame(aliquot, byId.get("aliquot").getSpecimen());
        assertSame(byId.get("mother"), byId.get("aliquot").getParent());
    }

    @Test
    void summariseReportsLevelCounts() {
        List<SpecimenNode> nodes = resolver.resolve(List.of(
                specimen("mother", null),
                specimen("group", "mother"),
                specimen("aliquot", "group")));

        assertEquals("1 MOTHER, 1 ALIQUOT_GROUP, 1 ALIQUOT", MiiSpecimenHierarchyResolver.countByLevel(nodes));
    }

    private static Specimen organoid(String id, String parentId) {
        Specimen specimen = specimen(id, parentId);
        specimen.getMeta().addProfile(MiiSpecimenHierarchyResolver.CELL_LINE_AND_ORGANOID_PROFILE);
        return specimen;
    }

    @Test
    void anOrganoidIsStandaloneEvenThoughItHasAParent() {
        // A cell line or organoid is derived from a sample, so it carries a parent reference, but
        // it is a thing in its own right rather than a portion of that sample.
        List<Specimen> input = List.of(specimen("mother", null), organoid("organoid", "mother"));

        Map<String, SampleLevel> levels = levels(input);

        assertEquals(SampleLevel.STANDALONE, levels.get("organoid"));
    }

    @Test
    void anOrganoidIsDetachedSoItDoesNotMakeItsParentAnAliquotGroup() {
        // Left linked, the parents-of-leaves rule would read the organoid as an aliquot and promote
        // its parent to an aliquot group.
        List<Specimen> input = List.of(specimen("mother", null), organoid("organoid", "mother"));

        List<SpecimenNode> nodes = resolver.resolve(input);
        SpecimenNode mother = nodes.stream().filter(n -> n.getId().equals("mother")).findFirst().orElseThrow();
        SpecimenNode organoid = nodes.stream().filter(n -> n.getId().equals("organoid")).findFirst().orElseThrow();

        assertNull(organoid.getParent(), "the organoid is detached from the hierarchy");
        assertTrue(mother.getChildren().isEmpty(), "the mother has no children left");
        assertEquals(SampleLevel.STANDALONE, mother.getLevel());
    }

    @Test
    void anOrganoidIsFlaggedSoTheTargetCanDecide() {
        // Structurally it is a sample, so the resolver hands it on. Whether it can be represented
        // depends on the target: MIABIS has codes for cell lines and organoids, bbmri.de does not.
        List<Specimen> input = List.of(specimen("mother", null), organoid("organoid", "mother"));

        SpecimenNode node = resolver.resolve(input).stream()
                .filter(n -> n.getId().equals("organoid")).findFirst().orElseThrow();

        assertTrue(node.isCellLineOrOrganoid());
        assertTrue(node.isExportable());
        assertEquals(List.of("mother", "organoid"), exportedIds(input));
    }

    @Test
    void aDeclaredLevelDoesNotOverrideTheOrganoidRule() {
        Specimen organoid = organoid("organoid", "mother");
        organoid.addExtension(new Extension()
                .setUrl(MiiSpecimenHierarchyResolver.SAMPLE_LEVEL_EXTENSION_URL)
                .setValue(new Coding()
                        .setSystem(MiiSpecimenHierarchyResolver.SAMPLE_LEVEL_CODE_SYSTEM)
                        .setCode("ALIQUOTGRUPPE")));

        List<Specimen> input = List.of(specimen("mother", null), organoid);

        assertEquals(SampleLevel.STANDALONE, levels(input).get("organoid"));
    }

    // --- real fixtures --------------------------------------------------------------------------

    @Test
    void theRealMii2025SpecimenIsStandalone() {
        // 2025 has no sample level extension, so this goes through inference. It has no parent and
        // no children, which is the case a strict parents-of-leaves rule would export nothing for.
        Specimen specimen = mii2025Specimen();

        SpecimenNode node = resolver.resolve(List.of(specimen)).get(0);

        assertEquals("MusterprobeFluessig", node.getId());
        assertTrue(node.isRoot());
        assertTrue(node.isLeaf());
        assertEquals(SampleLevel.STANDALONE, node.getLevel());
        assertTrue(node.isExportable());
    }

    @Test
    void realParentReferencesAreResolvedIntoLinks() {
        List<SpecimenNode> nodes = resolver.resolve(mii2026Examples());

        SpecimenNode mother = node(nodes, "MusterprobeFluessig");
        SpecimenNode buffyCoatGroup = node(nodes, "AliquotgruppeBuffyCoat");

        assertNull(mother.getParent());
        assertSame(mother, buffyCoatGroup.getParent());
        assertEquals(List.of("AliquotgruppeBuffyCoat", "AliquotgruppePlasma"),
                mother.getChildren().stream().map(SpecimenNode::getId).sorted().toList());
        assertEquals(List.of("AliquotBuffyCoat1", "AliquotBuffyCoat2"),
                buffyCoatGroup.getChildren().stream().map(SpecimenNode::getId).sorted().toList());
    }

    @Test
    void aRealAliquotGroupCanHangOffAnAliquot() {
        // AliquotgruppeDNA is derived from AliquotBuffyCoat2, so the real data is not a clean
        // mother, group, aliquot tree. Depth-based classification would get this wrong.
        List<SpecimenNode> nodes = resolver.resolve(mii2026Examples());

        SpecimenNode dnaGroup = node(nodes, "AliquotgruppeDNA");

        assertEquals("AliquotBuffyCoat2", dnaGroup.getParent().getId());
        assertEquals(SampleLevel.ALIQUOT, dnaGroup.getParent().getLevel());
        assertEquals(SampleLevel.ALIQUOT_GROUP, dnaGroup.getLevel());
        assertTrue(dnaGroup.isLeaf(), "it has been declared a group without being aliquoted yet");
    }

    @Test
    void theRealOrganoidIsDetachedFromItsParent() {
        // OrganoidLunge names MusterprobeGewebe as its parent in the file.
        List<SpecimenNode> nodes = resolver.resolve(mii2026Examples());

        SpecimenNode organoid = node(nodes, "OrganoidLunge");
        SpecimenNode tissue = node(nodes, "MusterprobeGewebe");

        assertTrue(organoid.isCellLineOrOrganoid());
        assertNull(organoid.getParent());
        assertEquals(SampleLevel.STANDALONE, organoid.getLevel());
        assertTrue(tissue.getChildren().isEmpty(), "the organoid no longer counts as a child");
    }

    @Test
    void inferenceOverTheRealGraphPicksTheParentsOfLeaves() {
        // Same files with the level extensions removed, which is what 2025-shaped data looks like.
        Map<String, SampleLevel> levels = levels(withoutLevelExtensions(mii2026Examples()));

        assertEquals(SampleLevel.ALIQUOT_GROUP, levels.get("AliquotgruppeBuffyCoat"));
        assertEquals(SampleLevel.ALIQUOT_GROUP, levels.get("AliquotBuffyCoat2"));
        assertEquals(SampleLevel.ALIQUOT, levels.get("AliquotBuffyCoat1"));
        assertEquals(SampleLevel.ALIQUOT, levels.get("AliquotgruppeDNA"));
        assertEquals(SampleLevel.STANDALONE, levels.get("MusterprobeGewebe"));
    }

    @Test
    void inferenceAndTheDeclaredLevelsExportTheSameCountButNotTheSameSamples() {
        // A useful warning about 2025 data: the totals can agree while the actual samples differ.
        List<String> declared = exportedIds(mii2026Examples());
        List<String> inferred = exportedIds(withoutLevelExtensions(mii2026Examples()));

        assertEquals(declared.size(), inferred.size());
        assertFalse(declared.equals(inferred));
        assertTrue(declared.contains("AliquotgruppePlasma"));
        assertFalse(inferred.contains("AliquotgruppePlasma"));
    }

    @Test
    void summariseDescribesTheRealHierarchy() {
        String summary = MiiSpecimenHierarchyResolver.countByLevel(resolver.resolve(mii2026Examples()));

        assertTrue(summary.contains("ALIQUOT_GROUP"), summary);
        assertTrue(summary.contains("STANDALONE"), summary);
    }
}
