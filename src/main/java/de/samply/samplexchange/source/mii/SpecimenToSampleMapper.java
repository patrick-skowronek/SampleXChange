package de.samply.samplexchange.source.mii;

import de.samply.samplexchange.domain.CodedValue;
import de.samply.samplexchange.domain.Sample;
import de.samply.samplexchange.domain.TemperatureRange;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Range;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Specimen;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@Slf4j
@Component
public class SpecimenToSampleMapper {

    private static final String SNOMED = "http://snomed.info/sct";
    private static final String STORAGE_PROCEDURE_CODE = "1186936003";
    private static final String MII_TEMPERATURE_EXTENSION_URL =
            "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/Temperaturbedingungen";
    static final String MANAGING_ORGANIZATION_EXTENSION_URL =
            "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/VerwaltendeOrganisation";
    private static final int MAX_ANCESTORS_TO_SEARCH = 32;

    public List<Sample> toSamples(List<SpecimenNode> specimens) {
        return specimens.stream().map(this::toSample).toList();
    }

    public Sample toSample(SpecimenNode specimen) {
        return new Sample(
                specimen.getId(),
                findInSpecimenOrItsAncestors(specimen, SpecimenToSampleMapper::readDonorId),
                CodedValue.allOf(specimen.getSpecimen().getType()),
                findInSpecimenOrItsAncestors(specimen, SpecimenToSampleMapper::readCollectionDate),
                findInSpecimenOrItsAncestors(specimen, SpecimenToSampleMapper::readBodySite),
                findInSpecimenOrItsAncestors(specimen, SpecimenToSampleMapper::readFastingStatus),
                findNewestStorageTemperatureInSpecimenOrItsAliquots(specimen),
                specimen.isCellLineOrOrganoid(),
                findInSpecimenOrItsAncestors(specimen, SpecimenToSampleMapper::readManagingOrganization));
    }

    private static <T> T findInSpecimenOrItsAncestors(
            SpecimenNode specimen, Function<Specimen, T> readValue) {

        Set<String> alreadySearched = new HashSet<>();
        SpecimenNode current = specimen;

        for (int searched = 0; current != null && searched < MAX_ANCESTORS_TO_SEARCH; searched++) {
            if (!alreadySearched.add(current.getId())) {
                log.warn("Specimen {} is its own ancestor, stopping the search", current.getId());
                return null;
            }
            T value = readValue.apply(current.getSpecimen());
            if (value != null) {
                return value;
            }
            current = current.getParent();
        }
        return null;
    }

    private static String readDonorId(Specimen specimen) {
        if (!specimen.hasSubject() || !specimen.getSubject().hasReference()) {
            return null;
        }
        String donorId = new IdType(specimen.getSubject().getReference()).getIdPart();
        return isBlank(donorId) ? null : donorId;
    }

    private static DateTimeType readCollectionDate(Specimen specimen) {
        if (!specimen.hasCollection() || !specimen.getCollection().hasCollectedDateTimeType()) {
            return null;
        }
        DateTimeType collectionDate = specimen.getCollection().getCollectedDateTimeType();
        return collectionDate.hasValue() ? collectionDate : null;
    }

    private static CodedValue readBodySite(Specimen specimen) {
        if (!specimen.hasCollection() || !specimen.getCollection().hasBodySite()) {
            return null;
        }
        // MII 2025 allows a SNOMED and an ICD-O-3 coding side by side. bbmri.de can only take the
        // ICD-O-3 one, so it wins wherever it sits in the list.
        List<CodedValue> codings = CodedValue.allOf(specimen.getCollection().getBodySite());
        return codings.stream().filter(CodedValue::isIcdO3).findFirst()
                .orElse(codings.isEmpty() ? null : codings.get(0));
    }

    private static String readManagingOrganization(Specimen specimen) {
        Extension managedBy = specimen.getExtensionByUrl(MANAGING_ORGANIZATION_EXTENSION_URL);
        if (managedBy == null || !(managedBy.getValue() instanceof Reference reference)
                || !reference.hasReference()) {
            return null;
        }
        String organizationId = new IdType(reference.getReference()).getIdPart();
        return isBlank(organizationId) ? null : organizationId;
    }

    private static CodedValue readFastingStatus(Specimen specimen) {
        if (!specimen.hasCollection()
                || !specimen.getCollection().hasFastingStatusCodeableConcept()) {
            return null;
        }
        return CodedValue.firstOf(specimen.getCollection().getFastingStatusCodeableConcept());
    }

    private static TemperatureRange findNewestStorageTemperatureInSpecimenOrItsAliquots(
            SpecimenNode specimen) {

        List<StorageStep> storageSteps = new ArrayList<>(readStorageSteps(specimen.getSpecimen()));
        for (SpecimenNode aliquot : specimen.getRemainingAliquots()) {
            storageSteps.addAll(readStorageSteps(aliquot.getSpecimen()));
        }
        return storageSteps.stream()
                .max(Comparator.comparing(
                        StorageStep::startedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(StorageStep::temperature)
                .orElse(null);
    }

    private static List<StorageStep> readStorageSteps(Specimen specimen) {
        List<StorageStep> storageSteps = new ArrayList<>();
        for (Specimen.SpecimenProcessingComponent step : specimen.getProcessing()) {
            if (!isStorageStep(step)) {
                continue;
            }
            TemperatureRange temperature = readTemperature(step);
            if (temperature != null) {
                storageSteps.add(new StorageStep(readStartTime(step), temperature));
            }
        }
        return storageSteps;
    }

    private static boolean isStorageStep(Specimen.SpecimenProcessingComponent step) {
        return step.getProcedure().hasCoding(SNOMED, STORAGE_PROCEDURE_CODE);
    }

    private static TemperatureRange readTemperature(Specimen.SpecimenProcessingComponent step) {
        Extension temperature = step.getExtensionByUrl(MII_TEMPERATURE_EXTENSION_URL);
        if (temperature == null || !(temperature.getValue() instanceof Range range)) {
            return null;
        }
        return new TemperatureRange(
                toLong(range.hasLow() ? range.getLow().getValue() : null),
                toLong(range.hasHigh() ? range.getHigh().getValue() : null));
    }

    private static Date readStartTime(Specimen.SpecimenProcessingComponent step) {
        if (step.hasTime() && step.getTime() instanceof Period period && period.hasStart()) {
            return period.getStart();
        }
        return null;
    }

    private static Long toLong(BigDecimal value) {
        return value == null ? null : value.longValue();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record StorageStep(Date startedAt, TemperatureRange temperature) {
    }
}
