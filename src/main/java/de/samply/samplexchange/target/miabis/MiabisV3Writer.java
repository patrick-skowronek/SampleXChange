package de.samply.samplexchange.target.miabis;

import de.samply.samplexchange.configuration.TargetFormat;
import de.samply.samplexchange.domain.BiobankDirectory;
import de.samply.samplexchange.domain.CauseOfDeath;
import de.samply.samplexchange.domain.CodedValue;
import de.samply.samplexchange.domain.Diagnosis;
import de.samply.samplexchange.domain.Donor;
import de.samply.samplexchange.domain.DonorRecord;
import de.samply.samplexchange.domain.Sample;
import de.samply.samplexchange.domain.TemperatureRange;
import de.samply.samplexchange.target.TargetWriter;
import de.samply.samplexchange.terminology.Terminology;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGenderEnumFactory;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Specimen;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class MiabisV3Writer implements TargetWriter {
    public static final String SAMPLE_PROFILE = "https://fhir.bbmri-eric.eu/StructureDefinition/miabis-sample";
    public static final String DONOR_PROFILE = "https://fhir.bbmri-eric.eu/StructureDefinition/miabis-sample-donor";
    public static final String CONDITION_PROFILE = "https://fhir.bbmri-eric.eu/StructureDefinition/miabis-condition";

    public static final String DETAILED_SAMPLE_TYPE_SYSTEM =
            "https://fhir.bbmri-eric.eu/CodeSystem/miabis-detailed-samply-type-cs";
    public static final String STORAGE_TEMPERATURE_SYSTEM =
            "https://fhir.bbmri-eric.eu/CodeSystem/miabis-storage-temperature-cs";
    public static final String STORAGE_TEMPERATURE_EXTENSION =
            "https://fhir.bbmri-eric.eu/StructureDefinition/miabis-sample-storage-temperature-extension";

    public static final String DETAILED_SAMPLE_TYPE_SYSTEM_AS_MII_SPELLS_IT =
            "https://fhir.bbmri-eric.eu/fhir/CodeSystem/miabis-detailed-samply-type-cs";

    static final String SNOMED = "http://snomed.info/sct";

    static final String SAMPLE_TYPE_WHEN_UNMAPPED = "Other";

    private final Terminology terminology;

    public MiabisV3Writer(Terminology terminology) {
        this.terminology = terminology;
    }

    @Override
    public TargetFormat format() {
        return TargetFormat.MIABIS_V3;
    }

    /**
     * Not built yet. MIABIS models a collection as a Group plus a collection Organization, and a
     * biobank as its own Organization; see docs/TODO.md. Samples carry no collection reference
     * here, so nothing dangles meanwhile.
     */
    @Override
    public List<Resource> write(BiobankDirectory directory) {
        if (!directory.biobanks().isEmpty() || !directory.collections().isEmpty()) {
            log.info("Skipping {} biobank(s) and {} collection(s), the MIABIS writer does not write "
                    + "them yet", directory.biobanks().size(), directory.collections().size());
        }
        return List.of();
    }

    @Override
    public List<Resource> write(DonorRecord record) {
        List<Resource> resources = new ArrayList<>();
        resources.add(toDonor(record.donor()));
        record.samples().forEach(sample -> resources.add(toSample(sample)));
        record.diagnoses().forEach(diagnosis -> resources.add(toCondition(diagnosis)));
        record.causesOfDeath().forEach(cause -> resources.add(toCauseOfDeathCondition(cause)));
        return resources;
    }

    Patient toDonor(Donor donor) {
        Patient patient = new Patient();
        patient.setMeta(new Meta().addProfile(DONOR_PROFILE));
        patient.setId(donor.id());
        patient.addIdentifier(toIdentifier(donor.id()));
        patient.setGender(toGender(donor.gender()));
        if (donor.birthDate() != null) {
            patient.setBirthDate(donor.birthDate());
        }
        if (donor.deceased() && donor.deceasedDateTime() != null) {
            patient.setDeceased(donor.deceasedDateTime());
        }
        return patient;
    }

    Specimen toSample(Sample sample) {
        Specimen specimen = new Specimen();
        specimen.setMeta(new Meta().addProfile(SAMPLE_PROFILE));
        specimen.setId(sample.id());
        specimen.addIdentifier(toIdentifier(sample.id()));
        specimen.setType(toSampleType(sample.types()));

        if (sample.donorId() != null) {
            specimen.setSubject(new Reference("Patient/" + sample.donorId()));
        }
        if (sample.collectedAt() != null) {
            specimen.getCollection().setCollected(sample.collectedAt());
        }
        CodeableConcept bodySite = toBodySite(sample.bodySite());
        if (bodySite != null) {
            specimen.getCollection().setBodySite(bodySite);
        }
        Extension temperature = toStorageTemperatureExtension(sample.storageTemperature());
        if (temperature != null) {
            specimen.addProcessing(toProcessingStepCarrying(temperature));
        }
        return specimen;
    }

    Condition toCondition(Diagnosis diagnosis) {
        Condition condition = new Condition();
        condition.setMeta(new Meta().addProfile(CONDITION_PROFILE));
        condition.setId(diagnosis.id());
        if (diagnosis.donorId() != null) {
            condition.setSubject(new Reference("Patient/" + diagnosis.donorId()));
        }
        CodeableConcept code = toDiagnosisCode(diagnosis.code());
        if (code != null) {
            condition.setCode(code);
        }
        return condition;
    }

    Condition toCauseOfDeathCondition(CauseOfDeath cause) {
        Condition condition = new Condition();
        condition.setMeta(new Meta().addProfile(CONDITION_PROFILE));
        condition.setId(cause.id());
        if (cause.donorId() != null) {
            condition.setSubject(new Reference("Patient/" + cause.donorId()));
        }
        CodeableConcept code = toDiagnosisCode(cause.code());
        if (code != null) {
            condition.setCode(code);
        }
        return condition;
    }

    static Identifier toIdentifier(String value) {
        return new Identifier().setValue(value);
    }

    static Enumerations.AdministrativeGender toGender(String code) {
        if (code == null || code.isBlank()) {
            return Enumerations.AdministrativeGender.UNKNOWN;
        }
        return new AdministrativeGenderEnumFactory().fromCode(code);
    }

    CodeableConcept toSampleType(List<CodedValue> types) {
        CodedValue codeMiabisAlreadyUses = CodedValue.inSystem(types, DETAILED_SAMPLE_TYPE_SYSTEM);
        if (codeMiabisAlreadyUses == null) {
            codeMiabisAlreadyUses = CodedValue.inSystem(types, DETAILED_SAMPLE_TYPE_SYSTEM_AS_MII_SPELLS_IT);
        }
        String code = codeMiabisAlreadyUses != null
                ? codeMiabisAlreadyUses.code()
                : terminology.sampleType(TargetFormat.MIABIS_V3, CodedValue.inSystem(types, SNOMED))
                        .orElse(SAMPLE_TYPE_WHEN_UNMAPPED);
        CodeableConcept concept = new CodeableConcept();
        concept.getCodingFirstRep().setSystem(DETAILED_SAMPLE_TYPE_SYSTEM).setCode(code);
        return concept;
    }

    static CodeableConcept toBodySite(CodedValue site) {
        if (site == null) {
            return null;
        }
        CodeableConcept concept = new CodeableConcept();
        concept.getCodingFirstRep().setSystem(site.system()).setCode(site.code());
        return concept;
    }

    Extension toStorageTemperatureExtension(TemperatureRange range) {
        return terminology.storageTemperature(TargetFormat.MIABIS_V3, range)
                .map(code -> {
                    CodeableConcept concept = new CodeableConcept();
                    concept.getCodingFirstRep().setSystem(STORAGE_TEMPERATURE_SYSTEM).setCode(code);
                    return new Extension(STORAGE_TEMPERATURE_EXTENSION, concept);
                })
                .orElse(null);
    }

    static Specimen.SpecimenProcessingComponent toProcessingStepCarrying(Extension temperature) {
        Specimen.SpecimenProcessingComponent processing = new Specimen.SpecimenProcessingComponent();
        processing.addExtension(temperature);
        return processing;
    }

    static CodeableConcept toDiagnosisCode(CodedValue code) {
        if (code == null) {
            return null;
        }
        CodeableConcept concept = new CodeableConcept();
        concept.getCodingFirstRep()
                .setSystem(code.system())
                .setVersion(code.version())
                .setCode(code.code());
        return concept;
    }
}
