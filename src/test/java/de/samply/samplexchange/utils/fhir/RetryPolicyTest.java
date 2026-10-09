package de.samply.samplexchange.utils.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import com.sun.net.httpserver.HttpServer;
import de.samply.samplexchange.SampleXChangeException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Specimen;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryPolicyTest {

    private final List<Duration> pauses = new ArrayList<>();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static IGenericClient clientFor(int port) {
        FhirContext ctx = FhirContext.forR4();
        ctx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        return ctx.newRestfulGenericClient("http://localhost:" + port + "/fhir");
    }

    private static Bundle searchSpecimens(IGenericClient client) {
        return client.search().forResource(Specimen.class).returnBundle(Bundle.class).execute();
    }

    /** Answers every request with an empty search result, like a FHIR server that came back. */
    private void startFhirServer(int port) {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/fhir", exchange -> {
            byte[] body = "{\"resourceType\":\"Bundle\",\"type\":\"searchset\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/fhir+json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @Test
    void aServerThatComesBackDuringTheWaitIsUsed() throws Exception {
        int port = freePort();
        RetryPolicy retry = new RetryPolicy(3, Duration.ofSeconds(30), pause -> {
            pauses.add(pause);
            startFhirServer(port);
        });

        Bundle result = retry.call("source", () -> searchSpecimens(clientFor(port)));

        assertEquals(Bundle.BundleType.SEARCHSET, result.getType());
        assertEquals(List.of(Duration.ofSeconds(30)), pauses, "one failed attempt, one pause");
    }

    @Test
    void afterTheLastAttemptTheRunStopsWithAReadableMessage() throws Exception {
        int port = freePort();
        RetryPolicy retry = new RetryPolicy(2, Duration.ofSeconds(10), pauses::add);

        SampleXChangeException thrown = assertThrows(SampleXChangeException.class,
                () -> retry.call("target", () -> searchSpecimens(clientFor(port))));

        assertEquals("Gave up on the target FHIR server after 3 attempts: the connection was refused",
                thrown.getMessage());
        assertEquals(List.of(Duration.ofSeconds(10), Duration.ofSeconds(10)), pauses);
    }

    @Test
    void aGatewayErrorIsRetried() {
        AtomicInteger calls = new AtomicInteger();
        RetryPolicy retry = new RetryPolicy(3, Duration.ofSeconds(1), pauses::add);

        String result = retry.call("source", () -> {
            if (calls.incrementAndGet() < 3) {
                throw BaseServerResponseException.newInstance(503, "Service Unavailable");
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(2, pauses.size());
    }

    @Test
    void anErrorThatWouldRepeatIsNotRetried() {
        // A rejected bundle gets the same answer the second time, so waiting would only delay the
        // failure.
        InvalidRequestException rejected = new InvalidRequestException("bad bundle");
        RetryPolicy retry = new RetryPolicy(5, Duration.ofSeconds(30), pauses::add);

        InvalidRequestException thrown = assertThrows(InvalidRequestException.class,
                () -> retry.call("target", () -> {
                    throw rejected;
                }));

        assertSame(rejected, thrown);
        assertTrue(pauses.isEmpty());
    }

    @Test
    void withoutRetriesTheFirstLostConnectionStopsTheRun() throws Exception {
        int port = freePort();

        SampleXChangeException thrown = assertThrows(SampleXChangeException.class,
                () -> RetryPolicy.none().call("source", () -> searchSpecimens(clientFor(port))));

        assertEquals("Gave up on the source FHIR server after 1 attempt: the connection was refused",
                thrown.getMessage());
    }

    @Test
    void theStandardPolicyRetriesFiveTimesThirtySecondsApart() {
        RetryPolicy retry = RetryPolicy.standard();

        assertEquals(5, retry.retries());
        assertEquals(Duration.ofSeconds(30), retry.waitBetweenAttempts());
    }
}
