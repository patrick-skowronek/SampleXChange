package de.samply.samplexchange.domain;

import java.util.List;

public record DonorRecord(
        Donor donor,
        List<Sample> samples,
        List<Diagnosis> diagnoses,
        List<CauseOfDeath> causesOfDeath) {
    public DonorRecord {
        samples = List.copyOf(samples);
        diagnoses = List.copyOf(diagnoses);
        causesOfDeath = List.copyOf(causesOfDeath);
    }
}
