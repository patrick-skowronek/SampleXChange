package de.samply.samplexchange.utils.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import de.samply.samplexchange.SampleXChangeException;
import de.samply.samplexchange.configuration.FhirServerUrl;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Specimen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class FhirTransfer {
    /** Every request this class makes goes to the source server. */
    private static final String SERVER = "source";

    FhirContext ctx;
    private final RetryPolicy retry;

    public FhirTransfer(FhirContext ctx) {
        this(ctx, RetryPolicy.none());
    }

    public FhirTransfer(FhirContext ctx, RetryPolicy retry) {
        this.ctx = ctx;
        this.retry = retry;
    }

    /**
     * Follows the next-page link, but only to the configured server. A server that reports a
     * different base URL than set in the environment fails the application.
     */
    Bundle nextPage(IGenericClient client, Bundle bundle) {
        String next = bundle.getLink(IBaseBundle.LINK_NEXT).getUrl();
        if (!FhirServerUrl.isOnServer(client.getServerBase(), next)) {
            throw new SampleXChangeException(
                    ("The FHIR server at %s returned a link to its next page at %s, which is a different "
                            + "address. Make the server report the address SampleXChange is configured "
                            + "with (for Blaze, set BASE_URL to the URL without /fhir), or configure "
                            + "SampleXChange with the address the server reports.")
                            .formatted(client.getServerBase(), next));
        }
        return retry.call(SERVER, () -> client.loadPage().next(bundle).execute());
    }

    /**
     * Only the subject is requested
     */
    public Set<String> fetchDonorReferencesFromSpecimens(IGenericClient client) {
        Set<String> references = new LinkedHashSet<>();
        Bundle page = retry.call(SERVER, () -> client.search()
                .forResource(Specimen.class)
                .elementsSubset("subject")
                .returnBundle(Bundle.class)
                .count(500)
                .execute());
        while (true) {
            for (Specimen specimen : resourcesOfType(page, Specimen.class)) {
                if (specimen.getSubject().hasReference()) {
                    references.add(specimen.getSubject().getReference());
                }
            }
            if (page.getLink(IBaseBundle.LINK_NEXT) == null) {
                break;
            }
            page = nextPage(client, page);
        }
        log.info("Found {} donor references on the Specimens in the source", references.size());
        return references;
    }

    public <T extends IBaseResource> T fetchResource(
            IGenericClient client, Class<T> resourceType, String id) {
        log.debug("Reading Resource {} with ID {} from {}", resourceType.getName(), id, client.getServerBase());
        return retry.call(SERVER, () -> client.read().resource(resourceType).withId(id).execute());
    }

    public List<Specimen> fetchSpecimensOfDonor(IGenericClient client, String patientId) {
        Bundle firstPage = retry.call(SERVER, () -> client.search()
                .forResource(Specimen.class)
                .where(Specimen.SUBJECT.hasId(patientId))
                .returnBundle(Bundle.class)
                .execute());
        return allPages(client, firstPage, Specimen.class);
    }

    public List<IBaseResource> fetchOrganizations(IGenericClient client) {
        Bundle firstPage = retry.call(SERVER, () -> client.search()
                .forResource(Organization.class)
                .returnBundle(Bundle.class)
                .execute());
        return new ArrayList<>(allPages(client, firstPage, Organization.class));
    }

    public List<IBaseResource> fetchConditionsOfDonor(IGenericClient client, String patientId) {
        Bundle firstPage = retry.call(SERVER, () -> client.search()
                .forResource(Condition.class)
                .where(Condition.SUBJECT.hasId(patientId))
                .returnBundle(Bundle.class)
                .execute());
        return new ArrayList<>(allPages(client, firstPage, Condition.class));
    }

    /** The resources of {@code type} on the first page and every page after it. */
    private <T extends IBaseResource> List<T> allPages(IGenericClient client, Bundle firstPage, Class<T> type) {
        List<T> resources = new ArrayList<>(resourcesOfType(firstPage, type));
        Bundle page = firstPage;
        while (page.getLink(IBaseBundle.LINK_NEXT) != null) {
            page = nextPage(client, page);
            resources.addAll(resourcesOfType(page, type));
        }
        return resources;
    }

    /**
     * The entries of {@code page} that are of {@code type}. Some servers, HAPI among them, add an
     * OperationOutcome to search results to carry a warning
     */
    static <T extends IBaseResource> List<T> resourcesOfType(Bundle page, Class<T> type) {
        List<T> matching = new ArrayList<>();
        for (Bundle.BundleEntryComponent entry : page.getEntry()) {
            Resource resource = entry.getResource();
            if (resource == null) {
                continue;
            }
            if (type.isInstance(resource)) {
                matching.add(type.cast(resource));
            } else if (resource instanceof OperationOutcome outcome) {
                log.warn("The source server added a note to a {} search: {}", type.getSimpleName(), describe(outcome));
            } else {
                log.warn("Ignoring a {} in the {} search results from the source server",
                        resource.fhirType(), type.getSimpleName());
            }
        }
        return matching;
    }

    private static String describe(OperationOutcome outcome) {
        return outcome.getIssue().stream()
                .map(issue -> issue.hasDiagnostics() ? issue.getDiagnostics() : issue.getDetails().getText())
                .collect(Collectors.joining("; "));
    }

    public Bundle buildResources(List<IBaseResource> resources) {
        Bundle bundleOut = new Bundle();
        bundleOut.setId(String.valueOf(UUID.randomUUID()));
        bundleOut.setType(Bundle.BundleType.TRANSACTION);

        try {
            for (IBaseResource resource : resources) {
                bundleOut
                        .addEntry()
                        .setFullUrl(resource.getIdElement().getValue())
                        .setResource((Resource) resource)
                        .getRequest()
                        .setUrl(
                                ((Resource) resource).getResourceType() + "/" + resource.getIdElement().getIdPart())
                        .setMethod(HTTPVerb.PUT);
            }

        } catch (Error e) {
            log.error(e.getMessage());
        }

        return bundleOut;
    }
}
