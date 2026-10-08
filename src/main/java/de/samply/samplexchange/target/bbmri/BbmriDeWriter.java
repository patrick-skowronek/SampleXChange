package de.samply.samplexchange.target.bbmri;

import de.samply.samplexchange.configuration.TargetFormat;
import de.samply.samplexchange.domain.Biobank;
import de.samply.samplexchange.domain.BiobankDirectory;
import de.samply.samplexchange.domain.CauseOfDeath;
import de.samply.samplexchange.domain.CodedValue;
import de.samply.samplexchange.domain.Contact;
import de.samply.samplexchange.domain.Diagnosis;
import de.samply.samplexchange.domain.Donor;
import de.samply.samplexchange.domain.DonorRecord;
import de.samply.samplexchange.domain.Sample;
import de.samply.samplexchange.domain.SampleCollection;
import de.samply.samplexchange.domain.TemperatureRange;
import de.samply.samplexchange.target.TargetWriter;
import de.samply.samplexchange.terminology.Terminology;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGenderEnumFactory;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Specimen;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class BbmriDeWriter implements TargetWriter {
    public static final String PATIENT_PROFILE = "https://fhir.simplifier.net/bbmri.de/StructureDefinition/Patient";
    public static final String SPECIMEN_PROFILE = "https://fhir.bbmri.de/StructureDefinition/Specimen";
    public static final String CONDITION_PROFILE = "https://fhir.bbmri.de/StructureDefinition/Condition";
    public static final String CAUSE_OF_DEATH_PROFILE = "https://fhir.bbmri.de/StructureDefinition/CauseOfDeath";
    public static final String BIOBANK_PROFILE = "https://fhir.bbmri.de/StructureDefinition/Biobank";
    public static final String COLLECTION_PROFILE = "https://fhir.bbmri.de/StructureDefinition/Collection";

    public static final String CUSTODIAN_EXTENSION = "https://fhir.bbmri.de/StructureDefinition/Custodian";
    public static final String DESCRIPTION_EXTENSION = "https://fhir.bbmri.de/StructureDefinition/OrganizationDescription";
    public static final String PARENT_COLLECTION_EXTENSION = "https://fhir.bbmri.de/StructureDefinition/ParentCollection";
    public static final String COLLECTION_TYPE_EXTENSION = "https://fhir.bbmri.de/StructureDefinition/CollectionType";
    public static final String COLLECTION_TYPE_SYSTEM = "https://fhir.bbmri.de/CodeSystem/CollectionType";
    public static final String DATA_CATEGORY_EXTENSION = "https://fhir.bbmri.de/StructureDefinition/DataCategory";
    public static final String DATA_CATEGORY_SYSTEM = "https://fhir.bbmri.de/CodeSystem/DataCategory";
    public static final String CONTACT_ROLE_EXTENSION = "https://fhir.bbmri.de/StructureDefinition/ContactRole";
    public static final String CONTACT_TYPE_SYSTEM = "https://fhir.bbmri.de/CodeSystem/ContactType";
    public static final String BBMRI_ERIC_ID_SYSTEM = "http://www.bbmri-eric.eu/";

    public static final String SAMPLE_MATERIAL_TYPE_SYSTEM = "https://fhir.bbmri.de/CodeSystem/SampleMaterialType";
    public static final String STORAGE_TEMPERATURE_SYSTEM = "https://fhir.bbmri.de/CodeSystem/StorageTemperature";
    public static final String STORAGE_TEMPERATURE_EXTENSION = "https://fhir.bbmri.de/StructureDefinition/StorageTemperature";
    public static final String BODY_SITE_SYSTEM = "urn:oid:1.3.6.1.4.1.19376.1.3.11.36";
    static final String LOINC = "http://loinc.org";
    static final String SNOMED = "http://snomed.info/sct";

    static final String SAMPLE_TYPE_WHEN_UNMAPPED = "derivative-other";

    private final Terminology terminology;

    public BbmriDeWriter(Terminology terminology) {
        this.terminology = terminology;
    }

    @Override
    public TargetFormat format() {
        return TargetFormat.BBMRI_DE;
    }

    @Override
    public List<Resource> write(BiobankDirectory directory) {
        List<Resource> resources = new ArrayList<>();
        directory.biobanks().forEach(biobank -> resources.add(toBiobank(biobank)));
        directory.collections().forEach(collection -> resources.add(toCollection(collection)));
        return resources;
    }

    @Override
    public List<Resource> write(DonorRecord record) {
        List<Resource> resources = new ArrayList<>();
        resources.add(toPatient(record.donor()));
        for (Sample sample : record.samples()) {
            if (sample.cellLineOrOrganoid()) {
                log.debug("Skipping cell line or organoid {}, bbmri.de cannot represent it",
                        sample.id());
                continue;
            }
            resources.add(toSpecimen(sample));
        }
        record.diagnoses().forEach(diagnosis -> resources.add(toCondition(diagnosis)));
        record.causesOfDeath().forEach(cause -> resources.add(toCauseOfDeathObservation(cause)));
        return resources;
    }

    Patient toPatient(Donor donor) {
        Patient patient = new Patient();
        patient.setMeta(new Meta().addProfile(PATIENT_PROFILE));
        patient.setId(donor.id());
        if (donor.gender() != null) {
            patient.setGender(new AdministrativeGenderEnumFactory().fromCode(donor.gender()));
        }
        if (donor.birthDate() != null) {
            patient.setBirthDate(donor.birthDate());
        }
        if (donor.deceased() && donor.deceasedDateTime() != null) {
            patient.setDeceased(donor.deceasedDateTime());
        }
        return patient;
    }

    Specimen toSpecimen(Sample sample) {
        Specimen specimen = new Specimen();
        specimen.setMeta(new Meta().addProfile(SPECIMEN_PROFILE));
        specimen.setId(sample.id());

        if (sample.donorId() != null) {
            specimen.setSubject(referenceToDonor(sample.donorId()));
        }
        specimen.setType(toSampleType(sample.types()));

        if (sample.collectedAt() != null) {
            specimen.getCollection().setCollected(sample.collectedAt());
        }
        CodeableConcept bodySite = toBodySite(sample.id(), sample.bodySite());
        if (bodySite != null) {
            specimen.getCollection().setBodySite(bodySite);
        }
        CodeableConcept fasting = toFastingStatus(sample.fastingStatus());
        if (fasting != null) {
            specimen.getCollection().setFastingStatus(fasting);
        }
        Extension temperature = toStorageTemperatureExtension(sample.storageTemperature());
        if (temperature != null) {
            specimen.addExtension(temperature);
        }
        if (sample.collectionId() != null) {
            specimen.addExtension(CUSTODIAN_EXTENSION, referenceToOrganization(sample.collectionId()));
        }
        return specimen;
    }

    Organization toBiobank(Biobank biobank) {
        Organization organization = new Organization();
        organization.setMeta(new Meta().addProfile(BIOBANK_PROFILE));
        organization.setId(biobank.id());
        List<String> missing = new ArrayList<>();

        addCommonFields(organization, biobank.bbmriEricId(), biobank.name(), biobank.alias(),
                biobank.description(), missing);
        biobank.contacts().forEach(contact -> organization.addContact(toResearchContact(contact)));
        if (biobank.contacts().isEmpty()) {
            missing.add("research contact");
        }
        // MII records none of these, and they cannot be derived from anything it does record.
        missing.add("juridical person");
        missing.add("address country");
        missing.add("head contact");

        warnAboutMissingFields("Biobank", biobank.id(), missing);
        return organization;
    }

    Organization toCollection(SampleCollection collection) {
        Organization organization = new Organization();
        organization.setMeta(new Meta().addProfile(COLLECTION_PROFILE));
        organization.setId(collection.id());
        List<String> missing = new ArrayList<>();

        addCommonFields(organization, collection.bbmriEricId(), collection.name(), collection.alias(),
                collection.description(), missing);
        if (collection.biobankId() != null) {
            organization.setPartOf(referenceToOrganization(collection.biobankId()));
        } else {
            missing.add("biobank (partOf)");
        }
        if (collection.parentCollectionId() != null) {
            organization.addExtension(PARENT_COLLECTION_EXTENSION,
                    referenceToOrganization(collection.parentCollectionId()));
        }

        List<String> types = collection.types().stream()
                .map(type -> terminology.collectionType(TargetFormat.BBMRI_DE, type))
                .flatMap(Optional::stream)
                .distinct()
                .toList();
        types.forEach(type -> organization.addExtension(COLLECTION_TYPE_EXTENSION,
                codeableConcept(COLLECTION_TYPE_SYSTEM, type)));
        if (types.isEmpty()) {
            missing.add("collection type");
        }
        // An MII collection is a Probensammlung, a collection of samples by definition.
        organization.addExtension(DATA_CATEGORY_EXTENSION,
                codeableConcept(DATA_CATEGORY_SYSTEM, "BIOLOGICAL_SAMPLES"));

        collection.contacts().forEach(contact -> organization.addContact(toResearchContact(contact)));
        warnAboutMissingFields("Collection", collection.id(), missing);
        return organization;
    }

    private static void addCommonFields(Organization organization, String bbmriEricId, String name,
                                        String alias, String description, List<String> missing) {
        if (bbmriEricId != null) {
            organization.addIdentifier().setSystem(BBMRI_ERIC_ID_SYSTEM).setValue(bbmriEricId);
        } else {
            missing.add("BBMRI-ERIC id");
        }
        if (name != null) {
            organization.setName(name);
        } else {
            missing.add("name");
        }
        if (alias != null) {
            organization.addAlias(alias);
        }
        if (description != null) {
            organization.addExtension(DESCRIPTION_EXTENSION, new StringType(description));
        } else {
            missing.add("description");
        }
    }

    static Organization.OrganizationContactComponent toResearchContact(Contact source) {
        Organization.OrganizationContactComponent contact = new Organization.OrganizationContactComponent();
        contact.setPurpose(codeableConcept(CONTACT_TYPE_SYSTEM, "RESEARCH"));
        if (source.role() != null) {
            contact.addExtension(CONTACT_ROLE_EXTENSION, new StringType(source.role()));
        }
        HumanName name = contact.getName();
        name.setFamily(source.family());
        source.given().forEach(name::addGiven);
        source.prefix().forEach(name::addPrefix);
        if (source.email() != null) {
            contact.addTelecom().setSystem(ContactPoint.ContactPointSystem.EMAIL).setValue(source.email());
        }
        if (source.phone() != null) {
            contact.addTelecom().setSystem(ContactPoint.ContactPointSystem.PHONE).setValue(source.phone());
        }
        Address address = contact.getAddress();
        source.addressLines().forEach(address::addLine);
        address.setCity(source.city());
        address.setPostalCode(source.postalCode());
        address.setCountry(source.country());
        return contact;
    }

    private static void warnAboutMissingFields(String profile, String id, List<String> missing) {
        if (!missing.isEmpty()) {
            log.warn("bbmri.de {} {} lacks fields its profile requires, the source has none for: {}",
                    profile, id, String.join(", ", missing));
        }
    }

    static Reference referenceToOrganization(String organizationId) {
        return new Reference("Organization/" + organizationId);
    }

    private static CodeableConcept codeableConcept(String system, String code) {
        CodeableConcept concept = new CodeableConcept();
        concept.getCodingFirstRep().setSystem(system).setCode(code);
        return concept;
    }

    Condition toCondition(Diagnosis diagnosis) {
        Condition condition = new Condition();
        condition.setMeta(new Meta().addProfile(CONDITION_PROFILE));
        condition.setId(diagnosis.id());
        if (diagnosis.donorId() != null) {
            condition.setSubject(referenceToDonor(diagnosis.donorId()));
        }
        if (diagnosis.onset() != null) {
            condition.setOnset(diagnosis.onset());
        }
        CodeableConcept code = toDiagnosisCode(diagnosis.code());
        if (code != null) {
            condition.setCode(code);
        }
        return condition;
    }

    Observation toCauseOfDeathObservation(CauseOfDeath cause) {
        Observation observation = new Observation();
        observation.setMeta(new Meta().addProfile(CAUSE_OF_DEATH_PROFILE));
        observation.setId(cause.id());
        observation.getCode().getCodingFirstRep().setSystem(LOINC).setCode("68343-3");
        if (cause.donorId() != null) {
            observation.setSubject(referenceToDonor(cause.donorId()));
        }
        CodeableConcept code = toDiagnosisCode(cause.code());
        if (code != null) {
            observation.setValue(code);
        } else {
            log.warn("Cause of death {} has no code", cause.id());
        }
        return observation;
    }

    static Reference referenceToDonor(String donorId) {
        return new Reference("Patient/" + donorId);
    }

    CodeableConcept toSampleType(List<CodedValue> types) {
        String code = terminology.sampleType(TargetFormat.BBMRI_DE, CodedValue.inSystem(types, SNOMED))
                .orElse(SAMPLE_TYPE_WHEN_UNMAPPED);
        CodeableConcept concept = new CodeableConcept();
        concept.getCodingFirstRep().setSystem(SAMPLE_MATERIAL_TYPE_SYSTEM).setCode(code);
        return concept;
    }

    static CodeableConcept toBodySite(String sampleId, CodedValue site) {
        if (site == null) {
            return null;
        }
        // bbmri.de only takes ICD-O-3 topography. MII 2026 records body sites in SNOMED CT and
        // there is no SNOMED to ICD-O-3 table yet (docs/TODO.md), so those are left out.
        if (!site.isIcdO3()) {
            log.warn("Sample {}: body site {}|{} is not ICD-O-3 topography, leaving it out",
                    sampleId, site.system(), site.code());
            return null;
        }
        CodeableConcept concept = new CodeableConcept();
        concept.getCodingFirstRep().setSystem(BODY_SITE_SYSTEM).setCode(site.code());
        return concept;
    }

    static CodeableConcept toFastingStatus(CodedValue status) {
        if (status == null) {
            return null;
        }
        CodeableConcept concept = new CodeableConcept();
        concept.getCodingFirstRep().setSystem(status.system()).setCode(status.code());
        return concept;
    }

    Extension toStorageTemperatureExtension(TemperatureRange range) {
        return terminology.storageTemperature(TargetFormat.BBMRI_DE, range)
                .map(code -> {
                    CodeableConcept concept = new CodeableConcept();
                    concept.getCodingFirstRep().setSystem(STORAGE_TEMPERATURE_SYSTEM).setCode(code);
                    return new Extension(STORAGE_TEMPERATURE_EXTENSION, concept);
                })
                .orElse(null);
    }

    static CodeableConcept toDiagnosisCode(CodedValue code) {
        if (code == null) {
            return null;
        }
        CodeableConcept concept = new CodeableConcept();
        concept.getCodingFirstRep()
                .setSystem(code.system())
                .setVersion(code.version())
                .setCode(code.code());
        return concept;
    }

    static DateTimeType copyOf(DateTimeType value) {
        return value == null ? null : value.copy();
    }
}
