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
                        ctx, configuration.getSourceServer(), configuration.isFhirClientAcceptSsl());
        
        // Initialize Keycloak token manager if configured
        if (isKeycloakConfigured(configuration.getSourceKeycloakTokenUrl(), 
                                  configuration.getSourceKeycloakClientId(), 
                                  configuration.getSourceKeycloakClientSecret())) {
            sourceKeycloakTokenManager = new KeycloakTokenManager(
                    configuration.getSourceKeycloakTokenUrl(),
                    configuration.getSourceKeycloakClientId(),
                    configuration.getSourceKeycloakClientSecret()
            );
        }
        
        setAuthForSource(sourceFhirServer);
        log.info("Start collecting Resources from FHIR server {}", configuration.getSourceServer());

        return sourceFhirServer.getClient();
    }

    private void setAuthForSource(FhirClient client) {
        try {
            // Priority 1: Keycloak token
            if (sourceKeycloakTokenManager != null) {
                String token = sourceKeycloakTokenManager.getToken();
                log.info("Setting Keycloak Bearer Token Authentication for FHIR server {}", client.getClient().getServerBase());
                client.setBearerAuth(token);
                return;
            }
            
            // Priority 2: Static bearer token
            if (configuration.getSourceServerBearerToken() != null && !configuration.getSourceServerBearerToken().isBlank()) {
                log.info("Setting static Bearer Token Authentication for FHIR server {}", client.getClient().getServerBase());
                client.setBearerAuth(configuration.getSourceServerBearerToken());
                return;
            }
            
            // Priority 3: Basic auth
            if (!configuration.getSourceServerUsername().isBlank() && !configuration.getSourceServerPassword().isBlank()) {
                log.info("Setting Basic Authentication for FHIR server {}", client.getClient().getServerBase());
                client.setBasicAuth(configuration.getSourceServerUsername(), configuration.getSourceServerPassword());
            }
        } catch (Exception e) {
            log.error("Failed to set authentication for source server: {}", e.getMessage());
            throw new RuntimeException("Authentication setup failed", e);
        }
    }

    private void setAuthForTarget(FhirClient client) {
        try {
            // Priority 1: Keycloak token
            if (targetKeycloakTokenManager != null) {
                String token = targetKeycloakTokenManager.getToken();
                log.info("Setting Keycloak Bearer Token Authentication for FHIR server {}", client.getClient().getServerBase());
                client.setBearerAuth(token);
                return;
            }
            
            // Priority 2: Static bearer token
            if (configuration.getTargetServerBearerToken() != null && !configuration.getTargetServerBearerToken().isBlank()) {
                log.info("Setting static Bearer Token Authentication for FHIR server {}", client.getClient().getServerBase());
                client.setBearerAuth(configuration.getTargetServerBearerToken());
                return;
            }
            
            // Priority 3: Basic auth
            if (!configuration.getTargetServerUsername().isBlank() && !configuration.getTargetServerPassword().isBlank()) {
                log.info("Setting Basic Authentication for FHIR server {}", client.getClient().getServerBase());
                client.setBasicAuth(configuration.getTargetServerUsername(), configuration.getTargetServerPassword());
            }
        } catch (Exception e) {
            log.error("Failed to set authentication for target server: {}", e.getMessage());
            throw new RuntimeException("Authentication setup failed", e);
        }
    }

    private boolean isKeycloakConfigured(String tokenUrl, String clientId, String clientSecret) {
        return tokenUrl != null && !tokenUrl.isBlank() &&
               clientId != null && !clientId.isBlank() &&
               clientSecret != null && !clientSecret.isBlank();
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
                            ctx, configuration.getTargetServer(), configuration.isFhirClientAcceptSsl());
            
            // Initialize Keycloak token manager for target if configured
            if (isKeycloakConfigured(configuration.getTargetKeycloakTokenUrl(), 
                                      configuration.getTargetKeycloakClientId(), 
                                      configuration.getTargetKeycloakClientSecret())) {
                targetKeycloakTokenManager = new KeycloakTokenManager(
                        configuration.getTargetKeycloakTokenUrl(),
                        configuration.getTargetKeycloakClientId(),
                        configuration.getTargetKeycloakClientSecret()
                );
            }
            
            setAuthForTarget(fhirServerSaver.getClient());
            log.info("Exporting resources to FHIR server " + configuration.getTargetServer());
            fhirExportInterface = fhirServerSaver;
        }

        return fhirExportInterface;
    }
}
