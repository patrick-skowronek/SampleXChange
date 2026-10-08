package de.samply.samplexchange.domain;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;

import java.util.List;
import java.util.Objects;

public record CodedValue(String system, String code, String version) {
    /** The canonical MII 2025 uses, the HL7 OID, and the OID bbmri.de fixes for topography. */
    private static final List<String> ICD_O_3_SYSTEMS = List.of(
            "http://terminology.hl7.org/CodeSystem/icd-o-3",
            "urn:oid:2.16.840.1.113883.6.43.1",
            "urn:oid:1.3.6.1.4.1.19376.1.3.11.36");

    public CodedValue(String system, String code) {
        this(system, code, null);
    }

    public static CodedValue firstOf(CodeableConcept concept) {
        if (concept == null || concept.getCoding().isEmpty()) {
            return null;
        }
        return of(concept.getCodingFirstRep());
    }

    public static List<CodedValue> allOf(CodeableConcept concept) {
        if (concept == null) {
            return List.of();
        }
        return concept.getCoding().stream().map(CodedValue::of).filter(Objects::nonNull).toList();
    }

    public static CodedValue inSystem(List<CodedValue> codings, String system) {
        return codings.stream().filter(c -> c.hasSystem(system)).findFirst().orElse(null);
    }

    public static CodedValue of(Coding coding) {
        if (coding == null || coding.getCode() == null || coding.getCode().isBlank()) {
            return null;
        }
        return new CodedValue(coding.getSystem(), coding.getCode(), coding.getVersion());
    }

    public boolean hasSystem(String expected) {
        return expected.equals(system);
    }

    public boolean isIcdO3() {
        return system != null && ICD_O_3_SYSTEMS.contains(system);
    }
}
