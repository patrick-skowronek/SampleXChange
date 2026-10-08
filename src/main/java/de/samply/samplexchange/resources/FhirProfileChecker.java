package de.samply.samplexchange.resources;

import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Condition;

import java.util.Objects;

public class FhirProfileChecker {

    private static final String SNOMED = "http://snomed.info/sct";
    private static final String LOINC = "http://loinc.org";
    private static final String SNOMED_CODE_FOR_CAUSE_OF_DEATH = "16100001";
    private static final String LOINC_CODE_FOR_CAUSE_OF_DEATH = "79378-6";
    private static final String MII_DIAGNOSIS_PROFILE =
            "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose";

    private FhirProfileChecker() {
    }

    public static boolean isMiiCauseOfDeath(Condition condition) {
        return hasCategory(condition, SNOMED, SNOMED_CODE_FOR_CAUSE_OF_DEATH)
                || hasCategory(condition, LOINC, LOINC_CODE_FOR_CAUSE_OF_DEATH);
    }

    public static boolean isMiiDiagnosis(Condition condition) {
        for (CanonicalType profile : condition.getMeta().getProfile()) {
            if (Objects.equals(profile.asStringValue(), MII_DIAGNOSIS_PROFILE)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCategory(Condition condition, String system, String code) {
        return Objects.equals(condition.getCategoryFirstRep().getCodingFirstRep().getSystem(), system)
                && Objects.equals(condition.getCategoryFirstRep().getCodingFirstRep().getCode(), code);
    }
}
