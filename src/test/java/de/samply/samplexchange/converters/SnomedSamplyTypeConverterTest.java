package de.samply.samplexchange.converters;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Ported from TransFAIR (converters/SnomedSamplyTypeConverterTest).
 *
 * <p>Only the MII to bbmri.de direction survives; {@code fromBbmriToMii} was removed with the
 * BBMRI to MII pipeline.
 */
class SnomedSamplyTypeConverterTest {

    @ParameterizedTest(name = "SNOMED {0} -> {1}")
    @CsvSource({
            "119297000, whole-blood",
            "122558009, whole-blood",
            "256912003, whole-blood",
            "119359002, bone-marrow",
            "167913002, bone-marrow",
            "258587000, buffy-coat",
            "117171008, buffy-coat",
            "119294007, dried-whole-blood",
            "404798000, peripheral-blood-cells-vital",
            "119361006, blood-plasma",
            "119364003, blood-serum",
            "122591000, blood-serum",
            "258441009, ascites",
            "258450006, csf-liquor",
            "119342007, saliva",
            "119339001, stool-faeces",
            "122575003, urine",
            "257261003, swab",
            "441652008, tissue-ffpe",
            "16214131000119104, tissue-frozen",
            "1003517007, tissue-frozen",
            "119376003, tissue-other",
            "258566005, dna",
            "441673008, rna",
            "33463005, liquid-other",
    })
    void mapsSnomedToBbmriSampleType(String snomed, String expected) {
        assertEquals(expected, SnomedSamplyTypeConverter.fromMiiToBbmri(snomed));
    }

    @ParameterizedTest(name = "unknown code {0} -> derivative-other")
    @CsvSource({"''", "'999999999'"})
    void unknownCodeFallsBackToDerivativeOther(String snomed) {
        assertEquals("derivative-other", SnomedSamplyTypeConverter.fromMiiToBbmri(snomed));
    }

    /**
     * DIVERGENCE from TransFAIR. TransFAIR mapped these to the narrower bbmri.de codes
     * plasma-edta, plasma-citrat, plasma-heparin, cf-dna and g-dna. This project collapses them
     * onto blood-plasma and dna, so that granularity is lost even though the bbmri.de
     * SampleMaterialType value set supports it. Pinned here as current behaviour, not as
     * endorsement -- see docs/adr/0001-mapping-architecture.md.
     */
    @ParameterizedTest(name = "SNOMED {0} collapses to {1} (was {2} in TransFAIR)")
    @CsvSource({
            "708049000, blood-plasma, plasma-edta",
            "708048008, blood-plasma, plasma-citrat",
            "258958007, blood-plasma, plasma-heparin",
            "446272009, blood-plasma, plasma-heparin",
            "726740008, dna, cf-dna",
            "18470003,  dna, g-dna",
    })
    void plasmaAndDnaSubtypesAreCollapsed(String snomed, String current, String transfairValue) {
        assertEquals(current, SnomedSamplyTypeConverter.fromMiiToBbmri(snomed));
    }

    @Test
    void nullCodeThrows() {
        // fromMiiToBbmri switches on the raw string with no null guard, unlike the deleted
        // fromBbmriToMii which returned a default. Documented so a caller change is deliberate.
        assertThrows(NullPointerException.class, () -> SnomedSamplyTypeConverter.fromMiiToBbmri(null));
    }
}
