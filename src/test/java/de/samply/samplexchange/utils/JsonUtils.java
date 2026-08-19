package de.samply.samplexchange.utils;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.instance.model.api.IBaseResource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Compares FHIR resources by serialising both sides to JSON.
 *
 * <p>Ported from TransFAIR (de.samply.transfair.utils.JsonUtils), switched from JUnit 4
 * asserts to JUnit 5 -- this project has no junit-vintage engine, so JUnit 4 assertions
 * would not run.
 */
public class JsonUtils {

    private static final IParser PARSER = FhirContext.forR4().newJsonParser();

    private JsonUtils() {
    }

    /**
     * Serialises both resources to FHIR JSON and asserts they are equal.
     */
    public static void compareFhirObjects(IBaseResource actual, IBaseResource expected) {
        assertEquals(toJson(expected), toJson(actual));
    }

    /**
     * Serialises a resource to FHIR JSON. Useful for golden-file comparisons.
     */
    public static String toJson(IBaseResource resource) {
        return PARSER.encodeResourceToString(resource);
    }
}
