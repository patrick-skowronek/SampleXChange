package de.samply.samplexchange;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Application Entrypoint.
 */
@SpringBootApplication()
@Slf4j
public class SampleXChangeApplication implements CommandLineRunner {

    /**
     * Loads the mapping service.
     */
    SampleXChangeApplication() {
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
        log.debug("EXECUTING : command line runner");

        for (int i = 0; i < args.length; ++i) {
            log.debug("args[{}]: {}", i, args[i]);
        }

    }
}
