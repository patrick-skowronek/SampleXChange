package de.samply.samplexchange;

import de.samply.samplexchange.configuration.Configuration;
import de.samply.samplexchange.mapper.fhir.FhirInterface;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;

/**
 * Main Application Entrypoint.
 */
@SpringBootApplication()
@Slf4j
public class SampleXChangeApplication implements CommandLineRunner {

    private final Configuration configuration;
    private final List<FhirInterface> mappers;

    /**
     * Loads the mapping service.
     */
    SampleXChangeApplication(Configuration configuration, List<FhirInterface> mappers) {
        this.configuration = configuration;
        this.mappers = mappers;
    }

    /**
     * Starts the program.
     *
     * @param args additional program arguments
     */
    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        SpringApplication.run(SampleXChangeApplication.class, args);

        long endTime = System.currentTimeMillis() - startTime;
        log.info("Finished SampleXChang in {} mil sec", endTime);
    }

    @Override
    public void run(String... args) throws Exception {
        log.trace("EXECUTING : command line runner");

        for (int i = 0; i < args.length; ++i) {
            log.debug("args[{}]: {}", i, args[i]);
        }

        // Execute transfer based on profile
        executeTransfer();
    }

    /**
     * Executes the FHIR resource transfer process based on the configured profile.
     */
    private void executeTransfer() throws Exception {
        log.info("Starting FHIR resource transfer process...");
        log.info("Active profile: {}", configuration.getProfile());
        log.info("Source server: {}", configuration.getSourceServer());
        log.info("Target server: {}", configuration.getTargetServer());
        log.info("SSL validation disabled - source: {}, target: {}",
                configuration.getSource().isDisableSsl(), configuration.getTarget().isDisableSsl());

        if (mappers.isEmpty()) {
            log.error("No FHIR mapper found for profile: {}", configuration.getProfile());
            log.info("Available profiles: MII2BBMRI");
            return;
        }

        // Execute the first matching mapper (there should be only one based on @ConditionalOnExpression)
        FhirInterface mapper = mappers.get(0);
        log.info("Using mapper: {}", mapper.getClass().getSimpleName());

        try {
            mapper.transfer();
            log.info("FHIR resource transfer process completed successfully");
        } catch (Exception e) {
            log.error("FHIR transfer failed: {}", e.getMessage(), e);
            throw e;
        }
    }
}
