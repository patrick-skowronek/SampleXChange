package de.samply.samplexchange.systemtest;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import de.samply.samplexchange.FileUtils;
import de.samply.samplexchange.configuration.AuthType;
import de.samply.samplexchange.configuration.Configuration;
import de.samply.samplexchange.configuration.SourceFormat;
import de.samply.samplexchange.configuration.TargetFormat;
import de.samply.samplexchange.source.mii.Mii2025Reader;
import de.samply.samplexchange.source.mii.Mii2026Reader;
import de.samply.samplexchange.source.mii.MiiSpecimenHierarchyResolver;
import de.samply.samplexchange.source.mii.SpecimenToSampleMapper;
import de.samply.samplexchange.target.bbmri.BbmriDeWriter;
import de.samply.samplexchange.target.miabis.MiabisV3Writer;
import de.samply.samplexchange.terminology.Terminology;
import de.samply.samplexchange.transform.TransferPipeline;
import de.samply.samplexchange.transform.Transformation;
import de.samply.samplexchange.transform.TransformationRegistry;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Specimen;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three transformations that {@link Mii2BbmriSystemTest} does not cover, each end to end
 * against a pair of Blaze servers.
 *
 * <p>Containers are per test rather than shared because each pair writes different profiles to
 * the same resource ids, and each source needs a clean server for its own fixture.
 */
@Testcontainers
@Tag("system")
@EnabledIf("dockerAvailable")
class TransformationSystemTest {

    private static final String BLAZE_IMAGE = "samply/blaze:1.10.1";
    private static final FhirContext CTX = FhirContext.forR4();

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    private static GenericContainer<?> blaze() {
        return new GenericContainer<>(BLAZE_IMAGE)
                .withEnv("LOG_LEVEL", "warn")
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/health").forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(4)));
    }

    @Container
    @SuppressWarnings("resource")
    private final GenericContainer<?> sourceBlaze = blaze();

    @Container
    @SuppressWarnings("resource")
    private final GenericContainer<?> targetBlaze = blaze();

    private static String fhirBaseUrl(GenericContainer<?> container) {
        return "http://%s:%d/fhir".formatted(container.getHost(), container.getFirstMappedPort());
    }

    private Configuration configuration() {
        Configuration configuration = new Configuration();
        configuration.setAppVersion("systemtest");
        configuration.setFileExportPath("");
        configuration.getSource().setUrl(fhirBaseUrl(sourceBlaze));
        configuration.getSource().setAuthType(AuthType.NONE);
        configuration.getTarget().setUrl(fhirBaseUrl(targetBlaze));
        configuration.getTarget().setAuthType(AuthType.NONE);
        return configuration;
    }

    private void upload(String fixture) {
        Bundle bundle = (Bundle) CTX.newJsonParser().parseResource(FileUtils.readResourceFile(fixture));
        CTX.newRestfulGenericClient(fhirBaseUrl(sourceBlaze)).transaction().withBundle(bundle).execute();
    }

    /** Resolves through the real registry, so the supported pairs are exercised too. */
    private void run(String fixture, SourceFormat source, TargetFormat target) throws Exception {
        upload(fixture);
        Configuration configuration = configuration();
        Terminology terminology = new Terminology();
        MiiSpecimenHierarchyResolver resolver = new MiiSpecimenHierarchyResolver();
        SpecimenToSampleMapper specimenToSampleMapper = new SpecimenToSampleMapper();

        TransformationRegistry registry = new TransformationRegistry(
                List.of(new Mii2025Reader(resolver, specimenToSampleMapper), new Mii2026Reader(resolver, specimenToSampleMapper)),
                List.of(new BbmriDeWriter(terminology), new MiabisV3Writer(terminology)));

        Transformation transformation = registry.findTransformationFor(source, target);
        new TransferPipeline(configuration, terminology).run(transformation);
    }

