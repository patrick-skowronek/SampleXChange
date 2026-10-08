package de.samply.samplexchange.terminology;

import de.samply.samplexchange.configuration.TargetFormat;
import de.samply.samplexchange.domain.CodedValue;
import de.samply.samplexchange.domain.TemperatureRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped tables plus the validation that runs when they load.
 */
class TerminologyTest {

    private final Terminology terminology = new Terminology();

    @ParameterizedTest(name = "SNOMED {0} -> bbmri.de {1}, MIABIS {2}")
    @CsvSource({
            "119297000, whole-blood,                  WholeBlood",
            "119359002, bone-marrow,                  BoneMarrowWhole",
            "258587000, buffy-coat,                   BuffyCoat",
            "119294007, dried-whole-blood,            WholeBloodDried",
            "404798000, peripheral-blood-cells-vital, PBMC",
            "119361006, blood-plasma,                 Plasma",
            "119364003, blood-serum,                  Serum",
            "309201001, ascites,                      AscitesFluid",
            "258450006, csf-liquor,                   CerebrospinalFluid",
            "119342007, saliva,                       Saliva",
            "119339001, stool-faeces,                 Faeces",
            "122575003, urine,                        Urine",
            "257261003, swab,                         Swab",
            "441652008, tissue-ffpe,                  TissueFixed",
            "16214131000119104, tissue-frozen,        TissueFreshFrozen",
            "258566005, dna,                          DNA",
            "441673008, rna,                          RNA",
            "33463005,  liquid-other,                 Other",
            "122555007, whole-blood,                  WholeBlood",
            "16214371000119104, tissue-frozen,        TissueFreshFrozen",
            "418564007, liquid-other,                 PleuralFluid",
    })
    void sampleTypeMapsToBothTargets(String snomed, String bbmri, String miabis) {
        CodedValue type = new CodedValue("http://snomed.info/sct", snomed);
        assertEquals(Optional.of(bbmri), terminology.sampleType(TargetFormat.BBMRI_DE, type));
        assertEquals(Optional.of(miabis), terminology.sampleType(TargetFormat.MIABIS_V3, type));
    }

    @ParameterizedTest(name = "old converter row {0} now maps to {1} / {2}")
    @CsvSource({
            "122551003,  whole-blood,      WholeBlood",
            "258441009,  liquid-other,     Other",
            "1003517007, derivative-other, Other",
            "256912003,  derivative-other, RedBloodCells",
    })
    void rowsTheOldConverterGotWrongFollowSnomed(String snomed, String bbmri, String miabis) {
        // 122551003 is peripheral blood, not PBMC. 258441009 is exudate, not ascites. 1003517007
        // is freeze dried, not frozen. 256912003 is a red cell fraction, not whole blood.
        CodedValue type = new CodedValue("http://snomed.info/sct", snomed);
        assertEquals(Optional.of(bbmri), terminology.sampleType(TargetFormat.BBMRI_DE, type));
        assertEquals(Optional.of(miabis), terminology.sampleType(TargetFormat.MIABIS_V3, type));
    }

    @Test
    void everySampleTypeRowIsMarkedOkOrTodo() {
        // The review column is how a reviewer finds the guesses. A typo there hides one.
        List<String[]> rows = CodeMap.TableReader.readRows("terminology/sample-type.csv");
        int review = List.of(rows.get(0)).indexOf("review");
        assertTrue(review > 0, "sample-type.csv needs a review column");
        rows.stream().skip(1).forEach(row ->
                assertTrue(Set.of("ok", "todo").contains(row[review]), row[0] + " is marked " + row[review]));
    }

    @ParameterizedTest(name = "subtype {0} collapses to {1} / {2}")
    @CsvSource({
            "708049000, blood-plasma, Plasma",
            "708048008, blood-plasma, Plasma",
            "258958007, blood-plasma, Plasma",
            "446272009, blood-plasma, Plasma",
            "726740008, dna,          DNA",
            "18470003,  dna,          DNA",
    })
    void plasmaAndDnaSubtypesCollapseOntoTheParentCode(String snomed, String bbmri, String miabis) {
        // Deliberate. Neither target has an EDTA, citrate or heparin plasma code.
        CodedValue type = new CodedValue("http://snomed.info/sct", snomed);
        assertEquals(Optional.of(bbmri), terminology.sampleType(TargetFormat.BBMRI_DE, type));
        assertEquals(Optional.of(miabis), terminology.sampleType(TargetFormat.MIABIS_V3, type));
    }

    @ParameterizedTest(name = "{0} {1} -> bbmri.de {2}")
    @CsvSource({
            "https://fhir.bbmri-eric.eu/CodeSystem/miabis-collection-design-cs,              LongitudinalCohort, LONGITUDINAL",
            "https://fhir.bbmri-eric.eu/fhir/CodeSystem/miabis-collection-design-cs,         CaseControl,        CASE_CONTROL",
            "https://fhir.bbmri-eric.eu/CodeSystem/miabis-collection-design-cs,              Other,              OTHER",
            "https://fhir.bbmri-eric.eu/CodeSystem/miabis-sample-collection-setting-cs,      RoutineHealthCare,  HOSPITAL",
    })
    void collectionDesignAndSettingMapToABbmriCollectionType(String system, String code, String bbmri) {
        assertEquals(Optional.of(bbmri),
                terminology.collectionType(TargetFormat.BBMRI_DE, new CodedValue(system, code)));
    }

