package de.samply.samplexchange.utils.fhir.clients;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IClientInterceptor;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BasicAuthInterceptor;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

@Getter
@Slf4j
public class FhirClient {
    private final IGenericClient client;

    public FhirClient(FhirContext ctx, String server, boolean ssl)
            throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        if (ssl) {
            try {
                KeyStore truststore = null;
                SSLContext sslContext =
                        SSLContexts.custom()
                                .loadTrustMaterial(truststore, new TrustSelfSignedStrategy())
                                .build();

                HostnameVerifier hostnameVerifier = NoopHostnameVerifier.INSTANCE;
                SSLConnectionSocketFactory sslFactory =
                        new SSLConnectionSocketFactory(sslContext, hostnameVerifier);

                CloseableHttpClient httpClient =
                        HttpClients.custom().setSSLSocketFactory(sslFactory).build();
                ctx.getRestfulClientFactory().setHttpClient(httpClient);
                log.info("Disable SSL checking");
            } catch (Exception e) {
                log.info(e.getMessage());
            }
        }
        client = ctx.newRestfulGenericClient(server);
    }

    public void setBasicAuth(String username, String password) {
        IClientInterceptor authInterceptor = new BasicAuthInterceptor(username, password);
        client.registerInterceptor(authInterceptor);
    }

    public void setBearerAuth(String token) {
        IClientInterceptor authInterceptor = new BearerTokenAuthInterceptor(token);
        client.registerInterceptor(authInterceptor);
    }

}
