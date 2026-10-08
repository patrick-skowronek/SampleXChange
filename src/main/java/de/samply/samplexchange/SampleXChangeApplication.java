package de.samply.samplexchange;

import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import de.samply.samplexchange.configuration.Configuration;
import de.samply.samplexchange.configuration.FhirServerUrl;
import de.samply.samplexchange.configuration.SourceFormat;
import de.samply.samplexchange.configuration.TargetFormat;
import de.samply.samplexchange.transform.TransferPipeline;
import de.samply.samplexchange.transform.Transformation;
import de.samply.samplexchange.transform.TransformationRegistry;
import de.samply.samplexchange.utils.fhir.FhirServerCheck;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;
import java.util.stream.Collectors;

@SpringBootApplication()
@Slf4j
public class SampleXChangeApplication implements CommandLineRunner, ExitCodeGenerator {
    private final Configuration configuration;
    private final TransformationRegistry registry;
    private final TransferPipeline pipeline;
    private int exitCode = 0;

    SampleXChangeApplication(Configuration configuration,
                             TransformationRegistry registry,
                             TransferPipeline pipeline) {
        this.configuration = configuration;
        this.registry = registry;
        this.pipeline = pipeline;
    }

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        int exitCode = SpringApplication.exit(SpringApplication.run(SampleXChangeApplication.class, args));

        long endTime = System.currentTimeMillis() - startTime;
        log.info("Finished SampleXChange in {} mil sec", endTime);
        System.exit(exitCode);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    @Override
    public void run(String... args) {
        log.trace("EXECUTING : command line runner");

        for (int i = 0; i < args.length; ++i) {
            log.debug("args[{}]: {}", i, args[i]);
        }

        executeTransfer();
    }

    private void executeTransfer() {
        log.info("Starting FHIR resource transfer process...");
        log.info("Source server: {}", configuration.getSourceServer());
        log.info("Target server: {}", configuration.getTargetServer());
        log.info("SSL validation disabled - source: {}, target: {}",
                configuration.getSource().isDisableSsl(), configuration.getTarget().isDisableSsl());


        try {
            pipeline.run(validatedTransformation());
            log.info("FHIR resource transfer process completed successfully");
        } catch (SampleXChangeException e) {
            fail(e.getMessage());
        } catch (FhirClientConnectionException e) {
            fail("Lost the connection to a FHIR server: "
                    + FhirServerCheck.describeConnectionFailure(e, null));
        } catch (BaseServerResponseException e) {
            fail("A FHIR server rejected a request with HTTP %d: %s".formatted(e.getStatusCode(), e.getMessage()));
        } catch (Exception e) {
            log.error("FHIR transfer failed: {}", e.getMessage(), e);
            exitCode = 1;
        }
    }

    private Transformation validatedTransformation() {
        FhirServerUrl.validate("SOURCE_URL", configuration.getSourceServer());
        if (configuration.getFileExportPath() == null || configuration.getFileExportPath().isBlank()) {
            FhirServerUrl.validate("TARGET_URL", configuration.getTargetServer());
        }
        try {
            return registry.findTransformationFor(
                    toEnumValue(SourceFormat.class, configuration.getSourceFormat(), "SOURCE_FORMAT"),
                    toEnumValue(TargetFormat.class, configuration.getTargetFormat(), "TARGET_FORMAT"));
        } catch (IllegalArgumentException e) {
            throw new SampleXChangeException(e.getMessage(), e);
        }
    }

    private void fail(String message) {
        log.error("SampleXChange stopped: {}", message);
        exitCode = 1;
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
