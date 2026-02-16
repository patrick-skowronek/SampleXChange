package de.samply.samplexchange.utils.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

/**
 * Manages Keycloak token retrieval and caching.
 */
@Slf4j
public class KeycloakTokenManager {

    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private String cachedToken;
    private Instant tokenExpiryTime;

    /**
     * Constructor.
     *
     * @param tokenUrl     Keycloak token endpoint URL
     * @param clientId     Client ID
     * @param clientSecret Client secret
     */
    public KeycloakTokenManager(String tokenUrl, String clientId, String clientSecret) {
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Gets a valid access token. Returns cached token if still valid, otherwise fetches a new one.
     *
     * @return Valid access token
     * @throws Exception if token retrieval fails
     */
    public String getToken() throws Exception {
        if (isTokenValid()) {
            log.debug("Using cached Keycloak token");
            return cachedToken;
        }

        log.info("Fetching new Keycloak token from {}", tokenUrl);
        return fetchNewToken();
    }

    /**
     * Checks if the cached token is still valid.
     */
    private boolean isTokenValid() {
        return cachedToken != null && tokenExpiryTime != null && Instant.now().isBefore(tokenExpiryTime);
    }

    /**
     * Fetches a new token from Keycloak.
     */
    private String fetchNewToken() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    tokenUrl,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                cachedToken = jsonNode.get("access_token").asText();
                int expiresIn = jsonNode.get("expires_in").asInt();

                // Set expiry time with 30 second buffer to avoid edge cases
                tokenExpiryTime = Instant.now().plusSeconds(expiresIn - 30);

                log.info("Successfully retrieved Keycloak token, expires in {} seconds", expiresIn);
                return cachedToken;
            } else {
                throw new Exception("Failed to retrieve Keycloak token: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error fetching Keycloak token: {}", e.getMessage());
            throw new Exception("Failed to fetch Keycloak token", e);
        }
    }

    /**
     * Clears the cached token, forcing a new fetch on next getToken() call.
     */
    public void invalidateToken() {
        log.debug("Invalidating cached Keycloak token");
        this.cachedToken = null;
        this.tokenExpiryTime = null;
    }
}
