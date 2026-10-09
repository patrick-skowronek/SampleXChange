package de.samply.samplexchange.utils.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import com.sun.net.httpserver.HttpServer;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Specimen;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the reader asks the source for, and what it keeps from the answer. */
class FhirTransferTest {

    private static final FhirContext CTX = FhirContext.forR4();

    private final List<String> queries = new ArrayList<>();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** A FHIR server that answers every search with {@code result} and records the query. */
    private IGenericClient serverAnswering(Bundle result) throws IOException {
        byte[] body = CTX.newJsonParser().encodeResourceToString(result).getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/fhir", exchange -> {
            queries.add(exchange.getRequestURI().getRawQuery());
            exchange.getResponseHeaders().add("Content-Type", "application/fhir+json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        FhirContext ctx = FhirContext.forR4();
        ctx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        return ctx.newRestfulGenericClient("http://localhost:" + server.getAddress().getPort() + "/fhir");
    }

    private static Bundle searchResult(org.hl7.fhir.r4.model.Resource... resources) {
        Bundle bundle = new Bundle().setType(Bundle.BundleType.SEARCHSET);
        for (org.hl7.fhir.r4.model.Resource resource : resources) {
            bundle.addEntry().setResource(resource);
        }
        return bundle;
    }

    private static OperationOutcome warning() {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.WARNING)
                .setDiagnostics("Unknown search parameter ignored");
        return outcome;
    }

    @Test
    void theDonorScanAsksOnlyForTheSubject() throws Exception {
        Specimen withDonor = new Specimen().setSubject(new Reference("Patient/p1"));
        Specimen withoutDonor = new Specimen();
        IGenericClient client = serverAnswering(searchResult(withDonor, withoutDonor));

        Set<String> references = new FhirTransfer(CTX).fetchDonorReferencesFromSpecimens(client);

        assertEquals(Set.of("Patient/p1"), references);
        assertTrue(queries.get(0).contains("_elements=subject"), queries.get(0));
    }

    @Test
    void anOperationOutcomeInTheResultsIsLeftOutInsteadOfBreakingTheRead() throws Exception {
        Specimen specimen = new Specimen().setSubject(new Reference("Patient/p1"));
        IGenericClient client = serverAnswering(searchResult(warning(), specimen));

        List<Specimen> specimens = new FhirTransfer(CTX).fetchSpecimensOfDonor(client, "p1");

        assertEquals(1, specimens.size());
    }

    @Test
    void conditionsAreFilteredToConditions() throws Exception {
        IGenericClient client = serverAnswering(
                searchResult(new Condition().setSubject(new Reference("Patient/p1")), warning()));

        List<IBaseResource> conditions = new FhirTransfer(CTX).fetchConditionsOfDonor(client, "p1");

        assertEquals(1, conditions.size());
        assertTrue(conditions.get(0) instanceof Condition);
    }
}
