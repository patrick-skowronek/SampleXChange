package de.samply.samplexchange.terminology;

import de.samply.samplexchange.configuration.TargetFormat;
import de.samply.samplexchange.domain.CodedValue;
import de.samply.samplexchange.domain.TemperatureRange;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
public class Terminology {
    private static final String SAMPLE_TYPE = "terminology/sample-type.csv";
    private static final String STORAGE_TEMPERATURE = "terminology/storage-temperature.csv";
    private static final String COLLECTION_TYPE = "terminology/collection-type.csv";
    private static final String MIABIS_COLLECTION_DESIGN = "/miabis-collection-design-cs";
    private static final String MIABIS_COLLECTION_SETTING = "/miabis-sample-collection-setting-cs";

    private final CodeMap sampleType;
    private final TemperatureTable storageTemperature;
    private final CodeMap collectionType;

    public Terminology() {
        this(CodeMap.load(SAMPLE_TYPE, "snomed"), TemperatureTable.load(STORAGE_TEMPERATURE),
                CodeMap.load(COLLECTION_TYPE, "miabis"));
    }

    Terminology(CodeMap sampleType, TemperatureTable storageTemperature, CodeMap collectionType) {
        this.sampleType = sampleType;
        this.storageTemperature = storageTemperature;
        this.collectionType = collectionType;
        log.info("Terminology loaded: {} sample types, {} temperature ranges, {} collection types",
                sampleType.size(), storageTemperature.size(), collectionType.size());
    }

    public Optional<String> sampleType(TargetFormat target, CodedValue type) {
        return type == null ? Optional.empty() : sampleType.findCodeFor(target, type.code());
    }

    public Optional<String> storageTemperature(TargetFormat target, TemperatureRange range) {
        return storageTemperature.findBucketFor(target, range);
    }

    /**
     * Translates a MIABIS collection design or sample collection setting. Both MIABIS spellings of
     * the code system canonical are accepted, with and without /fhir/.
     */
    public Optional<String> collectionType(TargetFormat target, CodedValue type) {
        if (type == null || type.system() == null) {
            return Optional.empty();
        }
        if (type.system().endsWith(MIABIS_COLLECTION_DESIGN)) {
            return collectionType.findCodeFor(target, "design/" + type.code());
        }
        if (type.system().endsWith(MIABIS_COLLECTION_SETTING)) {
            return collectionType.findCodeFor(target, "setting/" + type.code());
        }
        return Optional.empty();
    }

    public void logSampleTypesWithNoMapping() {
        var unmatched = sampleType.codesWithNoRow();
        if (unmatched.isEmpty()) {
            return;
        }
        log.warn("{} sample type code(s) had no mapping and fell back to the target default: {}",
                unmatched.size(), unmatched);
    }
}
