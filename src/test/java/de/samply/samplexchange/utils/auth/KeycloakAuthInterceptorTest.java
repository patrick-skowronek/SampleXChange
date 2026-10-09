package de.samply.samplexchange.utils.auth;

import ca.uhn.fhir.rest.client.api.IHttpRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Every request asks for a token, and a new one is fetched only once the old one has run out. */
class KeycloakAuthInterceptorTest {

    private final AtomicInteger tokensIssued = new AtomicInteger();
    private HttpServer keycloak;

    @AfterEach
    void stop() {
        if (keycloak != null) {
            keycloak.stop(0);
        }
    }

    /** A token endpoint that issues token-1, token-2, ... each valid for {@code lifetimeSeconds}. */
    private String startKeycloak(int lifetimeSeconds) throws IOException {
        keycloak = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        keycloak.createContext("/token", exchange -> {
            String body = "{\"access_token\":\"token-%d\",\"expires_in\":%d}"
                    .formatted(tokensIssued.incrementAndGet(), lifetimeSeconds);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        keycloak.start();
        return "http://localhost:" + keycloak.getAddress().getPort() + "/token";
    }

    @Test
    void aValidTokenIsReusedForTheNextRequest() throws Exception {
        KeycloakAuthInterceptor interceptor = new KeycloakAuthInterceptor(
                new KeycloakTokenManager(startKeycloak(300), "client", "secret"));
        IHttpRequest first = mock(IHttpRequest.class);
        IHttpRequest second = mock(IHttpRequest.class);

        interceptor.interceptRequest(first);
        interceptor.interceptRequest(second);

        verify(first).addHeader("Authorization", "Bearer token-1");
        verify(second).addHeader("Authorization", "Bearer token-1");
        assertEquals(1, tokensIssued.get());
    }

    @Test
    void anExpiredTokenIsReplacedBeforeTheNextRequest() throws Exception {
        // The manager treats a token as expired 30 s early, so a 30 s token has run out at once.
        KeycloakAuthInterceptor interceptor = new KeycloakAuthInterceptor(
                new KeycloakTokenManager(startKeycloak(30), "client", "secret"));
        IHttpRequest first = mock(IHttpRequest.class);
        IHttpRequest second = mock(IHttpRequest.class);

        interceptor.interceptRequest(first);
        interceptor.interceptRequest(second);

        verify(first).addHeader("Authorization", "Bearer token-1");
        verify(second).addHeader("Authorization", "Bearer token-2");
        assertEquals(2, tokensIssued.get());
    }
}
