package com.hartwig.hmftools.common.purple.copynumber;

import static org.junit.Assert.assertEquals;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.purple.PurityAdjuster;
import com.hartwig.hmftools.common.purple.PurpleDatamodelTest;
import com.hartwig.hmftools.common.purple.gender.Gender;
import com.hartwig.hmftools.common.purple.region.FittedRegion;
import com.hartwig.hmftools.common.purple.region.ObservedRegionStatus;
import com.hartwig.hmftools.common.purple.segment.SegmentSupport;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class SomaticExtensionTest {

    private final static String CHROMOSOME = "1";
    private final static double EPSILON = 1e-10;
    private final static PurityAdjuster PURE = new PurityAdjuster(Gender.FEMALE, 1d, 1d);


    @Test
    public void testInvalidGermlineGetsIgnored() {
        final FittedRegion somatic = createFittedRegion(1, 10000, 3.0, ObservedRegionStatus.SOMATIC, SegmentSupport.BND);
        final FittedRegion germline =
                createFittedRegion(10001, 20000, 4, ObservedRegionStatus.GERMLINE_AMPLIFICATION, SegmentSupport.NONE);
        final List<FittedRegion> regions = Lists.newArrayList(somatic, germline);

        final List<FittedRegion> result = SomaticExtension.fittedRegions(PURE, regions);
        assertEquals(1, result.size());
        assertRegion(1, 20000, 3, result.get(0));
    }

    @Test
    public void testInvalidGermlineIsKeptWithSVSupport() {
        final FittedRegion somatic = createFittedRegion(1, 10000, 3.0, ObservedRegionStatus.SOMATIC, SegmentSupport.BND);
        final FittedRegion germline =
                createFittedRegion(10001, 20000, 4.0, ObservedRegionStatus.GERMLINE_AMPLIFICATION, SegmentSupport.BND);
        final List<FittedRegion> regions = Lists.newArrayList(somatic, germline);

        final List<FittedRegion> result = SomaticExtension.fittedRegions(PURE, regions);
        assertEquals(2, result.size());
        assertRegion(1, 10000, 3, result.get(0));
        assertRegion(10001, 20000, 4, result.get(1));
    }

    private void assertRegion(long start, long end, double tumorCopyNumber, @NotNull final FittedRegion victim) {
        assertEquals(start, victim.start());
        assertEquals(end, victim.end());
        assertEquals(tumorCopyNumber, victim.tumorCopyNumber(), EPSILON);
    }

    private FittedRegion createFittedRegion(long start, long end, double tumorCopyNumber, ObservedRegionStatus status,
            SegmentSupport support) {
        return createFittedRegion(start, end, tumorCopyNumber, 1.1, status, support);
    }

    private FittedRegion createFittedRegion(long start, long end, double tumorCopyNumber, double observedNormalRatio,
            ObservedRegionStatus status, SegmentSupport support) {
        return PurpleDatamodelTest.createDefaultFittedRegion(CHROMOSOME, start, end)
                .status(status)
                .tumorCopyNumber(tumorCopyNumber)
                .refNormalisedCopyNumber(tumorCopyNumber)
                .observedNormalRatio(observedNormalRatio)
                .bafCount(50)
                .support(support)
                .build();
    }


}
