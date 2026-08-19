package de.samply.samplexchange.resources;

import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.samply.samplexchange.utils.JsonUtils.compareFhirObjects;

/**
 * Ported from TransFAIR (resources/ConditionMappingTest), MII to bbmri.de direction only.
 */
class ConditionMappingTest {

    private static final String ICD_10_GM = "http://fhir.de/CodeSystem/bfarm/icd-10-gm";

    @Test
    void convertIcd10GmFromMiiToBbmri() {
        DateTimeType onset = new DateTimeType("2018-06-07T15:54:00+01:00");

        Condition sourceMii = new Condition();
        sourceMii.setId("conditionId");
        sourceMii.getMeta().setProfile(List.of(new CanonicalType(
                "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose")));
        sourceMii.setSubject(new Reference().setReference("Patient/patientId"));
        sourceMii.getCode().getCodingFirstRep()
                .setSystem(ICD_10_GM).setVersion("2022").setCode("C61");
        sourceMii.setOnset(onset);

        Condition expectedBbmri = new Condition();
        expectedBbmri.setId("conditionId");
        expectedBbmri.getMeta().setProfile(
                List.of(new CanonicalType("https://fhir.bbmri.de/StructureDefinition/Condition")));
        expectedBbmri.setSubject(new Reference().setReference("Patient/patientId"));
        expectedBbmri.setOnset(onset);
        expectedBbmri.getCode().getCodingFirstRep()
                .setSystem(ICD_10_GM).setVersion("2022").setCode("C61");

        ConditionMapping mapping = new ConditionMapping();
        mapping.fromMii(sourceMii);

        compareFhirObjects(mapping.toBbmri(), expectedBbmri);
    }

    @Test
    void snomedOnlyDiagnosisProducesNoCode() {
        // KNOWN GAP: fromMii logs "Snomed-CT diagnosis mapping not supported" and drops the code,
        // so the bbmri.de Condition comes out without any coding. See
        // docs/adr/0001-mapping-architecture.md. TransFAIR had this case commented out too.
        Condition sourceMii = new Condition();
        sourceMii.setId("conditionId");
        sourceMii.setSubject(new Reference().setReference("Patient/patientId"));
        sourceMii.getCode().getCodingFirstRep()
                .setSystem("http://snomed.info/sct").setCode("399068003");

        Condition expectedBbmri = new Condition();
        expectedBbmri.setId("conditionId");
        expectedBbmri.getMeta().setProfile(
                List.of(new CanonicalType("https://fhir.bbmri.de/StructureDefinition/Condition")));
        expectedBbmri.setSubject(new Reference().setReference("Patient/patientId"));

        ConditionMapping mapping = new ConditionMapping();
        mapping.fromMii(sourceMii);

        compareFhirObjects(mapping.toBbmri(), expectedBbmri);
    }
}
