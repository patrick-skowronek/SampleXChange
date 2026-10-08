package de.samply.samplexchange.domain;

import java.util.List;

/** The institution that runs one or more sample collections. */
public record Biobank(
        String id,
        String bbmriEricId,
        String name,
        String alias,
        String description,
        List<Contact> contacts) {
    public Biobank {
        contacts = List.copyOf(contacts);
    }
}
