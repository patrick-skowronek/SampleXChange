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

@Slf4j
public class KeycloakTokenManager {
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private String cachedToken;
    private Instant tokenExpiryTime;

    public KeycloakTokenManager(String tokenUrl, String clientId, String clientSecret) {
        this(tokenUrl, clientId, clientSecret, false);
    }

    public KeycloakTokenManager(String tokenUrl, String clientId, String clientSecret, boolean disableSsl) {
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.restTemplate = createRestTemplate(disableSsl);
        this.objectMapper = new ObjectMapper();
    }

    private RestTemplate createRestTemplate(boolean disableSsl) {
        if (!disableSsl) {
            return new RestTemplate();
        }

        try {
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

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            HostnameVerifier allHostsValid = (hostname, session) -> true;

            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

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

    public String getToken() throws Exception {
        if (isTokenValid()) {
            log.info("Using cached Keycloak token");
            return cachedToken;
        }

        log.info("Fetching new Keycloak token from {}", tokenUrl);
        return fetchNewToken();
    }

    private boolean isTokenValid() {
        return cachedToken != null && tokenExpiryTime != null && Instant.now().isBefore(tokenExpiryTime);
    }

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

    public void invalidateToken() {
        log.warn("Invalidating cached Keycloak token");
        this.cachedToken = null;
        this.tokenExpiryTime = null;
    }
}
