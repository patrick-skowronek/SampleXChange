package de.samply.samplexchange.utils.fhir;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import de.samply.samplexchange.SampleXChangeException;
import org.hl7.fhir.r4.model.CapabilityStatement;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/** Contacts a FHIR server once at the start, so a wrong URL or login fails with a clear message. */
public final class FhirServerCheck {

    private FhirServerCheck() {
    }

    /**
     * Reads the server's capability statement and turns any failure into a
     * {@link SampleXChangeException} that names the server and the variable to check.
     *
     * @param role     "source" or "target", for the message
     * @param prefix   the environment variable prefix, SOURCE or TARGET
     */
    public static void ensureReachable(IGenericClient client, String role, String prefix) {
        String url = client.getServerBase();
        try {
            client.capabilities().ofType(CapabilityStatement.class).execute();
        } catch (FhirClientConnectionException e) {
            throw new SampleXChangeException("Cannot connect to the %s FHIR server at %s: %s. Check %s_URL and that the server is running and reachable from where SampleXChange runs."
                    .formatted(role, url, describeConnectionFailure(e, prefix), prefix), e);
        } catch (AuthenticationException e) {
            throw new SampleXChangeException("The %s FHIR server at %s rejected the login (HTTP 401). Check %s_AUTH_TYPE and the credentials that go with it."
                    .formatted(role, url, prefix), e);
        } catch (ForbiddenOperationException e) {
            throw new SampleXChangeException("The %s FHIR server at %s refused access (HTTP 403). The configured user may lack the rights SampleXChange needs."
                    .formatted(role, url), e);
        } catch (BaseServerResponseException e) {
            throw new SampleXChangeException("The %s FHIR server at %s answered with HTTP %d: %s"
                    .formatted(role, url, e.getStatusCode(), e.getMessage()), e);
        }
    }

    /**
     * A short reason for a failed connection, from the innermost cause.
     *
     * @param prefix SOURCE or TARGET, or null when it is not known which server failed
     */
    public static String describeConnectionFailure(Throwable failure, String prefix) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
            if (cause instanceof UnknownHostException) {
                return "the host name " + cause.getMessage().split(":")[0] + " cannot be resolved";
            }
            if (cause instanceof ConnectException) {
                return "the connection was refused";
            }
            if (cause instanceof SocketTimeoutException) {
                return "the connection timed out";
            }
            if (cause instanceof SSLException) {
                String variable = prefix == null
                        ? "SOURCE_DISABLE_SSL or TARGET_DISABLE_SSL" : prefix + "_DISABLE_SSL";
                return "the TLS handshake failed (" + cause.getMessage() + "). For a self-signed "
                        + "certificate, set " + variable + " to true";
            }
        }
        return cause.getMessage();
    }
}
