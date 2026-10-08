package de.samply.samplexchange.source;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import de.samply.samplexchange.domain.BiobankDirectory;
import de.samply.samplexchange.utils.fhir.FhirTransfer;

/**
 * What a reader needs for one run. {@code directory} holds the biobanks and collections read at
 * the start of the run, so a sample can only name a collection that is exported too.
 */
public record SourceSession(IGenericClient client, FhirTransfer transfer, BiobankDirectory directory) {
    public SourceSession(IGenericClient client, FhirTransfer transfer) {
        this(client, transfer, BiobankDirectory.empty());
    }

    public SourceSession withDirectory(BiobankDirectory directory) {
        return new SourceSession(client, transfer, directory);
    }
}
