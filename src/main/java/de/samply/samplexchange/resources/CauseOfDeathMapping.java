package de.samply.samplexchange.resources;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;

import java.util.List;

/**
 * Cause of Death mappings for converting between bbmri.de and MII KDS.
 */
@Slf4j
public class CauseOfDeathMapping extends ConvertClass<Observation, Condition> {

    public static final String ICD_SYSTEM = "http://hl7.org/fhir/sid/icd-10";

    public static final String BBMRI_PROFILE_CAUSE_OF_DEATH = "https://fhir.bbmri.de/StructureDefinition/CauseOfDeath";

    public static final String MII_PROFILE_CAUSE_OF_DEATH =
            "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Todesursache";

    String causeOfDeath = "";
    String miiId = "";
    String miiPatientId = "";

    // ICD-10
    String bbmriId = "";
    String bbmriPatientId = "";

    /**
     * This imports a Cause of Death to bbmri.de.
     */
    @Override
    public void fromBbmri(Observation resource) {
        if (resource.getMeta().getProfile().stream()
                .anyMatch(canonicalType -> canonicalType.equals(BBMRI_PROFILE_CAUSE_OF_DEATH))) {
            this.bbmriId = resource.getId();
            if (resource.getValueCodeableConcept().getCodingFirstRep().getSystem().equals(ICD_SYSTEM)) {
                this.causeOfDeath = resource.getValueCodeableConcept().getCodingFirstRep().getCode();
            }
            this.bbmriPatientId = resource.getSubject().getReference();
        }
    }

    /**
     * This imports a Cause of Death to MII KDS.
     */
    @Override
    public void fromMii(Condition resource) {
        if (resource.getMeta().getProfile().stream()
                .anyMatch(canonicalType -> canonicalType.equals(MII_PROFILE_CAUSE_OF_DEATH))) {
            this.miiId = resource.getId();
            if (resource.getCode().getCodingFirstRep().getSystem().equals(ICD_SYSTEM)) {
                this.causeOfDeath = resource.getCode().getCodingFirstRep().getCode();
            }
            this.miiPatientId = resource.getSubject().getReference();
        }
    }

    /**
     * This exports a Cause of Death to bbmri.de.
     */
    public org.hl7.fhir.r4.model.Observation toBbmri() {
        org.hl7.fhir.r4.model.Observation observation = new org.hl7.fhir.r4.model.Observation();
        observation.setMeta(
                new Meta().addProfile(BBMRI_PROFILE_CAUSE_OF_DEATH));

        Coding codingFirstRep = observation.getCode().getCodingFirstRep();
        codingFirstRep.setCode("68343-3");
        codingFirstRep.setSystem("http://loinc.org");

        if (bbmriId.isEmpty() && !miiId.isEmpty()) {
            this.bbmriId = miiId;
        }

        if (!miiPatientId.isEmpty() && bbmriPatientId.isEmpty()) {
            this.bbmriPatientId = miiPatientId;
        }

        if (bbmriId.isBlank()) {
            log.warn("Cannot create CauseOfDeath Observation without ID");
            return null;
        }

        observation.setId(bbmriId);
        observation.setSubject(new Reference().setReference(bbmriPatientId));
        
        if (!causeOfDeath.isBlank()) {
            CodeableConcept codeableConcept = new CodeableConcept();
            codeableConcept.getCodingFirstRep().setSystem(ICD_SYSTEM);
            codeableConcept.getCodingFirstRep().setCode(causeOfDeath);
            observation.setValue(codeableConcept);
        } else {
            log.warn("CauseOfDeath Observation {} has no ICD-10 code", bbmriId);
        }

        return observation;
    }

    /**
     * This exports a Cause of Death to MII KDS.
     */

    public org.hl7.fhir.r4.model.Condition toMii() {
        if (!bbmriId.isEmpty() && miiId.isEmpty()) {
            this.miiId = this.bbmriId;
        }
        
        if (miiId.isBlank()) {
            log.warn("Cannot create CauseOfDeath Condition without ID");
            return null;
        }

        org.hl7.fhir.r4.model.Condition condition = new org.hl7.fhir.r4.model.Condition();
        condition.setMeta(
                new Meta()
                        .addProfile(MII_PROFILE_CAUSE_OF_DEATH));

        CodeableConcept codingLoinc = new CodeableConcept();
        codingLoinc.getCodingFirstRep().setSystem("http://loinc.org");
        codingLoinc.getCodingFirstRep().setCode("79378-6");

        condition.setId(miiId);

        if (miiPatientId.isEmpty() && !bbmriPatientId.isEmpty()) {
            this.miiPatientId = bbmriPatientId;
        }

        condition.setSubject(new Reference(miiPatientId));

        CodeableConcept codingSnomedCt = new CodeableConcept();
        codingSnomedCt.getCodingFirstRep().setSystem("http://snomed.info/sct");
        codingSnomedCt.getCodingFirstRep().setCode("16100001");

        condition.setCategory(List.of(codingLoinc, codingSnomedCt));

        if (!causeOfDeath.isBlank()) {
            CodeableConcept codeableConceptCause = new CodeableConcept();
            codeableConceptCause.getCodingFirstRep().setSystem(ICD_SYSTEM);
            codeableConceptCause.getCodingFirstRep().setCode(causeOfDeath);
            condition.setCode(codeableConceptCause);
        } else {
            log.warn("CauseOfDeath Condition {} has no ICD-10 code", miiId);
        }

        return condition;
    }
}
