package de.samply.samplexchange.converters;

import java.util.Objects;

/**
 * Static methods for converting the bbmri.de sample types to SnomedCT codes and back.
 */
public class SnomedSamplyTypeConverter {

    private SnomedSamplyTypeConverter() {
    }

    /**
     * From SnomedCT to bbmri.de sample type.
     */
    public static String fromMiiToBbmri(String snomedType) {
        return switch (snomedType) {
            case "119297000", "122558009", "256912003" -> "whole-blood";
            case "119359002", "396997002", "396998007", "396999004", "110897001", "167913002" -> "bone-marrow";
            case "258587000", "117171008" -> "buffy-coat";
            case "119294007", "440500007", "738796001" -> "dried-whole-blood";
            case "404798000", "122551003" -> "peripheral-blood-cells-vital";
            case "119361006", "708049000", "708048008", "258958007", "446272009", "2431000181102", "2441000181109", "898205005" ->
                    "blood-plasma";
            case "119364003", "122591000" -> "blood-serum";
            case "258441009", "442039000" -> "ascites";
            case "258450006" -> "csf-liquor";
            case "119342007" -> "saliva";
            case "119339001" -> "stool-faeces";
            case "122575003" -> "urine";
            case "257261003" -> "swab";
            case "441652008" -> "tissue-ffpe";
            case "16214131000119104", "1003517007" -> "tissue-frozen";
            case "119376003" -> "tissue-other";
            case "258566005", "726740008", "18470003" -> "dna";
            case "441673008" -> "rna";
            case "33463005" -> "liquid-other";
            default -> "derivative-other";
        };
    }

}
