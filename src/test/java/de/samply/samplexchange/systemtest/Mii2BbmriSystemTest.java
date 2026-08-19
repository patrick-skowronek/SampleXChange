package de.samply.samplexchange.systemtest;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import de.samply.samplexchange.FileUtils;
import de.samply.samplexchange.configuration.AuthType;
import de.samply.samplexchange.configuration.Configuration;
import de.samply.samplexchange.mapper.fhir.mii.Mii2Bbmri;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Specimen;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end MII to bbmri.de transfer against two real FHIR servers.
 *
 * <p>Ported from TransFAIR (systemtest/Bbmri2MiiTest). Three things changed: the direction is
 * MII to bbmri.de, the fixture is uploaded with the HAPI client rather than a WebFlux WebClient
 * (this project has spring-web but not webflux), and the test actually asserts on the result --
 * the TransFAIR original only called transfer() and passed if nothing threw.
 *
 * <p>Skipped automatically when Docker is unavailable. Tagged "system" so it can be excluded
 * with {@code mvn test -DexcludedGroups=system}.
 */
@Testcontainers
@Tag("system")
@EnabledIf("dockerAvailable")
class Mii2BbmriSystemTest {

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

    private Configuration configuration;

    @BeforeEach
    void setUp() {
        Bundle fixture = (Bundle) CTX.newJsonParser()
                .parseResource(FileUtils.readResourceFile("mii.json"));
        CTX.newRestfulGenericClient(fhirBaseUrl(sourceBlaze))
                .transaction().withBundle(fixture).execute();

        configuration = new Configuration();
        configuration.setProfile("MII2BBMRI");
        configuration.setAppVersion("systemtest");
        configuration.setFileExportPath("");

        // AuthType has no "none" option, so an unauthenticated server has to be described as
        // BASIC with throwaway credentials. Blaze ignores the header. See the ADR open items.
        configuration.getSource().setUrl(fhirBaseUrl(sourceBlaze));
        configuration.getSource().setAuthType(AuthType.BASIC);
        configuration.getSource().setUsername("test");
        configuration.getSource().setPassword("test");

        configuration.getTarget().setUrl(fhirBaseUrl(targetBlaze));
        configuration.getTarget().setAuthType(AuthType.BASIC);
        configuration.getTarget().setUsername("test");
        configuration.getTarget().setPassword("test");
    }

    private <T extends Resource> List<T> readAll(IGenericClient client, Class<T> type) {
        Bundle bundle = client.search().forResource(type).returnBundle(Bundle.class).execute();
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
    void transfersPatientSpecimenAndConditionsToBbmriProfiles() throws Exception {
        new Mii2Bbmri(configuration).transfer();

        IGenericClient target = CTX.newRestfulGenericClient(fhirBaseUrl(targetBlaze));

        List<Patient> patients = readAll(target, Patient.class);
        List<Specimen> specimens = readAll(target, Specimen.class);
        List<Condition> conditions = readAll(target, Condition.class);
        List<Observation> observations = readAll(target, Observation.class);

        assertEquals(1, patients.size(), "one donor expected");
        assertEquals(1, specimens.size(), "one specimen expected");
        assertEquals(1, conditions.size(), "the Diagnose condition should become a bbmri Condition");
        assertEquals(1, observations.size(),
                "the Todesursache condition should become a bbmri CauseOfDeath Observation");

        assertTrue(hasProfile(specimens.get(0), "https://fhir.bbmri.de/StructureDefinition/Specimen"));
        assertTrue(hasProfile(conditions.get(0), "https://fhir.bbmri.de/StructureDefinition/Condition"));
        assertTrue(hasProfile(observations.get(0), "https://fhir.bbmri.de/StructureDefinition/CauseOfDeath"));
    }

    @Test
    void resourcesAreTaggedWithMappingProvenance() throws Exception {
        new Mii2Bbmri(configuration).transfer();

        IGenericClient target = CTX.newRestfulGenericClient(fhirBaseUrl(targetBlaze));
        Specimen specimen = readAll(target, Specimen.class).get(0);

        assertTrue(specimen.getMeta().getTag().stream()
                        .anyMatch(t -> t.getCode() != null && t.getCode().startsWith("SampleXChange systemtest")),
                "MetaMapping should tag every exported resource");
    }

    @Test
    void unknownSampleTypeFallsBackToDerivativeOther() throws Exception {
        // The fixture specimen is SNOMED 122555007, which the converter table does not know.
        new Mii2Bbmri(configuration).transfer();

        IGenericClient target = CTX.newRestfulGenericClient(fhirBaseUrl(targetBlaze));
        Specimen specimen = readAll(target, Specimen.class).get(0);

        assertEquals("derivative-other", specimen.getType().getCodingFirstRep().getCode());
        assertEquals("https://fhir.bbmri.de/CodeSystem/SampleMaterialType",
                specimen.getType().getCodingFirstRep().getSystem());
    }

    @Test
    void icd10GmCauseOfDeathLosesItsCode() throws Exception {
        // KNOWN GAP: the fixture codes the cause of death as ICD-10-GM (bfarm), but
        // CauseOfDeathMapping.fromMii only reads http://hl7.org/fhir/sid/icd-10. The Observation
        // is therefore exported without a value. Real MII data uses ICD-10-GM.
        new Mii2Bbmri(configuration).transfer();

        IGenericClient target = CTX.newRestfulGenericClient(fhirBaseUrl(targetBlaze));
        Observation causeOfDeath = readAll(target, Observation.class).get(0);

        assertFalse(causeOfDeath.hasValueCodeableConcept(),
                "cause of death arrives without an ICD code because only plain ICD-10 is read");
    }

    @Test
    void snomedOnlyDiagnosisLosesItsCode() throws Exception {
        // KNOWN GAP: the Diagnose condition is coded in SNOMED CT only, which
        // ConditionMapping.fromMii logs as unsupported, so no code is exported.
        new Mii2Bbmri(configuration).transfer();

        IGenericClient target = CTX.newRestfulGenericClient(fhirBaseUrl(targetBlaze));
        Condition condition = readAll(target, Condition.class).get(0);

        assertFalse(condition.hasCode(), "SNOMED-only diagnoses are dropped");
    }
}
