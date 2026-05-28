package de.samply.samplexchange.configuration;

import lombok.Data;

/**
 * Configuration properties for a FHIR server with authentication.
 */
@Data
public class FhirServerProperties {

    private String url;
    private AuthType authType;
    private String username;
    private String password;
    private String bearerToken;
    private boolean enableSsl = true;
    private KeycloakProperties keycloak = new KeycloakProperties();

    @Data
    public static class KeycloakProperties {
        private String tokenUrl;
        private String clientId;
        private String clientSecret;
    }
}
