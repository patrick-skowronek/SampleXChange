package de.samply.samplexchange.domain;

import java.util.List;

/** A person to contact about a biobank or collection, with the address requests should go to. */
public record Contact(
        String role,
        String family,
        List<String> given,
        List<String> prefix,
        String email,
        String phone,
        List<String> addressLines,
        String city,
        String postalCode,
        String country) {
    public Contact {
        given = List.copyOf(given);
        prefix = List.copyOf(prefix);
        addressLines = List.copyOf(addressLines);
    }
}
