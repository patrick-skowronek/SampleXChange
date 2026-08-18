package de.samply.samplexchange.utils.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import de.samply.samplexchange.configuration.Configuration;
import de.samply.samplexchange.repository.fhir.FhirServerSaver;
import de.samply.samplexchange.utils.auth.KeycloakTokenManager;
import de.samply.samplexchange.utils.fhir.clients.FhirClient;
import de.samply.samplexchange.writers.fhir.FhirFileSaver;
import lombok.extern.slf4j.Slf4j;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Main Class for working with fhir mappings.
 */
@Slf4j
public class FhirComponent {

    private final FhirContext ctx;
    /**
     * Configuration.
     */

    public Configuration configuration;

    /**
     * transferController.
     */
    public FhirTransfer transferController;

    /**
     * Source fhir client.
     */
    private FhirClient sourceFhirServer;

    /**
     * Fhir export interface.
     */
    private FhirExportInterface fhirExportInterface;

    /**
     * Keycloak token managers.
     */
    private KeycloakTokenManager sourceKeycloakTokenManager;
    private KeycloakTokenManager targetKeycloakTokenManager;

    /**
     * Constructor.
     */
    public FhirComponent(Configuration configuration) throws Exception {
        this.configuration = configuration;
        ctx = FhirContext.forR4();
        ctx.getRestfulClientFactory().setSocketTimeout(300 * 1000);

        this.transferController = new FhirTransfer(ctx);
    }

    /**
     * Returns source fhir client.
     */
    public IGenericClient getSourceFhirServer()
            throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {

        if (Objects.nonNull(sourceFhirServer)) {
            return this.sourceFhirServer.getClient();
        }

        sourceFhirServer =
                new FhirClient(
                        ctx, configuration.getSource().getUrl(), configuration.getSource().isDisableSsl());

        setAuthForSource(sourceFhirServer);
        log.info("Start collecting Resources from FHIR server {}", configuration.getSource().getUrl());

        return sourceFhirServer.getClient();
    }

    private void setAuthForSource(FhirClient client) {
        try {
            var source = configuration.getSource();
            var authType = source.getAuthType();

            if (authType == null) {
                throw new IllegalArgumentException("Source authentication type (SOURCE_AUTH_TYPE) must be specified");
            }

            switch (authType) {
                case KEYCLOAK -> {
                    validateKeycloakConfig(source.getKeycloak(), "source");
                    sourceKeycloakTokenManager = new KeycloakTokenManager(
                            source.getKeycloak().getTokenUrl(),
                            source.getKeycloak().getClientId(),
                            source.getKeycloak().getClientSecret(),
                            source.isDisableSsl()
                    );
                    String token = sourceKeycloakTokenManager.getToken();
                    log.info("Setting Keycloak Bearer Token Authentication for source FHIR server {}", client.getClient().getServerBase());
                    client.setBearerAuth(token);
                }
                case BEARER -> {
                    if (source.getBearerToken() == null || source.getBearerToken().isBlank()) {
                        throw new IllegalArgumentException("Source bearer token (SOURCE_BEARERTOKEN) is required for BEARER auth type");
                    }
                    log.info("Setting static Bearer Token Authentication for source FHIR server {}", client.getClient().getServerBase());
                    client.setBearerAuth(source.getBearerToken());
                }
                case BASIC -> {
                    if (source.getUsername() == null || source.getUsername().isBlank() ||
                        source.getPassword() == null || source.getPassword().isBlank()) {
                        throw new IllegalArgumentException("Source username and password (SOURCE_USERNAME, SOURCE_PASSWORD) are required for BASIC auth type");
                    }
                    log.info("Setting Basic Authentication for source FHIR server {}", client.getClient().getServerBase());
                    client.setBasicAuth(source.getUsername(), source.getPassword());
                }
            }
        } catch (Exception e) {
            log.error("Failed to set authentication for source server: {}", e.getMessage());
            throw new RuntimeException("Authentication setup failed for source", e);
        }
    }

    private void setAuthForTarget(FhirClient client) {
        try {
            var target = configuration.getTarget();
            var authType = target.getAuthType();

            if (authType == null) {
                throw new IllegalArgumentException("Target authentication type (TARGET_AUTH_TYPE) must be specified");
            }

            switch (authType) {
                case KEYCLOAK -> {
                    validateKeycloakConfig(target.getKeycloak(), "target");
                    targetKeycloakTokenManager = new KeycloakTokenManager(
                            target.getKeycloak().getTokenUrl(),
                            target.getKeycloak().getClientId(),
                            target.getKeycloak().getClientSecret(),
                            target.isDisableSsl()
                    );
                    String token = targetKeycloakTokenManager.getToken();
                    log.info("Setting Keycloak Bearer Token Authentication for target FHIR server {}", client.getClient().getServerBase());
                    client.setBearerAuth(token);
                }
                case BEARER -> {
                    if (target.getBearerToken() == null || target.getBearerToken().isBlank()) {
                        throw new IllegalArgumentException("Target bearer token (TARGET_BEARERTOKEN) is required for BEARER auth type");
                    }
                    log.info("Setting static Bearer Token Authentication for target FHIR server {}", client.getClient().getServerBase());
                    client.setBearerAuth(target.getBearerToken());
                }
                case BASIC -> {
                    if (target.getUsername() == null || target.getUsername().isBlank() ||
                        target.getPassword() == null || target.getPassword().isBlank()) {
                        throw new IllegalArgumentException("Target username and password (TARGET_USERNAME, TARGET_PASSWORD) are required for BASIC auth type");
                    }
                    log.info("Setting Basic Authentication for target FHIR server {}", client.getClient().getServerBase());
                    client.setBasicAuth(target.getUsername(), target.getPassword());
                }
            }
        } catch (Exception e) {
            log.error("Failed to set authentication for target server: {}", e.getMessage());
            throw new RuntimeException("Authentication setup failed for target", e);
        }
    }

    private void validateKeycloakConfig(de.samply.samplexchange.configuration.FhirServerProperties.KeycloakProperties keycloak, String serverType) {
        if (keycloak.getTokenUrl() == null || keycloak.getTokenUrl().isBlank()) {
            throw new IllegalArgumentException(serverType + " Keycloak token URL is required for KEYCLOAK auth type");
        }
        if (keycloak.getClientId() == null || keycloak.getClientId().isBlank()) {
            throw new IllegalArgumentException(serverType + " Keycloak client ID is required for KEYCLOAK auth type");
        }
        if (keycloak.getClientSecret() == null || keycloak.getClientSecret().isBlank()) {
            throw new IllegalArgumentException(serverType + " Keycloak client secret is required for KEYCLOAK auth type");
        }
    }

    /**
     * Returns fhir export interface.
     */
    public FhirExportInterface getFhirExportInterface()
            throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        if (Objects.nonNull(fhirExportInterface)) {
            return fhirExportInterface;
        }

        if (!configuration.getFileExportPath().isBlank()) {
            log.info("Exporting resources to file system " + configuration.getFileExportPath());
            this.fhirExportInterface = new FhirFileSaver(ctx, configuration.getFileExportPath());
        } else {
            FhirServerSaver fhirServerSaver =
                    new FhirServerSaver(
                            ctx, configuration.getTarget().getUrl(), configuration.getTarget().isDisableSsl());

            setAuthForTarget(fhirServerSaver.getClient());
            log.info("Exporting resources to FHIR server " + configuration.getTarget().getUrl());
            fhirExportInterface = fhirServerSaver;
        }

        return fhirExportInterface;
    }
}
