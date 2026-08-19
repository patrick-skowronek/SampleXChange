package de.samply.samplexchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Reads fixture files from the test classpath.
 *
 * <p>Ported from TransFAIR (de.samply.transfair.FileUtils). Unlike the original this fails
 * loudly instead of printing a stack trace and returning partial content, so a missing or
 * unreadable fixture cannot silently turn into an empty assertion.
 */
public class FileUtils {

    private FileUtils() {
    }

    /**
     * Read the given file from test resources and return the contents as a string.
     *
     * @param filename name of the resource file
     * @return file body
     */
    public static String readResourceFile(String filename) {
        try (InputStream in = FileUtils.class.getClassLoader().getResourceAsStream(filename)) {
            if (in == null) {
                throw new IllegalArgumentException("Test resource not found on classpath: " + filename);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read test resource: " + filename, e);
        }
    }
}
