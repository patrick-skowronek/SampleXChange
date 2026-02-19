package de.samply.samplexchange.utils.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.cert.X509Certificate;
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
        this(tokenUrl, clientId, clientSecret, false);
    }

    /**
     * Constructor with SSL option.
     *
     * @param tokenUrl     Keycloak token endpoint URL
     * @param clientId     Client ID
     * @param clientSecret Client secret
     * @param disableSsl   Whether to disable SSL certificate validation
     */
    public KeycloakTokenManager(String tokenUrl, String clientId, String clientSecret, boolean disableSsl) {
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.restTemplate = createRestTemplate(disableSsl);
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Creates a RestTemplate with optional SSL validation disabled.
     */
    private RestTemplate createRestTemplate(boolean disableSsl) {
        if (!disableSsl) {
            return new RestTemplate();
        }

        try {
            // Create a trust manager that accepts all certificates
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
            };

            // Install the all-trusting trust manager
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // Create a hostname verifier that accepts all hostnames
            HostnameVerifier allHostsValid = (hostname, session) -> true;

            // Set the default SSLSocketFactory and HostnameVerifier
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

            // Create SimpleClientHttpRequestFactory
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
                @Override
                protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                    if (connection instanceof HttpsURLConnection) {
                        ((HttpsURLConnection) connection).setSSLSocketFactory(sslContext.getSocketFactory());
                        ((HttpsURLConnection) connection).setHostnameVerifier(allHostsValid);
                    }
                    super.prepareConnection(connection, httpMethod);
                }
            };

            log.info("Created Keycloak RestTemplate with SSL verification disabled");
            return new RestTemplate(factory);
        } catch (Exception e) {
            log.error("Failed to create RestTemplate with disabled SSL, using default: {}", e.getMessage(), e);
            return new RestTemplate();
        }
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