    private <T extends Resource> List<T> readAll(Class<T> type) {
        IGenericClient target = CTX.newRestfulGenericClient(fhirBaseUrl(targetBlaze));
        Bundle bundle = target.search().forResource(type).returnBundle(Bundle.class).execute();
        return bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    private static boolean hasProfile(Resource resource, String profile) {
        return resource.getMeta().getProfile().stream()
                .anyMatch(p -> p.asStringValue().equals(profile));
    }

    @Test
    void mii2025ToMiabis() throws Exception {
        run("mii.json", SourceFormat.MII_2025, TargetFormat.MIABIS_V3);

        List<Specimen> samples = readAll(Specimen.class);
        List<Patient> donors = readAll(Patient.class);
        List<Condition> conditions = readAll(Condition.class);

        assertEquals(1, samples.size());
        assertEquals(1, donors.size());
        assertTrue(hasProfile(samples.get(0), MiabisV3Writer.SAMPLE_PROFILE));
        assertTrue(hasProfile(donors.get(0), MiabisV3Writer.DONOR_PROFILE));

        // MIABIS requires an identifier on both.
        assertEquals("MusterprobeFluessig", samples.get(0).getIdentifierFirstRep().getValue());
        assertTrue(donors.get(0).hasIdentifier());

        // The diagnosis and the cause of death are both Conditions in MIABIS, and no Observation
        // is written because miabis-observation is the sample-linked diagnosis.
        assertEquals(2, conditions.size());
        assertEquals(0, readAll(Observation.class).size());
    }

    @Test
    void mii2026ToBbmri() throws Exception {
        run("mii2026-bundle.json", SourceFormat.MII_2026, TargetFormat.BBMRI_DE);

        List<Specimen> specimens = readAll(Specimen.class);

        // Four declared aliquot groups. The primary sample and the two aliquots are not samples in
        // bbmri.de terms, and the organoid is filtered out by its profile.
        assertEquals(4, specimens.size());
        specimens.forEach(s -> assertTrue(hasProfile(s, BbmriDeWriter.SPECIMEN_PROFILE)));
        assertEquals(List.of("AliquotgruppeBuffyCoat", "AliquotgruppeDNA", "AliquotgruppePlasma",
                        "MusterprobeGewebe"),
                specimens.stream().map(s -> s.getIdElement().getIdPart()).sorted().toList());
        assertEquals(1, readAll(Patient.class).size());

        // The biobank and collection are written first, so Blaze accepts the custodian references.
        assertEquals(List.of("BiobankMusterstadt", "Mustersammlung"), readAll(Organization.class).stream()
                .map(o -> o.getIdElement().getIdPart()).sorted().toList());
        specimens.forEach(s -> assertEquals("Organization/Mustersammlung",
                ((Reference) s.getExtensionByUrl(BbmriDeWriter.CUSTODIAN_EXTENSION).getValue()).getReference(),
                s.getIdElement().getIdPart()));
    }

    @Test
    void mii2026ToMiabis() throws Exception {
        run("mii2026-bundle.json", SourceFormat.MII_2026, TargetFormat.MIABIS_V3);

        List<Specimen> samples = readAll(Specimen.class);

        // Five, not four: MIABIS has codes for cell lines and organoids, so OrganoidLunge is kept.
        // bbmri.de has none, which is why the run above exports only four.
        assertEquals(5, samples.size());
        samples.forEach(s -> assertTrue(hasProfile(s, MiabisV3Writer.SAMPLE_PROFILE)));
        Specimen organoid = samples.stream()
                .filter(s -> s.getIdElement().getIdPart().equals("OrganoidLunge"))
                .findFirst().orElseThrow();
        assertEquals("Organoid", organoid.getType().getCodingFirstRep().getCode());
        samples.forEach(s -> assertTrue(s.hasIdentifier(), s.getId() + " needs an identifier"));

        // The plasma aliquot group stored at -85 to -75, which is the MIABIS -60to-85 bucket.
        Specimen plasma = samples.stream()
                .filter(s -> s.getIdElement().getIdPart().equals("AliquotgruppePlasma"))
                .findFirst().orElseThrow();
        assertEquals("-60to-85", ((org.hl7.fhir.r4.model.CodeableConcept)
                plasma.getProcessingFirstRep().getExtensionFirstRep().getValue())
                .getCodingFirstRep().getCode());
    }
}
