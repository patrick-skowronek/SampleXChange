package de.samply.samplexchange.configuration;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Environment configuration parameters.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class Configuration {

    @Value("${app.version}")
    private String appVersion;

    @Value("${profile}")
    private String profile;

    @Value("${fileexportpath}")
    private String fileExportPath;

    @Value("${disablessl}")
    private boolean fhirClientAcceptSsl;

    private FhirServerProperties source = new FhirServerProperties();
    private FhirServerProperties target = new FhirServerProperties();

    // Legacy getters for backward compatibility
    public String getSourceServer() {
        return source.getUrl();
    }

    public String getSourceServerUsername() {
        return source.getUsername();
    }

    public String getSourceServerPassword() {
        return source.getPassword();
    }

    public String getSourceServerBearerToken() {
        return source.getBearerToken();
    }

    public String getSourceKeycloakTokenUrl() {
        return source.getKeycloak().getTokenUrl();
    }

    public String getSourceKeycloakClientId() {
        return source.getKeycloak().getClientId();
    }

    public String getSourceKeycloakClientSecret() {
        return source.getKeycloak().getClientSecret();
    }

    public String getTargetServer() {
        return target.getUrl();
    }

    public String getTargetServerUsername() {
        return target.getUsername();
    }

    public String getTargetServerPassword() {
        return target.getPassword();
    }

    public String getTargetServerBearerToken() {
        return target.getBearerToken();
    }

    public String getTargetKeycloakTokenUrl() {
        return target.getKeycloak().getTokenUrl();
    }

    public String getTargetKeycloakClientId() {
        return target.getKeycloak().getClientId();
    }

    public String getTargetKeycloakClientSecret() {
        return target.getKeycloak().getClientSecret();
    }
}
