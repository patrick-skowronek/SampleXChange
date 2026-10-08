package de.samply.samplexchange;

import de.samply.samplexchange.configuration.Configuration;
import de.samply.samplexchange.configuration.SourceFormat;
import de.samply.samplexchange.configuration.TargetFormat;
import de.samply.samplexchange.transform.TransferPipeline;
import de.samply.samplexchange.transform.Transformation;
import de.samply.samplexchange.transform.TransformationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;
import java.util.stream.Collectors;

@SpringBootApplication()
@Slf4j
public class SampleXChangeApplication implements CommandLineRunner {
    private final Configuration configuration;
    private final TransformationRegistry registry;
    private final TransferPipeline pipeline;

    SampleXChangeApplication(Configuration configuration,
                             TransformationRegistry registry,
                             TransferPipeline pipeline) {
        this.configuration = configuration;
        this.registry = registry;
        this.pipeline = pipeline;
    }

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        SpringApplication.run(SampleXChangeApplication.class, args);

        long endTime = System.currentTimeMillis() - startTime;
        log.info("Finished SampleXChange in {} mil sec", endTime);
    }

    @Override
    public void run(String... args) throws Exception {
        log.trace("EXECUTING : command line runner");

        for (int i = 0; i < args.length; ++i) {
            log.debug("args[{}]: {}", i, args[i]);
        }

        executeTransfer();
    }

    private void executeTransfer() throws Exception {
        log.info("Starting FHIR resource transfer process...");
        log.info("Source server: {}", configuration.getSourceServer());
        log.info("Target server: {}", configuration.getTargetServer());
        log.info("SSL validation disabled - source: {}, target: {}",
                configuration.getSource().isDisableSsl(), configuration.getTarget().isDisableSsl());

        Transformation transformation;
        try {
            transformation = registry.findTransformationFor(
                    toEnumValue(SourceFormat.class, configuration.getSourceFormat(), "SOURCE_FORMAT"),
                    toEnumValue(TargetFormat.class, configuration.getTargetFormat(), "TARGET_FORMAT"));
        } catch (IllegalArgumentException e) {
            log.error("{}", e.getMessage());
            return;
        }

        try {
            pipeline.run(transformation);
            log.info("FHIR resource transfer process completed successfully");
        } catch (Exception e) {
            log.error("FHIR transfer failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    private static <E extends Enum<E>> E toEnumValue(Class<E> type, String value, String variable) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "%s is not set. Accepted values: %s".formatted(variable, listAcceptedValues(type)));
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "%s=%s is not recognised. Accepted values: %s"
                            .formatted(variable, value, listAcceptedValues(type)));
        }
    }

    private static <E extends Enum<E>> String listAcceptedValues(Class<E> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
