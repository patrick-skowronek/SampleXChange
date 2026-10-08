package de.samply.samplexchange;

/**
 * A failure the user can fix, such as a wrong URL or an unreachable server.
 */
public class SampleXChangeException extends RuntimeException {
    public SampleXChangeException(String message) {
        super(message);
    }

    public SampleXChangeException(String message, Throwable cause) {
        super(message, cause);
    }
}
