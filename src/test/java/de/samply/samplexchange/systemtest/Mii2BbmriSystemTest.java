package de.samply.samplexchange.systemtest;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import de.samply.samplexchange.FileUtils;
import de.samply.samplexchange.configuration.AuthType;
import de.samply.samplexchange.configuration.Configuration;
import de.samply.samplexchange.configuration.SourceFormat;
import de.samply.samplexchange.configuration.TargetFormat;
import de.samply.samplexchange.source.mii.Mii2025Reader;
import de.samply.samplexchange.source.mii.MiiSpecimenHierarchyResolver;
import de.samply.samplexchange.source.mii.SpecimenToSampleMapper;
import de.samply.samplexchange.target.bbmri.BbmriDeWriter;
import de.samply.samplexchange.terminology.Terminology;
import de.samply.samplexchange.transform.TransferPipeline;
import de.samply.samplexchange.transform.Transformation;
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
        configuration.setSourceFormat(SourceFormat.MII_2025.name());
        configuration.setTargetFormat(TargetFormat.BBMRI_DE.name());
        configuration.setAppVersion("systemtest");
        configuration.setFileExportPath("");

        // Blaze runs without authentication in these tests.
        configuration.getSource().setUrl(fhirBaseUrl(sourceBlaze));
        configuration.getSource().setAuthType(AuthType.NONE);

        configuration.getTarget().setUrl(fhirBaseUrl(targetBlaze));
        configuration.getTarget().setAuthType(AuthType.NONE);
    }

    /** Wires the transformation by hand: every part is an ordinary object. */
    private void runTransfer() throws Exception {
        Terminology terminology = new Terminology();
        Transformation transformation = new Transformation(
                new Mii2025Reader(new MiiSpecimenHierarchyResolver(), new SpecimenToSampleMapper()),
                new BbmriDeWriter(terminology));
        new TransferPipeline(configuration, terminology).run(transformation);
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
        runTransfer();

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
        runTransfer();

        IGenericClient target = CTX.newRestfulGenericClient(fhirBaseUrl(targetBlaze));
        Specimen specimen = readAll(target, Specimen.class).get(0);

        assertTrue(specimen.getMeta().getTag().stream()
                        .anyMatch(t -> t.getCode() != null && t.getCode().startsWith("SampleXChange systemtest")),
                "MetaMapping should tag every exported resource");
    }

    @Test
    void venousBloodBecomesWholeBlood() throws Exception {
        // The fixture specimen is SNOMED 122555007 |Venous blood specimen|. Before the table
        // covered every descendant of 123038009 it fell back to derivative-other.
        runTransfer();

        IGenericClient target = CTX.newRestfulGenericClient(fhirBaseUrl(targetBlaze));
        Specimen specimen = readAll(target, Specimen.class).get(0);

        assertEquals("whole-blood", specimen.getType().getCodingFirstRep().getCode());
        assertEquals("https://fhir.bbmri.de/CodeSystem/SampleMaterialType",
                specimen.getType().getCodingFirstRep().getSystem());
    }

    @Test
    void icd10GmCauseOfDeathKeepsItsCode() throws Exception {
        // The fixture codes cause of death in ICD-10-GM (bfarm). The reader takes the coding
        // whatever its system, so the code survives instead of the Observation arriving empty.
        runTransfer();

        IGenericClient target = CTX.newRestfulGenericClient(fhirBaseUrl(targetBlaze));
        Observation causeOfDeath = readAll(target, Observation.class).get(0);

        assertTrue(causeOfDeath.hasValueCodeableConcept(), "cause of death should carry its code");
        assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm",
                causeOfDeath.getValueCodeableConcept().getCodingFirstRep().getSystem());
        assertEquals("R96.1",
                causeOfDeath.getValueCodeableConcept().getCodingFirstRep().getCode());
    }

    @Test
    void snomedOnlyDiagnosisKeepsItsCode() throws Exception {
        // The Diagnose condition is coded in SNOMED CT only. It now reaches the target with that
        // coding rather than being dropped. bbmri.de expects ICD-10, so a profile validator will
        // flag the system: the gap is visible instead of silent.
        runTransfer();

        IGenericClient target = CTX.newRestfulGenericClient(fhirBaseUrl(targetBlaze));
        Condition condition = readAll(target, Condition.class).get(0);

        assertTrue(condition.hasCode(), "SNOMED-only diagnoses should not be dropped");
        assertEquals("http://snomed.info/sct", condition.getCode().getCodingFirstRep().getSystem());
        assertEquals("195506001", condition.getCode().getCodingFirstRep().getCode());
    }

    @Test
    void fastingStatusReachesTheTarget() throws Exception {
        // Read from MII and previously never written.
        runTransfer();

        IGenericClient target = CTX.newRestfulGenericClient(fhirBaseUrl(targetBlaze));
        Specimen specimen = readAll(target, Specimen.class).get(0);

        assertTrue(specimen.getCollection().hasFastingStatusCodeableConcept(),
                "fasting status should be exported");
        assertEquals("NG", specimen.getCollection().getFastingStatusCodeableConcept()
                .getCodingFirstRep().getCode());
    }
}
