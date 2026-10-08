package de.samply.samplexchange.utils.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import de.samply.samplexchange.SampleXChangeException;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The messages a user sees instead of a stack trace when a server cannot be used. */
class FhirServerCheckTest {

    private static final FhirContext CTX = FhirContext.forR4();

    @Test
    void anUnknownHostNamesTheServerAndTheVariable() {
        IGenericClient client = CTX.newRestfulGenericClient("http://does-not-exist.invalid:8080/fhir");

        SampleXChangeException thrown = assertThrows(SampleXChangeException.class,
                () -> FhirServerCheck.ensureReachable(client, "source", "SOURCE"));

        assertEquals("Cannot connect to the source FHIR server at http://does-not-exist.invalid:8080/fhir: "
                + "the host name does-not-exist.invalid cannot be resolved. Check SOURCE_URL and that the "
                + "server is running and reachable from where SampleXChange runs.", thrown.getMessage());
    }

    @Test
    void aClosedPortIsReportedAsARefusedConnection() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        IGenericClient client = CTX.newRestfulGenericClient("http://localhost:" + closedPort + "/fhir");

        SampleXChangeException thrown = assertThrows(SampleXChangeException.class,
                () -> FhirServerCheck.ensureReachable(client, "target", "TARGET"));

        assertTrue(thrown.getMessage().contains("the connection was refused"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("Check TARGET_URL"), thrown.getMessage());
    }

    @Test
    void aNextPageLinkToAnotherAddressStopsTheRead() {
        IGenericClient client = mock(IGenericClient.class);
        when(client.getServerBase()).thenReturn("http://source-blaze:8080/fhir");
        Bundle page = new Bundle();
        page.addLink().setRelation("next").setUrl("http://localhost:8081/fhir/Specimen?__page-offset=50");

        SampleXChangeException thrown = assertThrows(SampleXChangeException.class,
                () -> new FhirTransfer(CTX).nextPage(client, page));

        assertTrue(thrown.getMessage().contains("http://localhost:8081/fhir/Specimen"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("BASE_URL"), thrown.getMessage());
    }
}
