package de.samply.samplexchange.converters;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Range;

/**
 * Convert between bbmri.de and MII KDS temperature.
 */
public class TemperatureConverter {

    public static final String URL = "https://fhir.bbmri.de/CodeSystem/StorageTemperature";

    private TemperatureConverter() {
    }

    /**
     * From MII KDS to bbmri.de temperature.
     */
    public static Extension fromMiiToBbmri(Long high, Long low) {
        Extension extension = new Extension();
        extension.setUrl("https://fhir.bbmri.de/StructureDefinition/StorageTemperature");

        CodeableConcept codeableConcept = new CodeableConcept();

        if (high <= 10 && low >= 2) {
            codeableConcept.getCodingFirstRep().setSystem(URL).setCode("temperature2to10");
        } else if (high <= -18 && low >= -35) {
            codeableConcept.getCodingFirstRep().setSystem(URL).setCode("temperature-18to-35");
        } else if (high <= -60 && low >= -85) {
            codeableConcept.getCodingFirstRep().setSystem(URL).setCode("temperature-60to-85");
        } else if (high <= -196 && low >= -209) {
            codeableConcept.getCodingFirstRep().setSystem(URL).setCode("temperatureLN");
        } else if (high <= -160 && low >= -195) {
            codeableConcept.getCodingFirstRep().setSystem(URL).setCode("temperatureGN");
        } else if (high <= 30 && low >= 11) {
            codeableConcept.getCodingFirstRep().setSystem(URL).setCode("temperatureRoom");
        } else {
            codeableConcept.getCodingFirstRep().setSystem(URL).setCode("temperatureOther");
        }

        extension.setValue(codeableConcept);

        return extension;
    }
}
