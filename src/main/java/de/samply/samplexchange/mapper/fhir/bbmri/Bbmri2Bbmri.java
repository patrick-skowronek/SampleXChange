package de.samply.samplexchange.mapper.fhir.bbmri;

import de.samply.samplexchange.configuration.Configuration;
import de.samply.samplexchange.mapper.fhir.FhirInterface;
import de.samply.samplexchange.utils.fhir.FhirComponent;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Jump Mapping.
 * This mapping transfers everything from one blaze with bbmri to another
 */
@Service
@ConditionalOnExpression("'${tf.profile}'.equals('BBMRI2BBMRI')")
@Slf4j
public class Bbmri2Bbmri extends FhirInterface {

    FhirComponent fhirComponent;

    /**
     * Constructor.
     */
    @Autowired
    public Bbmri2Bbmri(Configuration configuration) throws Exception {
        super(configuration);
        fhirComponent = new FhirComponent(configuration);
    }

    /**
     * Transferring.
     */
    @Override
    public void transfer()
            throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        log.info("Running TransFAIR in BBMRI2BBMRI mode");
        if (!this.setup()) {
            log.info("Variables are not set, transfer not possible");
            return;
        }

        log.info("Setup complete");

        // TODO Collect Organization and Collection

        fhirComponent
                .getFhirExportInterface()
                .export(
                        fhirComponent.transferController.buildResources(
                                fhirComponent.transferController.fetchOrganizations(
                                        fhirComponent.getSourceFhirServer())));
        fhirComponent
                .getFhirExportInterface()
                .export(
                        fhirComponent.transferController.buildResources(
                                fhirComponent.transferController.fetchOrganizationAffiliation(
                                        fhirComponent.getSourceFhirServer())));

        int counter = 1;

        Set<String> patientRefs =
                fhirComponent.transferController.getSpecimenPatients(fhirComponent.getSourceFhirServer());

        log.info("Loaded " + patientRefs.size() + " Patients");

        for (String pid : patientRefs) {
            List<IBaseResource> patientResources = new ArrayList<>();
            log.debug("Loading data for patient " + pid);

            patientResources.add(
                    fhirComponent.transferController.fetchResource(
                            fhirComponent.getSourceFhirServer(), Patient.class, pid));
            patientResources.addAll(
                    fhirComponent.transferController.fetchPatientSpecimens(
                            fhirComponent.getSourceFhirServer(), pid));
            patientResources.addAll(
                    fhirComponent.transferController.fetchPatientObservation(
                            fhirComponent.getSourceFhirServer(), pid));

            patientResources.addAll(
                    fhirComponent.transferController.fetchPatientCondition(
                            fhirComponent.getSourceFhirServer(), pid));

            fhirComponent
                    .getFhirExportInterface()
                    .export(fhirComponent.transferController.buildResources(patientResources));
            log.info("Exported Resources " + counter++ + "/" + patientRefs.size());
        }
    }

    private boolean setup() {

        if (fhirComponent.configuration.getSourceServer().isBlank()) {
            return false;
        }
        return !fhirComponent.configuration.getFileExportPath().isBlank()
                || !fhirComponent.configuration.getTargetServer().isBlank();
    }
}
