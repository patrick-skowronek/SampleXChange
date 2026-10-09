package de.samply.samplexchange.source.mii;

import de.samply.samplexchange.domain.Biobank;
import de.samply.samplexchange.domain.BiobankDirectory;
import de.samply.samplexchange.domain.CodedValue;
import de.samply.samplexchange.domain.Contact;
import de.samply.samplexchange.domain.SampleCollection;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.StringType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns MII biobank Organizations into biobanks and collections.
 *
 * <p>MII uses one profile for both. An Organization with no {@code partOf} is a biobank; one with
 * {@code partOf} is a collection, belonging to the biobank at the top of its {@code partOf} chain.
 * When the chain passes through another collection, that one is the parent collection.
 */
@Slf4j
final class MiiOrganizationReader {

    static final String ORGANIZATION_PROFILE =
            "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/Organization";
    static final String BBMRI_ERIC_ID_SYSTEM = "http://www.bbmri-eric.eu/";

    // MII 2026 uses the MIABIS extensions, and spells their canonical with /fhir/ in the profile
    // but without it in the examples. MII 2025 had its own description extension.
    private static final List<String> DESCRIPTION_EXTENSIONS = List.of(
            "https://fhir.bbmri-eric.eu/StructureDefinition/miabis-organization-description-extension",
            "https://fhir.bbmri-eric.eu/fhir/StructureDefinition/miabis-organization-description-extension",
            "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/BeschreibungSammlung");
    private static final List<String> COLLECTION_TYPE_EXTENSIONS = List.of(
            "https://fhir.bbmri-eric.eu/StructureDefinition/miabis-collection-design-extension",
            "https://fhir.bbmri-eric.eu/fhir/StructureDefinition/miabis-collection-design-extension",
            "https://fhir.bbmri-eric.eu/StructureDefinition/miabis-sample-collection-setting-extension",
            "https://fhir.bbmri-eric.eu/fhir/StructureDefinition/miabis-sample-collection-setting-extension");
    private static final String CONTACT_ROLE_EXTENSION =
            "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/KontaktRolle";

    private static final int MAX_PART_OF_DEPTH = 32;

    private MiiOrganizationReader() {
    }

    static BiobankDirectory toDirectory(List<Organization> organizations) {
        Map<String, Organization> byId = new LinkedHashMap<>();
        for (Organization organization : organizations) {
            if (!hasMiiProfile(organization)) {
                continue;
            }
            String id = organization.getIdElement().getIdPart();
            if (id == null || id.isBlank()) {
                log.warn("Skipping an MII Organization because it has no id");
                continue;
            }
            byId.putIfAbsent(id, organization);
        }

        List<Biobank> biobanks = new ArrayList<>();
        List<SampleCollection> collections = new ArrayList<>();
        byId.forEach((id, organization) -> {
            if (organization.hasPartOf()) {
                collections.add(toCollection(id, organization, byId));
            } else {
                biobanks.add(toBiobank(id, organization));
            }
        });
        log.info("Read {} biobank(s) and {} collection(s)", biobanks.size(), collections.size());
        return new BiobankDirectory(biobanks, collections);
    }

    static boolean hasMiiProfile(Organization organization) {
        return organization.getMeta().getProfile().stream()
                .anyMatch(profile -> ORGANIZATION_PROFILE.equals(profile.asStringValue()));
    }

    private static Biobank toBiobank(String id, Organization organization) {
        return new Biobank(
                id,
                readBbmriEricId(organization),
                organization.getName(),
                readAlias(organization),
                readDescription(organization),
                readContactsOfOrganization(id, organization));
    }

    private static SampleCollection toCollection(String id, Organization organization,
                                                 Map<String, Organization> byId) {
        String parentId = readPartOfId(organization);
        Organization parent = byId.get(parentId);
        if (parent == null) {
            log.warn("Collection {} is part of {}, which is not an MII Organization in the source; "
                    + "writing it without a biobank", id, parentId);
        }
        return new SampleCollection(
                id,
                findBiobankAbove(id, organization, byId),
                parent != null && parent.hasPartOf() ? parentId : null,
                readBbmriEricId(organization),
                organization.getName(),
                readAlias(organization),
                readDescription(organization),
                readCollectionTypes(organization),
                readContactsOfOrganization(id, organization));
    }

