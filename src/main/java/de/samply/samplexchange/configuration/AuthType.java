package de.samply.samplexchange.configuration;

/**
 * Supported authentication types for FHIR servers.
 */
public enum AuthType {
    /** Keycloak OAuth2 authentication */
    KEYCLOAK,
    /** Static bearer token authentication */
    BEARER,
    /** HTTP Basic authentication */
    BASIC
}
