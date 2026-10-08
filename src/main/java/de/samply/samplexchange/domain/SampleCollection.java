package de.samply.samplexchange.domain;

import java.util.List;

/**
 * A collection of samples inside a biobank, the level a sample names as its custodian.
 *
 * <p>{@code types} holds the collection's design and setting codes as the source records them;
 * each writer translates them into its own collection type list.
 */
public record SampleCollection(
        String id,
        String biobankId,
        String parentCollectionId,
        String bbmriEricId,
        String name,
        String alias,
        String description,
        List<CodedValue> types,
        List<Contact> contacts) {
    public SampleCollection {
        types = List.copyOf(types);
        contacts = List.copyOf(contacts);
    }
}
