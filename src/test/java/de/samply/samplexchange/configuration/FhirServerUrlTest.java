package de.samply.samplexchange.configuration;

import de.samply.samplexchange.SampleXChangeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FhirServerUrlTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:8080/fhir",
            "http://localhost:8080/fhir/",
            "https://blaze.example.org/fhir",
            "http://source-blaze:8080/api/FHIR",
    })
    void aUrlEndingInFhirIsAccepted(String url) {
        FhirServerUrl.validate("SOURCE_URL", url);
    }

    @Test
    void aUrlWithoutFhirAtTheEndIsRejectedWithAnExample() {
        SampleXChangeException thrown = assertThrows(SampleXChangeException.class,
                () -> FhirServerUrl.validate("SOURCE_URL", "http://localhost:8080"));

        assertEquals("SOURCE_URL=http://localhost:8080 must end with /fhir, for example "
                + "http://localhost:8080/fhir", thrown.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8080/fhir/Patient", "http://localhost:8080/myfhir"})
    void fhirMustBeTheLastPathSegment(String url) {
        assertThrows(SampleXChangeException.class, () -> FhirServerUrl.validate("TARGET_URL", url));
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost:8080/fhir", "ftp://localhost/fhir", "http:///fhir", "not a url"})
    void somethingThatIsNotAnHttpUrlIsRejected(String url) {
        SampleXChangeException thrown = assertThrows(SampleXChangeException.class,
                () -> FhirServerUrl.validate("TARGET_URL", url));
        assertTrue(thrown.getMessage().startsWith("TARGET_URL="), thrown.getMessage());
    }

    @Test
    void aMissingUrlNamesTheVariable() {
        SampleXChangeException thrown = assertThrows(SampleXChangeException.class,
                () -> FhirServerUrl.validate("SOURCE_URL", " "));
        assertEquals("SOURCE_URL is not set", thrown.getMessage());
    }

    @Test
    void aNextPageOnTheSameServerIsAccepted() {
        assertTrue(FhirServerUrl.isOnServer("http://source-blaze:8080/fhir",
                "http://source-blaze:8080/fhir/Specimen?_count=50&__t=1&__page-offset=50"));
        assertTrue(FhirServerUrl.isOnServer("http://Source-Blaze:80/fhir/", "http://source-blaze/fhir/__page/abc"));
    }

    @Test
    void aNextPageOnAnotherAddressIsRefused() {
        // Blaze builds links from its BASE_URL, which may be the address seen from the host.
        assertFalse(FhirServerUrl.isOnServer("http://source-blaze:8080/fhir",
                "http://localhost:8081/fhir/Specimen?__page-offset=50"));
        assertFalse(FhirServerUrl.isOnServer("http://source-blaze:8080/fhir",
                "https://source-blaze:8080/fhir/Specimen"));
        assertFalse(FhirServerUrl.isOnServer("http://source-blaze:8080/fhir",
                "http://source-blaze:8080/fhirother/Specimen"));
    }

    @Test
    void theAuthTypeDefaultsToNone() {
        FhirServerProperties properties = new FhirServerProperties();
        assertEquals(AuthType.NONE, properties.getAuthType());

        // An empty SOURCE_AUTH_TYPE binds as null.
        properties.setAuthType(null);
        assertEquals(AuthType.NONE, properties.getAuthType());
    }
}
