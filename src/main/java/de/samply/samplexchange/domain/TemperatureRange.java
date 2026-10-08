package de.samply.samplexchange.domain;

public record TemperatureRange(Long low, Long high) {
    public boolean isComplete() {
        return low != null && high != null;
    }
}
