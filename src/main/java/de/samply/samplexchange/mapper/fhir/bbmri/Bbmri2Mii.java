package de.samply.samplexchange.mapper.fhir.bbmri;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import de.samply.samplexchange.configuration.Configuration;
import de.samply.samplexchange.enums.ProfileFormats;
import de.samply.samplexchange.mapper.fhir.FhirInterface;
import de.samply.samplexchange.resources.*;
import de.samply.samplexchange.utils.fhir.FhirComponent;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Specimen;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Mapping for bbmri.de data and transformation to MII KDS.
 */
@Service
@ConditionalOnExpression("'${profile}'.equals('BBMRI2MII')")
@Slf4j
public class Bbmri2Mii extends FhirInterface {

    private static final String WITH_FORMAT_MII_KDS = " with format mii kds";
    private static final String WITH_FORMAT_BBMRI = " with format bbmri";

    ProfileFormats sourceFormat = ProfileFormats.BBMRI;
    ProfileFormats targetFormat = ProfileFormats.MII;

    FhirComponent fhirComponent;

    List<String> resources;

    /**
     * Constructor.
     */
    @Autowired
    public Bbmri2Mii(Configuration configuration) throws Exception {
        super(configuration);
        fhirComponent = new FhirComponent(configuration);
        // Initialize resources list with all supported resource types
        this.resources = List.of("Patient", "Condition", "Specimen", "Observation");
    }

    /**
     * Transferring.
     */
    @Override
    public void transfer() throws Exception {

        if (!this.setup()) {
            log.info("Variables are not set, transfer not possible");
            return;
        }

        log.info("Setup complete");

        IGenericClient sourceClient = fhirComponent.getSourceFhirServer();

        Set<String> patientIds =
                fhirComponent.transferController.fetchPatientIds(sourceClient);

        log.info("Loaded " + patientIds.size() + " Patients");

        int counter = 1;
        MetaMapping metaMapping =
                new MetaMapping(getConfiguration().getAppVersion(), "BBMRI2MII");

        for (String pid : patientIds) {
            List<IBaseResource> patientResources = new ArrayList<>();
            log.debug("Loading data for patient " + pid);


            if (resources.contains("Patient")) {
                PatientMapping ap = new PatientMapping();
                log.debug("Analysing patient " + pid + WITH_FORMAT_BBMRI);
                ap.fromBbmri(
                        fhirComponent.transferController.fetchResource(sourceClient, Patient.class, pid));
                try {
                    patientResources.add(metaMapping.tagResource(ap.toMii()));
                } catch (Exception e) {
                    log.error("Skipped patient " + pid + " with format bbmri due to " + Arrays.toString(
                            e.getStackTrace()));
                    continue;
                }
            }
            if (resources.contains("Condition")) {
                for (IBaseResource base :
                        fhirComponent.transferController.fetchPatientCondition(sourceClient, pid)) {
                    Condition condition = (Condition) base;
                    ConditionMapping conditionMapping = new ConditionMapping();

                    conditionMapping.fromBbmri(condition);
                    patientResources.add(metaMapping.tagResource(conditionMapping.toMii()));
                }
            }
            if (resources.contains("Specimen")) {
                for (Specimen specimen :
                        fhirComponent.transferController.fetchPatientSpecimens(sourceClient, pid)) {
                    SpecimenMapping transferSpecimenMapping = new SpecimenMapping();
                    log.debug("Analysing Specimen " + specimen.getId() + WITH_FORMAT_MII_KDS);
                    transferSpecimenMapping.fromBbmri(specimen);

                    log.debug("Export Specimen " + specimen.getId() + WITH_FORMAT_MII_KDS);
                    patientResources.add(metaMapping.tagResource(transferSpecimenMapping.toMii()));
                }
            }
            if (resources.contains("Observation")) {
                for (IBaseResource base :
                        fhirComponent.transferController.fetchPatientObservation(sourceClient, pid)) {
                    Observation observation = (Observation) base;

                    if (FhirProfileChecker.checkBbmriCauseOfDeath(observation)) {
                        CauseOfDeathMapping causeOfDeathMapping = new CauseOfDeathMapping();
                        causeOfDeathMapping.fromBbmri(observation);
                        log.debug("Analysing Cause of Death " + observation.getId() + WITH_FORMAT_BBMRI);

                        if (targetFormat == ProfileFormats.BBMRI) {
                            patientResources.add(metaMapping.tagResource(causeOfDeathMapping.toBbmri()));
                            log.debug("Analysing Cause of Death " + observation.getId() + WITH_FORMAT_BBMRI);
                        }
                    }
                }
            }

            fhirComponent
                    .getFhirExportInterface()
                    .export(fhirComponent.transferController.buildResources(patientResources));
            log.info("Exported Resources " + counter++ + "/" + patientIds.size());
        }
    }

    private boolean setup() {

        return !fhirComponent.configuration.getSourceServer().isBlank();
    }
}
