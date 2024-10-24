package com.hartwig.hmftools.esvee;

import static com.hartwig.hmftools.esvee.common.SvConstants.DEFAULT_DISCORDANT_FRAGMENT_LENGTH;
import static com.hartwig.hmftools.esvee.common.SvConstants.MIN_ANCHOR_LENGTH;
import static com.hartwig.hmftools.esvee.common.SvConstants.MIN_INDEL_SUPPORT_LENGTH;
import static com.hartwig.hmftools.esvee.common.SvConstants.MIN_MAP_QUALITY;
import static com.hartwig.hmftools.esvee.common.SvConstants.MIN_VARIANT_LENGTH;

import java.util.List;

import com.hartwig.hmftools.common.genome.region.Orientation;
import com.hartwig.hmftools.common.region.ChrBaseRegion;

public final class AssemblyConstants
{
    // BAM reading
    public static final int BAM_READ_JUNCTION_BUFFER = 1000;

    // read adjustments
    public static final int INDEL_TO_SC_MIN_SIZE_SOFTCLIP = MIN_INDEL_SUPPORT_LENGTH;
    public static final int INDEL_TO_SC_MAX_SIZE_SOFTCLIP = MIN_VARIANT_LENGTH - 1;
    public static final int POLY_G_TRIM_LENGTH = 4;
    public static final double LOW_BASE_TRIM_PERC = 0.35;
    public static final int UNMAPPED_TRIM_THRESHOLD = 40;

    // primary assembly
    public static final int MIN_SOFT_CLIP_LENGTH = MIN_VARIANT_LENGTH;;
    public static final int DECOY_MAX_MISMATCHES = 3;
    public static final int ASSEMBLY_MIN_READ_SUPPORT = 2;
    public static final int ASSEMBLY_SPLIT_MIN_READ_SUPPORT = 5;
    public static final double PRIMARY_ASSEMBLY_SPLIT_MIN_READ_SUPPORT_PERC = 0.2;
    public static final int PROXIMATE_REF_SIDE_SOFT_CLIPS = 3;
    public static final int ASSEMBLY_MIN_SOFT_CLIP_LENGTH = MIN_VARIANT_LENGTH;
    public static final int ASSEMBLY_MIN_SOFT_CLIP_SECONDARY_LENGTH = ASSEMBLY_MIN_SOFT_CLIP_LENGTH / 2;
    public static final int ASSEMBLY_MAX_JUNC_POS_DIFF = 2;
    public static final int ASSEMBLY_REF_READ_MIN_SOFT_CLIP = 10;
    public static final int ASSEMBLY_MIN_EXTENSION_READ_HIGH_QUAL_MATCH = 2;
    public static final int ASSEMBLY_DISCORDANT_MIN_MAP_QUALITY = MIN_MAP_QUALITY;

    public static final int DEFAULT_ASSEMBLY_MAP_QUAL_THRESHOLD = 10;

    public static final int PRIMARY_ASSEMBLY_MERGE_MISMATCH = 3;
    public static final int PROXIMATE_JUNCTION_DISTANCE = 50;

    // discordant fragment max upper bound is dynamically set from the fragment distribution
    public static int DISCORDANT_FRAGMENT_LENGTH = DEFAULT_DISCORDANT_FRAGMENT_LENGTH;

    // assembly extension
    public static final int ASSEMBLY_READ_OVERLAP_BASES = 20;
    public static final int ASSEMBLY_LINK_OVERLAP_BASES = 50;
    public static final int ASSEMBLY_EXTENSION_BASE_MISMATCH = 2;
    public static final int ASSEMBLY_REF_BASE_MAX_GAP = 200;
    public static final int REF_SIDE_MIN_SOFT_CLIP_LENGTH = MIN_SOFT_CLIP_LENGTH;

    public static final int LOCAL_ASSEMBLY_MATCH_DISTANCE = 500;
    public static final int MATCH_SUBSEQUENCE_LENGTH = 20;

    // phasing
    public static final int REMOTE_PHASING_MIN_READS = 2;
    public static final int REMOTE_REGION_MERGE_MARGIN = 150;
    public static final int REMOTE_REGION_REF_MIN_READS = REMOTE_PHASING_MIN_READS;
    public static final double REMOTE_REGION_REF_MIN_READ_PERCENT = 0.1;
    public static final double REMOTE_REGION_WEAK_SUPP_PERCENT = 0.1;
    public static final int PHASED_ASSEMBLY_MIN_TI = 30;
    public static final int PHASED_ASSEMBLY_MAX_TI = 1000;
    public static final int PROXIMATE_DEL_LENGTH = 1000;
    public static final int PROXIMATE_DUP_LENGTH = 500;

    // output
    public static final int DEFAULT_ASSEMBLY_REF_BASE_WRITE_MAX = 200; // for TSV and VCF output, no function impact

    // alignment
    public static final int ALIGNMENT_MIN_SOFT_CLIP = MIN_VARIANT_LENGTH;
    public static final int ALIGNMENT_MIN_MOD_MAP_QUAL = 10;
    public static final int ALIGNMENT_MIN_MOD_MAP_QUAL_NO_XA = 5;
    public static final int ALIGNMENT_CALC_SCORE_FACTOR = 15;
    public static final double ALIGNMENT_CALC_SCORE_THRESHOLD = 0.85;
    public static final int ALIGNMENT_INDEL_MIN_ANCHOR_LENGTH = MIN_ANCHOR_LENGTH;
    public static final int ALIGNMENT_LOW_MOD_MQ_VARIANT_LENGTH = 50000;
    public static final int ALIGNMENT_LOW_MOD_MQ_QUAL_BOOST = 15;
    public static final int ALIGNMENT_MIN_ADJUST_ALIGN_LENGTH = MIN_ANCHOR_LENGTH;

    public static final int SHORT_DEL_DUP_INS_LENGTH = 1000;

    // DUX-4 regions
    public static final List<ChrBaseRegion> MULTI_MAPPED_ALT_ALIGNMENT_REGIONS_V37 = List.of(
            new ChrBaseRegion("4", 190930000, 191030000),
            new ChrBaseRegion("10", 135420000, 135520000 ));

    public static final List<ChrBaseRegion> MULTI_MAPPED_ALT_ALIGNMENT_REGIONS_V38 = List.of(
            new ChrBaseRegion("chr4", 190060000, 190190000),
            new ChrBaseRegion("chr10", 133660000, 133770000));

    // SSX2 region
    public static final ChrBaseRegion SSX2_REGION_V37 = new ChrBaseRegion("X", 52725946, 52736239);
    public static final Orientation SSX2_GENE_ORIENT = Orientation.REVERSE;
    public static final int SSX2_MAX_MAP_QUAL = 20;
    public static final ChrBaseRegion SSX2_REGION_V38 = new ChrBaseRegion("chrX", 52696896, 52707189);
}
