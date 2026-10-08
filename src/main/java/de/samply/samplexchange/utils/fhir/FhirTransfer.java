package de.samply.samplexchange.utils.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.util.BundleUtil;
import de.samply.samplexchange.SampleXChangeException;
import de.samply.samplexchange.configuration.FhirServerUrl;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;

import java.util.*;

@Slf4j
public class FhirTransfer {
    FhirContext ctx;

    public FhirTransfer(FhirContext ctx) {
        this.ctx = ctx;
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
        return client.loadPage().next(bundle).execute();
    }

    private List<IBaseResource> fetchAllSpecimens(IGenericClient client) {
        List<IBaseResource> resourceList = new ArrayList<>();

        Bundle bundle =
                client.search().forResource(Specimen.class).returnBundle(Bundle.class).count(500).execute();
        resourceList.addAll(BundleUtil.toListOfResources(ctx, bundle));

        while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
            bundle = nextPage(client, bundle);
            resourceList.addAll(BundleUtil.toListOfResources(ctx, bundle));
            log.debug("Fetching next page of Specimen");
        }
        log.info("Loaded " + resourceList.size() + " Specimen Resources from source");

        return resourceList;
    }

    public Set<String> fetchDonorReferencesFromSpecimens(IGenericClient client) {
        return this.readDonorReferencesOfAllSpecimens(client);
    }

    private <T extends IBaseResource> List<T> fetchResources(
            Class<T> resourceType, IGenericClient client) {
        Bundle bundle =
                client.search().forResource(resourceType).returnBundle(Bundle.class).count(500).execute();
        List<T> resourceList =
                new ArrayList<>(BundleUtil.toListOfResourcesOfType(ctx, bundle, resourceType));

        while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
            bundle = nextPage(client, bundle);
            resourceList.addAll(BundleUtil.toListOfResourcesOfType(ctx, bundle, resourceType));
            log.debug("Fetching next page of " + resourceType.getName());
        }
        log.info(
                "Loaded " + resourceList.size() + " " + resourceType.getName() + " Resources from source");

        return resourceList;
    }

    public <T extends IBaseResource> T fetchResource(
            IGenericClient client, Class<T> resourceType, String id) {
        log.debug(
                "Reading Resource "
                        + resourceType.getName()
                        + " with ID "
                        + id
                        + " from "
                        + client.getServerBase());
        return client.read().resource(resourceType).withId(id).execute();
    }

    public List<Specimen> fetchSpecimensOfDonor(IGenericClient client, String patientId) {
        List<IBaseResource> resourceList = new ArrayList<>();

        Bundle bundle =
                client
                        .search()
                        .forResource(Specimen.class)
                        .where(Specimen.SUBJECT.hasId(patientId))
                        .returnBundle(Bundle.class)
                        .execute();

        resourceList.addAll(BundleUtil.toListOfResources(ctx, bundle));

        while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
            bundle = nextPage(client, bundle);
            resourceList.addAll(BundleUtil.toListOfResources(ctx, bundle));
        }

        List<Specimen> specimens = new ArrayList<>();

        for (IBaseResource resource : resourceList) {
            specimens.add((Specimen) resource);
        }

        return specimens;
    }

    public List<IBaseResource> fetchOrganizations(IGenericClient client) {
        List<IBaseResource> resourceList = new ArrayList<>();

        Bundle bundle =
                client.search().forResource(Organization.class).returnBundle(Bundle.class).execute();

        resourceList.addAll(BundleUtil.toListOfResources(ctx, bundle));

        while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
            bundle = nextPage(client, bundle);
            resourceList.addAll(BundleUtil.toListOfResources(ctx, bundle));
        }
        return resourceList;
    }

    public List<IBaseResource> fetchOrganizationAffiliation(IGenericClient client) {
        List<IBaseResource> resourceList = new ArrayList<>();

        Bundle bundle =
                client
                        .search()
                        .forResource(OrganizationAffiliation.class)
                        .returnBundle(Bundle.class)
                        .execute();

        resourceList.addAll(BundleUtil.toListOfResources(ctx, bundle));

        while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
            bundle = nextPage(client, bundle);
            resourceList.addAll(BundleUtil.toListOfResources(ctx, bundle));
        }
        return resourceList;
    }

    public List<IBaseResource> fetchObservationsOfDonor(IGenericClient client, String patientId) {
        List<IBaseResource> resourceList = new ArrayList<>();

        Bundle bundle =
                client
                        .search()
                        .forResource(Observation.class)
                        .where(Observation.SUBJECT.hasId(patientId))
                        .returnBundle(Bundle.class)
                        .execute();

        resourceList.addAll(BundleUtil.toListOfResources(ctx, bundle));

        while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
            bundle = nextPage(client, bundle);
            resourceList.addAll(BundleUtil.toListOfResources(ctx, bundle));
        }

        return resourceList;
    }

    public List<IBaseResource> fetchConditionsOfDonor(IGenericClient client, String patientId) {
        Bundle bundle =
                client
                        .search()
                        .forResource(Condition.class)
                        .where(Condition.SUBJECT.hasId(patientId))
                        .returnBundle(Bundle.class)
                        .execute();

        List<IBaseResource> resourceList = new ArrayList<>(BundleUtil.toListOfResources(ctx, bundle));

        while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
            bundle = nextPage(client, bundle);
            resourceList.addAll(BundleUtil.toListOfResources(ctx, bundle));
        }

        return resourceList;
    }

    public Set<String> readDonorReferencesOfAllSpecimens(IGenericClient sourceClient) {
        List<IBaseResource> specimens = fetchAllSpecimens(sourceClient);
        HashSet<String> patientRefs = new HashSet<>();
        for (IBaseResource specimen : specimens) {
            Specimen s = (Specimen) specimen;
            patientRefs.add(s.getSubject().getReference());
        }
        return patientRefs;
    }

    public Set<String> getSpecimenIds(IGenericClient sourceClient) {
        List<IBaseResource> specimens = fetchAllSpecimens(sourceClient);
        HashSet<String> specimenRefs = new HashSet<>();
        for (IBaseResource specimen : specimens) {
            Specimen s = (Specimen) specimen;
            specimenRefs.add(s.getId());
        }
        return specimenRefs;
    }

    private HashSet<String> getPatientRefs(IGenericClient sourceClient) {
        List<Patient> patients = fetchResources(Patient.class, sourceClient);
        HashSet<String> patientRefs = new HashSet<>();

        for (IBaseResource patient : patients) {
            patientRefs.add(patient.getIdElement().getValue());
        }
        return patientRefs;
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
