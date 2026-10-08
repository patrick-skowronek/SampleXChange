package de.samply.samplexchange.repository.fhir;

import ca.uhn.fhir.context.FhirContext;
import de.samply.samplexchange.utils.fhir.FhirExportInterface;
import de.samply.samplexchange.utils.fhir.clients.FhirClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

@Getter
@Slf4j
public class FhirServerSaver extends FhirExportInterface {
    private final FhirClient client;

    public FhirServerSaver(FhirContext context, String targetServer, Boolean ssl)
            throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        this.client = new FhirClient(context, targetServer, ssl);
    }

    public Boolean export(Bundle bundle) {
        log.debug("Sending Resource to {}", getClient().getClient().getServerBase());
        client.getClient().transaction().withBundle(bundle).execute();
        return true;
    }

}
