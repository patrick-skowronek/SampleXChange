package de.samply.samplexchange.source.mii;

import org.hl7.fhir.r4.model.Specimen;

import java.util.List;

public interface SpecimenHierarchyResolver {
    List<SpecimenNode> resolve(List<Specimen> donorSpecimens);

    default List<SpecimenNode> resolveExportable(List<Specimen> donorSpecimens) {
        return resolve(donorSpecimens).stream().filter(SpecimenNode::isExportable).toList();
    }
}
