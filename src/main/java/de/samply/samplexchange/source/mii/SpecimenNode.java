package de.samply.samplexchange.source.mii;

import org.hl7.fhir.r4.model.Specimen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SpecimenNode {
    private final Specimen specimen;
    private final String id;
    private final List<SpecimenNode> children = new ArrayList<>();
    private SpecimenNode parent;
    private SampleLevel level;
    private boolean cellLineOrOrganoid;

    SpecimenNode(Specimen specimen, String id) {
        this.specimen = specimen;
        this.id = id;
    }

    public Specimen getSpecimen() {
        return specimen;
    }

    public String getId() {
        return id;
    }

    public SpecimenNode getParent() {
        return parent;
    }

    public List<SpecimenNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public SampleLevel getLevel() {
        return level;
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    public boolean isRoot() {
        return parent == null;
    }

    public boolean isCellLineOrOrganoid() {
        return cellLineOrOrganoid;
    }

    public boolean isExportable() {
        if (level == SampleLevel.STANDALONE) {
            return true;
        }
        return level == SampleLevel.ALIQUOT_GROUP && !isUsedUpAliquotGroup();
    }

    /**
     * The aliquots directly below that the biobank still holds. An aliquot no longer counts once
     * it is used up, or once a group has been derived from it, because that group is exported on
     * its own.
     */
    public List<SpecimenNode> getRemainingAliquots() {
        return children.stream().filter(child -> child.isLeaf() && !child.isUsedUp()).toList();
    }

    /**
     * An aliquot group that had aliquots and has none left. A group with no aliquots at all is
     * not used up: its aliquots may simply not have been recorded.
     */
    public boolean isUsedUpAliquotGroup() {
        return level == SampleLevel.ALIQUOT_GROUP && !children.isEmpty() && getRemainingAliquots().isEmpty();
    }

    private boolean isUsedUp() {
        Specimen.SpecimenStatus status = specimen.getStatus();
        return status == Specimen.SpecimenStatus.UNAVAILABLE || status == Specimen.SpecimenStatus.ENTEREDINERROR;
    }

    void setParent(SpecimenNode parent) {
        this.parent = parent;
    }

    void addChild(SpecimenNode child) {
        this.children.add(child);
    }

    void setCellLineOrOrganoid(boolean cellLineOrOrganoid) {
        this.cellLineOrOrganoid = cellLineOrOrganoid;
    }

    void setLevel(SampleLevel level) {
        this.level = level;
    }

    @Override
    public String toString() {
        return "SpecimenNode[%s, %s, children=%d]".formatted(id, level, children.size());
    }
}
