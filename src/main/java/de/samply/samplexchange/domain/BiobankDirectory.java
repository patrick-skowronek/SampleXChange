package de.samply.samplexchange.domain;

import java.util.List;

/** Every biobank and collection the source holds, read once per run before any donor. */
public record BiobankDirectory(List<Biobank> biobanks, List<SampleCollection> collections) {
    public BiobankDirectory {
        biobanks = List.copyOf(biobanks);
        collections = List.copyOf(collections);
    }

    public static BiobankDirectory empty() {
        return new BiobankDirectory(List.of(), List.of());
    }

    public boolean hasCollection(String id) {
        return collections.stream().anyMatch(collection -> collection.id().equals(id));
    }
}
