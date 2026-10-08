package de.samply.samplexchange.transform;

import de.samply.samplexchange.configuration.SourceFormat;
import de.samply.samplexchange.configuration.TargetFormat;
import de.samply.samplexchange.domain.BiobankDirectory;
import de.samply.samplexchange.domain.DonorRecord;
import de.samply.samplexchange.source.SourceReader;
import de.samply.samplexchange.source.SourceSession;
import de.samply.samplexchange.target.TargetWriter;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Selection is a plain object graph, so it can be checked without a Spring context. That is the
 * point of the registry: the previous {@code @ConditionalOnExpression} could only be exercised by
 * booting the application.
 */
class TransformationRegistryTest {

    private record StubReader(SourceFormat format) implements SourceReader {
        @Override
        public BiobankDirectory readDirectory(SourceSession session) {
            return BiobankDirectory.empty();
        }

        @Override
        public Set<String> donorIds(SourceSession session) {
            return Set.of();
        }

        @Override
        public DonorRecord read(SourceSession session, String donorId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StubWriter(TargetFormat format) implements TargetWriter {
        @Override
        public List<Resource> write(BiobankDirectory directory) {
            return List.of();
        }

        @Override
        public List<Resource> write(DonorRecord record) {
            return List.of();
        }
    }

    private static TransformationRegistry registry() {
        return new TransformationRegistry(
                List.of(new StubReader(SourceFormat.MII_2025), new StubReader(SourceFormat.MII_2026)),
                List.of(new StubWriter(TargetFormat.BBMRI_DE), new StubWriter(TargetFormat.MIABIS_V3)));
    }

    @Test
    void resolvesMii2025ToBbmri() {
        Transformation transformation =
                registry().findTransformationFor(SourceFormat.MII_2025, TargetFormat.BBMRI_DE);

        assertEquals(SourceFormat.MII_2025, transformation.reader().format());
        assertEquals(TargetFormat.BBMRI_DE, transformation.writer().format());
        assertEquals("MII_2025 to BBMRI_DE", transformation.describe());
    }

    @Test
    void resolvesMii2026ToBbmri() {
        assertEquals(SourceFormat.MII_2026,
                registry().findTransformationFor(SourceFormat.MII_2026, TargetFormat.BBMRI_DE).reader().format());
    }

    @Test
    void resolvesMii2025ToMiabis() {
        assertEquals(TargetFormat.MIABIS_V3,
                registry().findTransformationFor(SourceFormat.MII_2025, TargetFormat.MIABIS_V3).writer().format());
    }

    @Test
    void resolvesMii2026ToMiabis() {
        Transformation transformation =
                registry().findTransformationFor(SourceFormat.MII_2026, TargetFormat.MIABIS_V3);

        assertEquals("MII_2026 to MIABIS_V3", transformation.describe());
    }

    @Test
    void aPairWithNoReaderIsRefusedAndNamesTheSupportedPairs() {
        TransformationRegistry incomplete = new TransformationRegistry(
                List.of(new StubReader(SourceFormat.MII_2025)),
                List.of(new StubWriter(TargetFormat.BBMRI_DE), new StubWriter(TargetFormat.MIABIS_V3)));

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> incomplete.findTransformationFor(SourceFormat.MII_2026, TargetFormat.BBMRI_DE));
        assertTrue(thrown.getMessage().contains("reader"), thrown.getMessage());
    }

    @Test
    void twoReadersClaimingTheSameFormatFailFast() {
        assertThrows(IllegalStateException.class, () -> new TransformationRegistry(
                List.of(new StubReader(SourceFormat.MII_2025), new StubReader(SourceFormat.MII_2025)),
                List.of(new StubWriter(TargetFormat.BBMRI_DE))));
    }

    @Test
    void twoWritersClaimingTheSameFormatFailFast() {
        assertThrows(IllegalStateException.class, () -> new TransformationRegistry(
                List.of(new StubReader(SourceFormat.MII_2025)),
                List.of(new StubWriter(TargetFormat.BBMRI_DE), new StubWriter(TargetFormat.BBMRI_DE))));
    }

    @Test
    void theSupportedListNamesAllFourPairs() {
        String supported = registry().listSupportedPairs();

        assertTrue(supported.contains("MII_2025 to BBMRI_DE"), supported);
        assertTrue(supported.contains("MII_2026 to BBMRI_DE"), supported);
        assertTrue(supported.contains("MII_2025 to MIABIS_V3"), supported);
        assertTrue(supported.contains("MII_2026 to MIABIS_V3"), supported);
    }

    @Test
    void theSameReaderInstanceIsHandedBackEachTime() {
        TransformationRegistry registry = registry();

        assertSame(registry.findTransformationFor(SourceFormat.MII_2025, TargetFormat.BBMRI_DE).reader(),
                registry.findTransformationFor(SourceFormat.MII_2025, TargetFormat.MIABIS_V3).reader());
    }
}