    private static String findBiobankAbove(String id, Organization organization, Map<String, Organization> byId) {
        Set<String> seen = new HashSet<>(Set.of(id));
        Organization current = organization;
        for (int depth = 0; depth < MAX_PART_OF_DEPTH; depth++) {
            String parentId = readPartOfId(current);
            Organization parent = byId.get(parentId);
            if (parent == null) {
                return null;
            }
            if (!seen.add(parentId)) {
                log.warn("Collection {} is part of a partOf cycle, writing it without a biobank", id);
                return null;
            }
            if (!parent.hasPartOf()) {
                return parentId;
            }
            current = parent;
        }
        return null;
    }

    private static String readPartOfId(Organization organization) {
        if (!organization.getPartOf().hasReference()) {
            return null;
        }
        return new IdType(organization.getPartOf().getReference()).getIdPart();
    }

    private static String readBbmriEricId(Organization organization) {
        return organization.getIdentifier().stream()
                .filter(identifier -> BBMRI_ERIC_ID_SYSTEM.equals(identifier.getSystem()))
                .map(Identifier::getValue)
                .findFirst()
                .orElse(null);
    }

    private static String readAlias(Organization organization) {
        return organization.getAlias().isEmpty() ? null : organization.getAlias().get(0).getValue();
    }

    private static String readDescription(Organization organization) {
        for (String url : DESCRIPTION_EXTENSIONS) {
            Extension description = organization.getExtensionByUrl(url);
            if (description != null && description.getValue() instanceof PrimitiveType<?> text
                    && text.hasValue()) {
                return text.getValueAsString();
            }
        }
        return null;
    }

    private static List<CodedValue> readCollectionTypes(Organization organization) {
        List<CodedValue> types = new ArrayList<>();
        for (Extension extension : organization.getExtension()) {
            if (COLLECTION_TYPE_EXTENSIONS.contains(extension.getUrl())
                    && extension.getValue() instanceof CodeableConcept concept) {
                types.addAll(CodedValue.allOf(concept));
            }
        }
        return types;
    }


    private static List<Contact> readContactsOfOrganization(String organizationId, Organization organization) {
        List<Contact> contacts = new ArrayList<>();
        for (Organization.OrganizationContactComponent contact : organization.getContact()) {
            HumanName name = contact.getName();
            String family = name.getFamily();
            if (family == null || family.isBlank()) {
                log.warn("Organization {}: leaving out a contact without a family name", organizationId);
                continue;
            }
            List<String> gaps = new ArrayList<>();
            Address address = contact.getAddress();
            Extension role = contact.getExtensionByUrl(CONTACT_ROLE_EXTENSION);
            String email = readTelecom(contact, ContactPoint.ContactPointSystem.EMAIL);
            if (email == null) {
                gaps.add("no email");
            }
            if (!contact.hasAddress()) {
                gaps.add("no address");
            }
            contacts.add(new Contact(
                    role != null && role.getValue() instanceof PrimitiveType<?> text ? text.getValueAsString() : null,
                    family,
                    valuesOf(name.getGiven(), "given name", gaps),
                    valuesOf(name.getPrefix(), "name prefix", gaps),
                    email,
                    readTelecom(contact, ContactPoint.ContactPointSystem.PHONE),
                    valuesOf(address.getLine(), "address line", gaps),
                    address.getCity(),
                    address.getPostalCode(),
                    address.getCountry()));
            if (!gaps.isEmpty()) {
                log.warn("Organization {}: contact {} is incomplete: {}", organizationId, family,
                        String.join(", ", gaps));
            }
        }
        return contacts;
    }

    /** The parts that have a value; each one without is noted in {@code gaps}. */
    private static List<String> valuesOf(List<StringType> parts, String what, List<String> gaps) {
        List<String> values = new ArrayList<>();
        for (StringType part : parts) {
            if (part.hasValue() && !part.getValue().isBlank()) {
                values.add(part.getValue());
            } else {
                gaps.add("a " + what + " with no value");
            }
        }
        return values;
    }

    private static String readTelecom(Organization.OrganizationContactComponent contact,
                                      ContactPoint.ContactPointSystem system) {
        return contact.getTelecom().stream()
                .filter(telecom -> telecom.getSystem() == system && telecom.hasValue())
                .map(ContactPoint::getValue)
                .findFirst()
                .orElse(null);
    }
}
