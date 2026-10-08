package de.samply.samplexchange.source.mii;

import de.samply.samplexchange.domain.BiobankDirectory;
import de.samply.samplexchange.domain.CauseOfDeath;
import de.samply.samplexchange.domain.CodedValue;
import de.samply.samplexchange.domain.Diagnosis;
import de.samply.samplexchange.domain.Donor;
import de.samply.samplexchange.domain.DonorRecord;
import de.samply.samplexchange.domain.Sample;
import de.samply.samplexchange.resources.FhirProfileChecker;
import de.samply.samplexchange.source.SourceReader;
import de.samply.samplexchange.source.SourceSession;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Specimen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public abstract class MiiSourceReader implements SourceReader {
    private final SpecimenHierarchyResolver resolver;
    private final SpecimenToSampleMapper specimenToSampleMapper;
    private final Set<String> unknownCollectionsWarnedAbout = ConcurrentHashMap.newKeySet();

    protected MiiSourceReader(SpecimenHierarchyResolver resolver, SpecimenToSampleMapper specimenToSampleMapper) {
        this.resolver = resolver;
        this.specimenToSampleMapper = specimenToSampleMapper;
    }

    @Override
    public BiobankDirectory readDirectory(SourceSession session) {
        List<Organization> organizations = session.transfer().fetchOrganizations(session.client()).stream()
                .filter(Organization.class::isInstance)
                .map(Organization.class::cast)
                .toList();
        return MiiOrganizationReader.toDirectory(organizations);
    }

    @Override
    public Set<String> donorIds(SourceSession session) {
        Set<String> ids = new LinkedHashSet<>();
        for (String reference : session.transfer().fetchDonorReferencesFromSpecimens(session.client())) {
            String idPart = new IdType(reference).getIdPart();
            if (idPart != null && !idPart.isBlank()) {
                ids.add(idPart);
            }
        }
        return ids;
    }

    @Override
    public DonorRecord read(SourceSession session, String donorId) {
        Patient patient = session.transfer().fetchResource(session.client(), Patient.class, donorId);

        List<Sample> samples = readSamples(session, donorId);

        List<Diagnosis> diagnoses = new ArrayList<>();
        List<CauseOfDeath> causesOfDeath = new ArrayList<>();
        for (IBaseResource resource : session.transfer().fetchConditionsOfDonor(session.client(), donorId)) {
            Condition condition = (Condition) resource;
            if (FhirProfileChecker.isMiiCauseOfDeath(condition)) {
                causesOfDeath.add(toCauseOfDeath(condition, donorId));
            } else if (FhirProfileChecker.isMiiDiagnosis(condition)) {
                diagnoses.add(toDiagnosis(condition, donorId));
            }
        }

        return new DonorRecord(toDonor(patient, donorId), samples, diagnoses, causesOfDeath);
    }

    private List<Sample> readSamples(SourceSession session, String donorId) {
        List<Specimen> fetched = session.transfer().fetchSpecimensOfDonor(session.client(), donorId);

        List<SpecimenNode> hierarchy = resolver.resolve(fetched);
        List<SpecimenNode> exportable = hierarchy.stream().filter(SpecimenNode::isExportable).toList();

        log.debug("Donor {} specimen hierarchy: {}", donorId,
                MiiSpecimenHierarchyResolver.countByLevel(hierarchy));

        hierarchy.stream().filter(SpecimenNode::isUsedUpAliquotGroup).forEach(group ->
                log.debug("Donor {}: skipped aliquot group {}, none of its aliquots is left",
                        donorId, group.getId()));

        long organoids = hierarchy.stream().filter(SpecimenNode::isCellLineOrOrganoid).count();
        if (organoids > 0) {
            log.debug("Donor {}: skipped {} cell line or organoid specimen(s)", donorId, organoids);
        }
        if (exportable.size() != hierarchy.size()) {
            log.info("Donor {}: exporting {} of {} specimens, aliquot groups and standalone "
                    + "samples only", donorId, exportable.size(), hierarchy.size());
        }
        return specimenToSampleMapper.toSamples(exportable).stream()
                .map(sample -> keepCollectionOnlyIfExported(sample, session.directory()))
                .toList();
    }

    /**
     * A sample may only name a collection that is exported in the same run. A reference to
     * anything else (a biobank, or an Organization the source does not hold) would not resolve on
     * the target, so the sample goes out without one.
     */
    private Sample keepCollectionOnlyIfExported(Sample sample, BiobankDirectory directory) {
        if (sample.collectionId() == null || directory.hasCollection(sample.collectionId())) {
            return sample;
        }
        if (unknownCollectionsWarnedAbout.add(sample.collectionId())) {
            log.warn("Samples are managed by {}, which is not a collection in the source, "
                    + "exporting them without a custodian", sample.collectionId());
        }
        return sample.withoutCollection();
    }

    static Donor toDonor(Patient patient, String donorId) {
        boolean deceased = patient.hasDeceasedBooleanType()
                && patient.getDeceasedBooleanType().equals(new BooleanType(true));
        return new Donor(
                donorId,
                patient.hasBirthDate() ? patient.getBirthDate() : null,
                patient.hasGender() ? patient.getGender().toCode() : null,
                deceased || patient.hasDeceasedDateTimeType(),
                patient.hasDeceasedDateTimeType() ? patient.getDeceasedDateTimeType() : null);
    }

    static Diagnosis toDiagnosis(Condition condition, String donorId) {
        return new Diagnosis(
                new IdType(condition.getId()).getIdPart(),
                donorId,
                CodedValue.firstOf(condition.getCode()),
                condition.hasOnsetDateTimeType() ? condition.getOnsetDateTimeType() : null);
    }

    static CauseOfDeath toCauseOfDeath(Condition condition, String donorId) {
        return new CauseOfDeath(
                new IdType(condition.getId()).getIdPart(),
                donorId,
                CodedValue.firstOf(condition.getCode()));
    }
}
