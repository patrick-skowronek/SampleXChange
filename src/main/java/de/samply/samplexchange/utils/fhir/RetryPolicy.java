package de.samply.samplexchange.utils.fhir;

import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import de.samply.samplexchange.SampleXChangeException;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Repeats a request to a FHIR server that failed because the server was briefly unavailable.
 *
 * <p>Only lost connections and the gateway answers 502, 503 and 504 are retried.
 */
@Slf4j
public final class RetryPolicy {
    /** How often a request is repeated after a lost connection. */
    public static final int RETRY_COUNT = 5;
    /** The wait between attempts. With the count above, an outage of about 2.5 minutes is bridged. */
    public static final Duration RETRY_INTERVAL = Duration.ofSeconds(30);

    private static final Set<Integer> TEMPORARY_STATUS_CODES = Set.of(502, 503, 504);

    /** Waits between attempts; replaced in tests so they do not sleep. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final int retries;
    private final Duration wait;
    private final Sleeper sleeper;

    RetryPolicy(int retries, Duration wait, Sleeper sleeper) {
        this.retries = retries;
        this.wait = wait;
        this.sleeper = sleeper;
    }

    /** No retries: a failed request ends the run. */
    public static RetryPolicy none() {
        return new RetryPolicy(0, Duration.ZERO, duration -> { });
    }

    /** {@link #RETRY_COUNT} retries, {@link #RETRY_INTERVAL} apart. */
    public static RetryPolicy standard() {
        return new RetryPolicy(RETRY_COUNT, RETRY_INTERVAL, duration -> Thread.sleep(duration.toMillis()));
    }

    public int retries() {
        return retries;
    }

    public Duration waitBetweenAttempts() {
        return wait;
    }

    /**
     * Sends {@code request}, and sends it again after a pause while the server is unavailable.
     *
     * @param server "source" or "target", for the log and the error message
     * @throws SampleXChangeException once every attempt has failed
     */
    public <T> T call(String server, Supplier<T> request) {
        int attempts = retries + 1;
        for (int attempt = 1; ; attempt++) {
            try {
                return request.get();
            } catch (BaseServerResponseException e) {
                if (!isTemporary(e)) {
                    throw e;
                }
                if (attempt == attempts) {
                    throw new SampleXChangeException("Gave up on the %s FHIR server after %d %s: %s"
                            .formatted(server, attempts, attempts == 1 ? "attempt" : "attempts", reason(e)), e);
                }
                log.warn("The {} FHIR server is not available ({}). Attempt {} of {}, trying again in {} s",
                        server, reason(e), attempt, attempts, wait.toSeconds());
                pause(server);
            }
        }
    }

    /** {@link FhirClientConnectionException} is a {@link BaseServerResponseException} with status 0. */
    static boolean isTemporary(BaseServerResponseException e) {
        return e instanceof FhirClientConnectionException || TEMPORARY_STATUS_CODES.contains(e.getStatusCode());
    }

    private static String reason(BaseServerResponseException e) {
        return e instanceof FhirClientConnectionException
                ? FhirServerCheck.describeConnectionFailure(e, null)
                : "HTTP " + e.getStatusCode();
    }

    private void pause(String server) {
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SampleXChangeException("Interrupted while waiting for the " + server + " FHIR server", e);
        }
    }
}
