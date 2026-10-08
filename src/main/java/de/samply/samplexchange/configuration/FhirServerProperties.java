package de.samply.samplexchange.configuration;

import lombok.Data;

@Data
public class FhirServerProperties {
    private String url;
    private AuthType authType = AuthType.NONE;
    private String username;
    private String password;
    private String bearerToken;
    private boolean disableSsl = false;
    private KeycloakProperties keycloak = new KeycloakProperties();

    /** NONE when unset, including when the variable is present but empty. */
    public AuthType getAuthType() {
        return authType == null ? AuthType.NONE : authType;
    }

    @Data
    public static class KeycloakProperties {
        private String tokenUrl;
        private String clientId;
        private String clientSecret;
    }
}
