package de.samply.samplexchange.utils.auth;

import ca.uhn.fhir.rest.client.api.IClientInterceptor;
import ca.uhn.fhir.rest.client.api.IHttpRequest;
import ca.uhn.fhir.rest.client.api.IHttpResponse;
import de.samply.samplexchange.SampleXChangeException;

/**
 * Adds a Keycloak token to every request. The token manager hands out its cached token while it is
 * valid and fetches a new one shortly before it expires, so a run can outlast the token lifetime.
 */
public class KeycloakAuthInterceptor implements IClientInterceptor {
    private final KeycloakTokenManager tokenManager;

    public KeycloakAuthInterceptor(KeycloakTokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @Override
    public void interceptRequest(IHttpRequest request) {
        try {
            request.addHeader("Authorization", "Bearer " + tokenManager.getToken());
        } catch (Exception e) {
            throw new SampleXChangeException("Could not get a token from Keycloak: " + e.getMessage(), e);
        }
    }

    @Override
    public void interceptResponse(IHttpResponse response) {
        // nothing to do
    }
}
