package de.samply.samplexchange.configuration;

public enum TargetFormat {
    BBMRI_DE("bbmri_de"),
    MIABIS_V3("miabis_v3");

    private final String column;

    TargetFormat(String column) {
        this.column = column;
    }

    public String column() {
        return column;
    }
}
