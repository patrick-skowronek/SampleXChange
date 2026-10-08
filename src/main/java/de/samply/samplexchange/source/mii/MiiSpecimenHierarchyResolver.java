package de.samply.samplexchange.source.mii;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Specimen;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MiiSpecimenHierarchyResolver implements SpecimenHierarchyResolver {

    public static final String SAMPLE_LEVEL_EXTENSION_URL =
            "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/mii-ex-biobank-ebene";

    public static final String SAMPLE_LEVEL_CODE_SYSTEM =
            "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/CodeSystem/mii-cs-biobank-probenebene";

    public static final String CELL_LINE_AND_ORGANOID_PROFILE =
            "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/mii-pr-biobank-zellinie-organoid";

    private static final String CODE_FOR_PRIMARY_SAMPLE = "PRIMÄRPROBE";
    private static final String CODE_FOR_ALIQUOT_GROUP = "ALIQUOTGRUPPE";
    private static final String CODE_FOR_ALIQUOT = "ALIQUOT";

    @Override
    public List<SpecimenNode> resolve(List<Specimen> specimensOfOneDonor) {
        Map<String, SpecimenNode> specimensById = toNodesById(specimensOfOneDonor);
        linkEachSpecimenToItsParent(specimensById);
        specimensById.values().forEach(this::setSampleLevel);
        return List.copyOf(specimensById.values());
    }

    private Map<String, SpecimenNode> toNodesById(List<Specimen> specimens) {
        Map<String, SpecimenNode> specimensById = new LinkedHashMap<>();
        for (Specimen specimen : specimens) {
            String id = specimen.getIdElement().getIdPart();
            if (id == null || id.isBlank()) {
                log.warn("Skipping a specimen because it has no id");
                continue;
            }
            SpecimenNode node = new SpecimenNode(specimen, id);
            node.setCellLineOrOrganoid(isCellLineOrOrganoid(specimen));
            if (specimensById.putIfAbsent(id, node) != null) {
                log.warn("Two specimens share the id {}, keeping the first", id);
            }
        }
        return specimensById;
    }

    private void linkEachSpecimenToItsParent(Map<String, SpecimenNode> specimensById) {
        for (SpecimenNode node : specimensById.values()) {
            if (node.isCellLineOrOrganoid()) {
                log.debug("Leaving cell line or organoid {} out of the hierarchy", node.getId());
                continue;
            }
            linkToFirstResolvableParent(node, specimensById);
        }
    }

    private void linkToFirstResolvableParent(
            SpecimenNode node, Map<String, SpecimenNode> specimensById) {

        List<Reference> parentReferences = node.getSpecimen().getParent();
        if (parentReferences.size() > 1) {
            log.warn("Specimen {} names {} parents, MII expects one, using the first that resolves",
                    node.getId(), parentReferences.size());
        }
        for (Reference parentReference : parentReferences) {
            String parentId = readIdPart(parentReference);
            if (parentId == null) {
                continue;
            }
            if (parentId.equals(node.getId())) {
                log.warn("Specimen {} names itself as its parent, ignoring that", node.getId());
                continue;
            }
            SpecimenNode parent = specimensById.get(parentId);
            if (parent == null) {
                log.debug("Parent {} of specimen {} was not fetched, treating {} as a root",
                        parentId, node.getId(), node.getId());
                continue;
            }
            node.setParent(parent);
            parent.addChild(node);
            return;
        }
    }

    private static String readIdPart(Reference reference) {
        if (reference == null || !reference.hasReference()) {
            return null;
        }
        String idPart = new IdType(reference.getReference()).getIdPart();
        return (idPart == null || idPart.isBlank()) ? null : idPart;
    }

    private void setSampleLevel(SpecimenNode node) {
        if (node.isCellLineOrOrganoid()) {
            node.setLevel(SampleLevel.STANDALONE);
            return;
        }

        SampleLevel declaredLevel = readDeclaredSampleLevel(node.getSpecimen());
        if (declaredLevel != null) {
            node.setLevel(declaredLevelOf(node, declaredLevel));
            return;
        }
        node.setLevel(inferLevelFromPositionInTree(node));
    }

    private static SampleLevel declaredLevelOf(SpecimenNode node, SampleLevel declaredLevel) {
        boolean isPrimarySampleThatWasNeverAliquoted =
                declaredLevel == SampleLevel.MOTHER && node.isLeaf();
        return isPrimarySampleThatWasNeverAliquoted ? SampleLevel.STANDALONE : declaredLevel;
    }

    private SampleLevel inferLevelFromPositionInTree(SpecimenNode node) {
        if (hasAtLeastOneAliquotBelowIt(node)) {
            warnIfSomeChildrenAreNotAliquots(node);
            return SampleLevel.ALIQUOT_GROUP;
        }
        if (node.isLeaf()) {
            return node.isRoot() ? SampleLevel.STANDALONE : SampleLevel.ALIQUOT;
        }
        return node.isRoot() ? SampleLevel.MOTHER : SampleLevel.INTERMEDIATE;
    }

    private static boolean hasAtLeastOneAliquotBelowIt(SpecimenNode node) {
        return node.getChildren().stream().anyMatch(SpecimenNode::isLeaf);
    }

    private void warnIfSomeChildrenAreNotAliquots(SpecimenNode node) {
        if (!node.getChildren().stream().allMatch(SpecimenNode::isLeaf)) {
            log.warn("Specimen {} has children that are aliquots and children that are not, "
                    + "treating it as an aliquot group", node.getId());
        }
    }

    protected SampleLevel readDeclaredSampleLevel(Specimen specimen) {
        Extension sampleLevel = specimen.getExtensionByUrl(SAMPLE_LEVEL_EXTENSION_URL);
        if (sampleLevel == null || sampleLevel.getValue() == null) {
            return null;
        }
        if (!(sampleLevel.getValue() instanceof Coding coding)) {
            log.warn("Sample level of specimen {} is a {}, expected a Coding",
                    specimen.getIdElement().getIdPart(), sampleLevel.getValue().fhirType());
            return null;
        }
        return toSampleLevel(coding, specimen.getIdElement().getIdPart());
    }

    private SampleLevel toSampleLevel(Coding coding, String specimenId) {
        String code = coding.getCode();
        if (code == null || code.isBlank()) {
            return null;
        }
        if (coding.hasSystem() && !SAMPLE_LEVEL_CODE_SYSTEM.equals(coding.getSystem())) {
            log.warn("Specimen {} states sample level {} using the unexpected code system {}",
                    specimenId, code, coding.getSystem());
        }
        return switch (code) {
            case CODE_FOR_PRIMARY_SAMPLE -> SampleLevel.MOTHER;
            case CODE_FOR_ALIQUOT_GROUP -> SampleLevel.ALIQUOT_GROUP;
            case CODE_FOR_ALIQUOT -> SampleLevel.ALIQUOT;
            default -> {
                log.warn("Specimen {} states the unknown sample level {}, working it out from the "
                        + "hierarchy instead", specimenId, code);
                yield null;
            }
        };
    }

    public static boolean isCellLineOrOrganoid(Specimen specimen) {
        for (CanonicalType profile : specimen.getMeta().getProfile()) {
            if (CELL_LINE_AND_ORGANOID_PROFILE.equals(profile.asStringValue())) {
                return true;
            }
        }
        return false;
    }

    public static String countByLevel(List<SpecimenNode> nodes) {
        List<String> counts = new ArrayList<>();
        for (SampleLevel level : SampleLevel.values()) {
            long count = nodes.stream().filter(node -> node.getLevel() == level).count();
            if (count > 0) {
                counts.add(count + " " + level);
            }
        }
        return String.join(", ", counts);
    }
}