    @Test
    void aSettingBbmriHasNoTypeForIsLeftOut() {
        // Both MIABIS lists have Other, so the setting Other must not borrow the design's OTHER.
        CodedValue settingOther = new CodedValue(
                "https://fhir.bbmri-eric.eu/CodeSystem/miabis-sample-collection-setting-cs", "Other");
        assertTrue(terminology.collectionType(TargetFormat.BBMRI_DE, settingOther).isEmpty());
    }

    @Test
    void unknownSampleTypeReturnsEmptyAndIsRecorded() {
        // 73211009 is diabetes mellitus, a SNOMED code that is not a specimen. The caller applies
        // the fallback so the run can report how often that happened.
        CodedValue unknown = new CodedValue("http://snomed.info/sct", "73211009");

        assertTrue(terminology.sampleType(TargetFormat.BBMRI_DE, unknown).isEmpty());
        terminology.logSampleTypesWithNoMapping();
    }

    @Test
    void aMissingTypeIsNotAnUnknownCode() {
        assertTrue(terminology.sampleType(TargetFormat.BBMRI_DE, null).isEmpty());
    }

    @ParameterizedTest(name = "{0}..{1} -> bbmri.de {2}, MIABIS {3}")
    @CsvSource({
            "2,    10,   temperature2to10,    2to10",
            "-35,  -18,  temperature-18to-35, -18to-35",
            "-85,  -60,  temperature-60to-85, -60to-85",
            "-209, -196, temperatureLN,       LN",
            "-196, -150, temperatureLN,       LN",
            "11,   30,   temperatureRoom,     RT",
    })
    void storageTemperatureMapsToBothTargets(long low, long high, String bbmri, String miabis) {
        TemperatureRange range = new TemperatureRange(low, high);
        assertEquals(Optional.of(bbmri), terminology.storageTemperature(TargetFormat.BBMRI_DE, range));
        assertEquals(Optional.of(miabis), terminology.storageTemperature(TargetFormat.MIABIS_V3, range));
    }

    @Test
    void liquidNitrogenIsTestedBeforeGaseousNitrogen() {
        // Row order carries this. Swap the two rows and LN starts reporting GN.
        TemperatureRange liquid = new TemperatureRange(-209L, -196L);
        assertEquals(Optional.of("temperatureLN"),
                terminology.storageTemperature(TargetFormat.BBMRI_DE, liquid));
    }

    @Test
    void gaseousNitrogenHasNoMiabisBucketAndFallsBackToOther() {
        // MIABIS storage temperature has RT, 2to10, -18to-35, -60to-85, LN and Other. No GN.
        TemperatureRange gaseous = new TemperatureRange(-195L, -160L);

        assertEquals(Optional.of("temperatureGN"),
                terminology.storageTemperature(TargetFormat.BBMRI_DE, gaseous));
        assertEquals(Optional.of("Other"),
                terminology.storageTemperature(TargetFormat.MIABIS_V3, gaseous));
    }

    @Test
    void theMiiLiquidNitrogenSpanGoesToTheClosestBbmriBucket() {
        // -196 to -150 straddles bbmri.de LN and GN and reaches above both. LN is the closest and
        // the table warns that it is not exact. For MIABIS it is exactly LN.
        TemperatureRange miiLiquidNitrogen = new TemperatureRange(-196L, -150L);

        assertEquals(Optional.of("temperatureLN"),
                terminology.storageTemperature(TargetFormat.BBMRI_DE, miiLiquidNitrogen));
        assertEquals(Optional.of("LN"),
                terminology.storageTemperature(TargetFormat.MIABIS_V3, miiLiquidNitrogen));
    }

    @Test
    void anUnknownTargetInClosestForIsRejected() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> TemperatureTable.load("terminology-bad/unknown-closest-target.csv"));
        assertTrue(thrown.getMessage().contains("bbmri_eu"), thrown.getMessage());
    }

    @Test
    void aRangeNoRowCoversReturnsEmpty() {
        assertTrue(terminology.storageTemperature(TargetFormat.BBMRI_DE,
                new TemperatureRange(50L, 100L)).isEmpty());
    }

    @Test
    void anIncompleteRangeNeverMatches() {
        assertTrue(terminology.storageTemperature(TargetFormat.BBMRI_DE,
                new TemperatureRange(null, 10L)).isEmpty());
        assertTrue(terminology.storageTemperature(TargetFormat.BBMRI_DE, null).isEmpty());
    }

    @Test
    void everyTargetNeedsAColumn() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> CodeMap.load("terminology-bad/missing-column.csv", "snomed"));
        assertTrue(thrown.getMessage().contains("miabis_v3"), thrown.getMessage());
    }

    @Test
    void aRepeatedKeyIsRejected() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> CodeMap.load("terminology-bad/duplicate-key.csv", "snomed"));
        assertTrue(thrown.getMessage().contains("119297000"), thrown.getMessage());
    }

    @Test
    void anEmptyCellIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> CodeMap.load("terminology-bad/empty-cell.csv", "snomed"));
    }

    @Test
    void aRaggedRowIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> CodeMap.load("terminology-bad/ragged-row.csv", "snomed"));
    }

    @Test
    void aMissingTableIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> CodeMap.load("terminology/does-not-exist.csv", "snomed"));
    }
}
