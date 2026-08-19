package de.samply.samplexchange.converters;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Extension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ported from TransFAIR (converters/TemperatureConverterTest).
 *
 * <p>Only the MII to bbmri.de direction survives; {@code fromBbrmiToMii} was removed with the
 * BBMRI to MII pipeline.
 */
class TemperatureConverterTest {

    private static final String SYSTEM = "https://fhir.bbmri.de/CodeSystem/StorageTemperature";
    private static final String BBMRI_TEMPERATURE_EXTENSION_URL =
            "https://fhir.bbmri.de/StructureDefinition/StorageTemperature";

    @ParameterizedTest(name = "range {0}..{1} -> {2}")
    @CsvSource({
            "10,   2,    temperature2to10",
            "-18,  -35,  temperature-18to-35",
            "-60,  -85,  temperature-60to-85",
            "-160, -195, temperatureGN",
            "-196, -209, temperatureLN",
            "30,   11,   temperatureRoom",
            "100,  50,   temperatureOther",
    })
    void mapsRangeToBbmriStorageTemperature(long high, long low, String expectedCode) {
        Extension extension = TemperatureConverter.fromMiiToBbmri(high, low);

        assertEquals(BBMRI_TEMPERATURE_EXTENSION_URL, extension.getUrl());
        CodeableConcept value = (CodeableConcept) extension.getValue();
        assertEquals(SYSTEM, value.getCodingFirstRep().getSystem());
        assertEquals(expectedCode, value.getCodingFirstRep().getCode());
    }

    @Test
    void liquidNitrogenTakesPrecedenceOverGaseous() {
        // The LN branch is evaluated before GN, so a range fully inside LN must not report GN.
        Extension extension = TemperatureConverter.fromMiiToBbmri(-196L, -209L);
        CodeableConcept value = (CodeableConcept) extension.getValue();
        assertEquals("temperatureLN", value.getCodingFirstRep().getCode());
    }
}
