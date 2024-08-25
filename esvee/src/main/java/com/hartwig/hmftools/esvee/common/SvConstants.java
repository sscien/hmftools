package com.hartwig.hmftools.esvee.common;

import static com.hartwig.hmftools.common.sv.LineElements.LINE_POLY_AT_REQ;

public final class SvConstants
{
    public static final String BAM_HEADER_SAMPLE_INDEX_TAG = "sampleId";

    // commonly used thresholds
    public static final int MIN_VARIANT_LENGTH = 32;
    public static final int DEFAULT_DISCORDANT_FRAGMENT_LENGTH = 1000; // default, otherwise set from BAM fragment sampling
    public static int LOW_BASE_QUAL_THRESHOLD = 26;

    // indels
    public static final int MIN_INDEL_SUPPORT_LENGTH = 5;
    public static final int MIN_INDEL_LENGTH = MIN_VARIANT_LENGTH;

    // qual calcs and filters
    public static final double QUAL_CALC_FRAG_SUPPORT_FACTOR = 5;

    // LINE elements
    public static final int LINE_MIN_EXTENSION_LENGTH = LINE_POLY_AT_REQ;
    public static final int LINE_REF_BASE_TEST_LENGTH = 8;
    public static final int LINE_INDEL_MAX_OVERLAP = 40;
    public static final int LINE_INDEL_MAX_GAP = 30;
}
