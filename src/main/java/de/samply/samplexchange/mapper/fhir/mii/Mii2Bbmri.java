package de.samply.samplexchange.mapper.fhir.mii;


import ca.uhn.fhir.rest.client.api.IGenericClient;
import de.samply.samplexchange.configuration.Configuration;
import de.samply.samplexchange.mapper.fhir.FhirInterface;
import de.samply.samplexchange.resources.*;
import de.samply.samplexchange.utils.fhir.FhirComponent;
import jakarta.annotation.PostConstruct;
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

import static de.samply.samplexchange.resources.FhirProfileChecker.checkMmiCondition;
import static java.lang.System.exit;

/**
 * Mapping for MII KDS data and transformation to bbmri.de.
 */
@Service
@ConditionalOnExpression("'${profile}'.equals('MII2BBMRI')")
@Slf4j
public class Mii2Bbmri extends FhirInterface {

    FhirComponent fhirComponent;

    /**
     * Constructor.
     */
    @Autowired
    public Mii2Bbmri(Configuration configuration) throws Exception {
        super(configuration);

        fhirComponent = new FhirComponent(configuration);
    }

    /**
     * Transferring.
     */
    @PostConstruct
    public void transfer() throws Exception {
        log.info("Running MII2BMMRI");
        this.setup();

        IGenericClient sourceClient = fhirComponent.getSourceFhirServer();

        Set<String> patientIds =
                fhirComponent.transferController.fetchPatientIds(
                        sourceClient);

        log.info("Loaded {} Patients", patientIds.size());

        int counter = 1;
        MetaMapping metaMapping = new MetaMapping(getConfiguration().getAppVersion(), "MII2BBMRI");

        for (String pid : patientIds) {
            List<IBaseResource> patientResources = new ArrayList<>();
            log.debug("Loading data for patient " + pid);


            PatientMapping ap = new PatientMapping();
            log.debug("Analysing patient " + pid + " with format MII KDS");
            ap.fromMii(
                    fhirComponent.transferController.fetchResource(sourceClient, Patient.class, pid));
            try {
                patientResources.add(metaMapping.tagResource(ap.toBbmri()));
            } catch (Exception e) {
                log.error("Skipped patient {} with format MII KDS due to {}", pid, Arrays.toString(
                        e.getStackTrace()));
                continue;
            }

            for (Specimen specimen :
                    fhirComponent.transferController.fetchPatientSpecimens(sourceClient, pid)) {
                SpecimenMapping transferSpecimenMapping = new SpecimenMapping();
                log.debug("Analysing Specimen {} with format bbmri.de", specimen.getId());
                transferSpecimenMapping.fromMii(specimen);
                Specimen specimenl = transferSpecimenMapping.toBbmri();
                if (specimenl != null) {
                    patientResources.add(metaMapping.tagResource(specimenl));
                }
            }

            for (IBaseResource base :
                    fhirComponent.transferController.fetchPatientCondition(sourceClient, pid)) {
                Condition condition = (Condition) base;

                if (FhirProfileChecker.checkMiiCauseOfDeath(condition)) {
                    CauseOfDeathMapping causeOfDeathMapping = new CauseOfDeathMapping();
                    causeOfDeathMapping.fromMii(condition);
                    log.debug("Analysing Cause of Death {} with format mii", condition.getId());

                    Observation causeOfDeath = causeOfDeathMapping.toBbmri();
                    if (causeOfDeath != null && !causeOfDeath.getId().isBlank()) {
                        patientResources.add(metaMapping.tagResource(causeOfDeath));
                    }
                } else if (checkMmiCondition(condition)) {
                    ConditionMapping conditionMapping = new ConditionMapping();
                    conditionMapping.fromMii(condition);
                    patientResources.add(metaMapping.tagResource(conditionMapping.toBbmri()));
                }
            }


            fhirComponent
                    .getFhirExportInterface()
                    .export(fhirComponent.transferController.buildResources(patientResources));
            log.info("Exported Resources {}/{}", counter++, patientIds.size());
        }
    }

    private void setup() {

        if (this.getConfiguration().getSourceServer().isBlank()) {
            log.error("Source FHIR server is blank");
            exit(1);
        }
    }
}
