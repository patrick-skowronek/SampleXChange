package de.samply.samplexchange.resources;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Reference;

import java.util.Objects;

/**
 * Organizationmappings for converting between bbmri.de and MII KDS.
 */
@Slf4j
public class ConditionMapping
        extends ConvertClass<org.hl7.fhir.r4.model.Condition, org.hl7.fhir.r4.model.Condition> {

    private static final String ICD_10_GM_CODE_SYSTEM = "http://fhir.de/CodeSystem/bfarm/icd-10-gm";
    String bbmriId = "";
    String bbmriSubject;
    DateTimeType onset;
    String diagnosisIcd10Who;
    String diagnosisIcd10Gm;
    String diagnosisIcd10GmVersion;
    String miiId = "";
    String miiSubject;

    public ConditionMapping() {
    }

    @Override
    public void fromMii(org.hl7.fhir.r4.model.Condition resource) {
        this.miiId = resource.getId();

        for (Coding coding : resource.getCode().getCoding()) {
            if (Objects.equals(coding.getSystem(), ICD_10_GM_CODE_SYSTEM)) {
                this.diagnosisIcd10Gm = coding.getCode();
                this.diagnosisIcd10GmVersion = coding.getVersion();
                break;
            }

            if (Objects.equals(coding.getSystem(), "http://snomed.info/sct")) {
                log.info("Snomed-CT diagnosis mapping not supported");
                continue;
            }
            log.info("Diagnosis found which is not supported");
        }

        this.miiSubject = resource.getSubject().getReference();


        this.onset = resource.getOnsetDateTimeType();
    }

    @Override
    public org.hl7.fhir.r4.model.Condition toBbmri() {
        org.hl7.fhir.r4.model.Condition condition = new org.hl7.fhir.r4.model.Condition();
        condition.setMeta(new Meta().addProfile("https://fhir.bbmri.de/StructureDefinition/Condition"));

        this.bbmriId = miiId;

        this.bbmriSubject = this.miiSubject;

        condition.setId(bbmriId);

        condition.setSubject(new Reference(bbmriSubject));

        condition.setOnset(this.onset);

        if (Objects.nonNull(this.diagnosisIcd10Gm)) {
            condition
                    .getCode()
                    .getCodingFirstRep()
                    .setSystem(ICD_10_GM_CODE_SYSTEM)
                    .setVersion(this.diagnosisIcd10GmVersion)
                    .setCode(this.diagnosisIcd10Gm);
        } else if (Objects.nonNull(this.diagnosisIcd10Who)) {
            condition
                    .getCode()
                    .getCodingFirstRep()
                    .setSystem("http://hl7.org/fhir/sid/icd-10")
                    .setCode(this.diagnosisIcd10Who);
        }

        return condition;
    }

}
