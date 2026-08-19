package de.samply.samplexchange.resources;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGenderEnumFactory;
import org.hl7.fhir.r4.model.Meta;

import java.util.Date;
import java.util.Objects;

/**
 * Patientmappings for converting MII KDS to bbmri.de.
 */
public class PatientMapping
        extends ConvertClass<org.hl7.fhir.r4.model.Patient, org.hl7.fhir.r4.model.Patient> {

    // MII data
    String miiId = "";

    // BBMRI data
    String bbmriId = "";

    Date brithDate;
    boolean patientDeceased;
    DateTimeType patientDeceasedDateTime;
    String gender;

    public PatientMapping() {
    }

    @Override
    public void fromMii(org.hl7.fhir.r4.model.Patient resource) {
        this.miiId = resource.getId();
        this.brithDate = resource.getBirthDate();

        if (resource.hasGender()) {
            this.gender = resource.getGender().toCode();
        }

        if (resource.getDeceasedBooleanType().equals(new BooleanType(true))) {
            this.patientDeceased = true;
            this.patientDeceasedDateTime = resource.getDeceasedDateTimeType();
        } else {
            this.patientDeceased = false;
        }
    }

    @Override
    public org.hl7.fhir.r4.model.Patient toBbmri() throws Exception {
        org.hl7.fhir.r4.model.Patient patient = new org.hl7.fhir.r4.model.Patient();
        patient.setMeta(
                new Meta().addProfile("https://fhir.simplifier.net/bbmri.de/StructureDefinition/Patient"));

        if (Objects.nonNull(this.gender)) {
            patient.setGender(new AdministrativeGenderEnumFactory().fromCode(this.gender));
        }

        if (Objects.nonNull(this.brithDate)) {
            patient.setBirthDate(brithDate);
        }

        if (bbmriId.isEmpty() && !miiId.isEmpty()) {
            this.bbmriId = this.miiId;
        }

        patient.setId(bbmriId);

        if (this.patientDeceased) {
            patient.setDeceased(this.patientDeceasedDateTime);
        }

        return patient;
    }
}
