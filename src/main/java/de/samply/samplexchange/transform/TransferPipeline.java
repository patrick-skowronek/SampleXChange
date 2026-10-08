package de.samply.samplexchange.transform;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import de.samply.samplexchange.SampleXChangeException;
import de.samply.samplexchange.configuration.Configuration;
import de.samply.samplexchange.domain.BiobankDirectory;
import de.samply.samplexchange.domain.DonorRecord;
import de.samply.samplexchange.repository.fhir.FhirServerSaver;
import de.samply.samplexchange.resources.MetaMapping;
import de.samply.samplexchange.source.SourceSession;
import de.samply.samplexchange.terminology.Terminology;
import de.samply.samplexchange.utils.fhir.FhirComponent;
import de.samply.samplexchange.utils.fhir.FhirServerCheck;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class TransferPipeline {
    private final Configuration configuration;
    private final Terminology terminology;

    public TransferPipeline(Configuration configuration, Terminology terminology) {
        this.configuration = configuration;
        this.terminology = terminology;
    }

    public void run(Transformation transformation) throws Exception {
        log.info("Running {}", transformation.describe());

        FhirComponent fhir = new FhirComponent(configuration);
        IGenericClient sourceClient = fhir.getSourceFhirServer();
        // Contact both servers before reading anything, so a wrong URL or login stops the run at
        // once with a message that names the server.
        FhirServerCheck.ensureReachable(sourceClient, "source", "SOURCE");
        if (fhir.getFhirExportInterface() instanceof FhirServerSaver target) {
            FhirServerCheck.ensureReachable(target.getClient().getClient(), "target", "TARGET");
        }
        SourceSession session = new SourceSession(sourceClient, fhir.fhirTransfer);

        MetaMapping metaMapping =
                new MetaMapping(configuration.getAppVersion(), transformation.describe());

        // Biobanks and collections go first: samples reference their collection, and the target
        // rejects a reference to an Organization it does not hold yet.
        BiobankDirectory directory = transformation.reader().readDirectory(session);
        session = session.withDirectory(directory);
        int exported = 0;
        List<IBaseResource> organizations = new ArrayList<>();
        for (Resource resource : transformation.writer().write(directory)) {
            organizations.add(metaMapping.tagResource(resource));
        }
        if (!organizations.isEmpty()) {
            fhir.getFhirExportInterface().export(fhir.fhirTransfer.buildResources(organizations));
            exported += organizations.size();
            log.info("Exported {} biobank and collection resources", organizations.size());
        }

        Set<String> donorIds = transformation.reader().donorIds(session);
        log.info("Loaded {} donors", donorIds.size());

        int counter = 1;
        for (String donorId : donorIds) {
            log.debug("Loading data for donor {}", donorId);
            DonorRecord record;
            try {
                record = transformation.reader().read(session, donorId);
            } catch (SampleXChangeException | FhirClientConnectionException e) {
                // A setup problem or a lost server affects every donor, so stop instead of
                // skipping them one by one.
                throw e;
            } catch (Exception e) {
                log.error("Skipped donor {}: {}", donorId, e.getMessage());
                continue;
            }

            List<IBaseResource> tagged = new ArrayList<>();
            for (Resource resource : transformation.writer().write(record)) {
                tagged.add(metaMapping.tagResource(resource));
            }

            fhir.getFhirExportInterface()
                    .export(fhir.fhirTransfer.buildResources(tagged));
            exported += tagged.size();
            log.info("Exported donor {}/{}", counter++, donorIds.size());
        }

        log.info("{} finished, {} resources exported for {} donors",
                transformation.describe(), exported, donorIds.size());
        terminology.logSampleTypesWithNoMapping();
    }
}
