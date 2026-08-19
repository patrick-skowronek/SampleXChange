package de.samply.samplexchange.resources;

import de.samply.samplexchange.converters.SnomedSamplyTypeConverter;
import de.samply.samplexchange.converters.TemperatureConverter;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;

import java.util.*;

/**
 * Specimenmappings for converting between bbmri.de and MII KDS.
 */
@Slf4j
public class SpecimenMapping
        extends ConvertClass<Specimen, Specimen> {

    // Shared
    DateTimeType collectedDate;

    String fastingStatus;
    String fastingStatusSystem;

    // BBMRI data
    String bbmriBodySite;

    String storageTemperature;
    String diagnosisIcd10Who;
    String miiId = "";

    // MII data
    String miiSubject = "";
    // Decoded as snomed-ct
    String miiSampleType;
    String miiBodySiteIcd;
    String miiBodySiteSnomedCt;
    Long miiStoargeTemperatureHigh;
    Long miiStoargeTemperaturelow;
    boolean hasParent;
    @Setter
    @Getter
    private String diagnosisIcd10Gm;
    @Setter
    private String miiConditionRef;

    @Override
    public void fromMii(Specimen resource) {

        this.hasParent = resource.hasParent();

        this.miiId = resource.getId();
        this.miiSubject = resource.getSubject().getReference();

        this.miiSampleType = resource.getType().getCodingFirstRep().getCode();
        this.collectedDate = resource.getCollection().getCollectedDateTimeType();

        if (Objects.equals(
                resource.getCollection().getBodySite().getCodingFirstRep().getSystem(),
                "http://snomed.info/sct")) {
            this.miiBodySiteSnomedCt =
                    resource.getCollection().getBodySite().getCodingFirstRep().getCode();
        } else if (Objects.equals(
                resource.getCollection().getBodySite().getCodingFirstRep().getSystem(),
                "http://terminology.hl7.org/CodeSystem/icd-o-3")) {
            this.miiBodySiteIcd = resource.getCollection().getBodySite().getCodingFirstRep().getCode();
        }

        this.fastingStatus =
                resource.getCollection().getFastingStatusCodeableConcept().getCodingFirstRep().getCode();
        this.fastingStatusSystem =
                resource.getCollection().getFastingStatusCodeableConcept().getCodingFirstRep().getSystem();

        for (Extension e : resource.getExtension()) {
            if (Objects.equals(
                    e.getUrl(),
                    "https://simplifier.net/medizininformatikinitiative-modulbiobank/files/fsh-generated/resources/structuredefinition-diagnose.json")) {
                this.setMiiConditionRef(e.getValue().toString());
            }
        }

        Calendar calendar = Calendar.getInstance();
        calendar.set(1900, Calendar.JANUARY, 1); // Month is zero-based
        Date latestStorageDate = calendar.getTime();
        Range latestStorageRange = new Range();

        // Storage temperature is an extension of the processing
        for (Specimen.SpecimenProcessingComponent processingComponent : resource.getProcessing()) {
            if (processingComponent.getProcedure().hasCoding("http://snomed.info/sct", "1186936003")) {
                for (Extension extension : processingComponent.getExtension()) {
                    if (Objects.equals(extension.getUrl(),
                            "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/Temperaturbedingungen")) {
                        if (Objects.nonNull(processingComponent.getTimePeriod())
                                && Objects.nonNull(processingComponent.getTimePeriod().getStart())
                                && processingComponent.getTimePeriod().getStart().after(latestStorageDate)) {
                            latestStorageRange = (Range) processingComponent.getExtensionFirstRep().getValue();

                        }
                    }
                }
            }
        }

        if(Objects.nonNull(latestStorageRange)) {
            if (Objects.nonNull(latestStorageRange.getHigh()) && Objects.nonNull(latestStorageRange.getHigh().getValue())) {
                this.miiStoargeTemperatureHigh = latestStorageRange.getHigh().getValue().longValue();
            }
            if (Objects.nonNull(latestStorageRange.getLow()) && Objects.nonNull(latestStorageRange.getLow().getValue())) {
                this.miiStoargeTemperaturelow = latestStorageRange.getLow().getValue().longValue();
            }
        }
    }

    @Override
    public Specimen toBbmri() {
        if (this.hasParent) {
            log.debug("Specimen {} has parent, exporting anyway", miiId);
        }

        Specimen specimen = new Specimen();
        specimen.setMeta(new Meta().addProfile("https://fhir.bbmri.de/StructureDefinition/Specimen"));

        specimen.setId(miiId);

        specimen.getSubject().setReference(miiSubject);

        if(Objects.nonNull(this.miiSampleType)) {
            CodeableConcept coding = new CodeableConcept();
            coding
                    .getCodingFirstRep()
                    .setCode(SnomedSamplyTypeConverter.fromMiiToBbmri(this.miiSampleType))
                    .setSystem("https://fhir.bbmri.de/CodeSystem/SampleMaterialType");
            specimen.setType(coding);
        }  else {
            log.warn("Sample {} has no sample type", miiId);
        }

        if (Objects.nonNull(this.collectedDate)) {
            specimen.getCollection().setCollected(this.collectedDate);
        }

        if (Objects.nonNull(miiBodySiteIcd)) {
            this.bbmriBodySite = miiBodySiteIcd;
            CodeableConcept bodySiteCode = new CodeableConcept();
            bodySiteCode
                    .getCodingFirstRep()
                    .setCode(this.bbmriBodySite)
                    .setSystem("urn:oid:1.3.6.1.4.1.19376.1.3.11.36");
            specimen.getCollection().setBodySite(bodySiteCode);
        }

        if (Objects.nonNull(storageTemperature)) {
            Extension extension = new Extension();
            extension.setUrl("https://fhir.bbmri.de/StructureDefinition/StorageTemperature");
            extension.setValue(new CodeableConcept().getCodingFirstRep().setCode(storageTemperature));
        }

        if (Objects.nonNull(this.miiStoargeTemperatureHigh)
                && Objects.nonNull(this.miiStoargeTemperaturelow)) {
            specimen.addExtension(
                    TemperatureConverter.fromMiiToBbmri(
                            this.miiStoargeTemperatureHigh, this.miiStoargeTemperaturelow));
        }

        if (Objects.nonNull(this.getDiagnosisIcd10Gm()) || Objects.nonNull(this.diagnosisIcd10Who)) {
            Extension extension = new Extension();
            extension.setUrl("https://fhir.bbmri.de/StructureDefinition/SampleDiagnosis");
            List<Coding> diagnosis = new ArrayList<>();
            diagnosis.add(
                    new Coding()
                            .setSystem("http://fhir.de/CodeSystem/bfarm/icd-10-gm")
                            .setCode(this.getDiagnosisIcd10Gm()));
            diagnosis.add(
                    new Coding()
                            .setSystem("http://hl7.org/fhir/sid/icd-10")
                            .setCode(this.getDiagnosisIcd10Gm()));
            CodeableConcept codeableConcept = new CodeableConcept();
            extension.setValue(codeableConcept.setCoding(diagnosis));
        }

        return specimen;
    }

}
