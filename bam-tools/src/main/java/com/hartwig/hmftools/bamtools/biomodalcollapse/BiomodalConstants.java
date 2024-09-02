package com.hartwig.hmftools.bamtools.biomodalcollapse;

public class BiomodalConstants
{
    public static int LOW_QUAL_CUTOFF = 30;

    public static int MIN_RESOLVED_READ_LENGTH = 30;
    public static int PREFIX_TRIM_LENGTH = 1;
    public static float LOW_QUAL_TRIM_PROPORTION_THRESHOLD = 0.7f;
    public static float MISSING_BASE_TRIM_PROPORTION_THRESHOLD = 0.14f;

    public static final String FORWARD_HAIRPIN = "AATGACGATGCGTTCGAGCATCGTTATT";
    public static final String REVERSE_HAIRPIN = "AATAACGATGCTCGAACGCATCGTCATT";

    public static final byte MISSING_BASE = (byte) 'N';
    public static final byte MODC_BASE = (byte) 'c';
    public static final byte MISMATCH_BASE = (byte) 'X';
    public static final byte INS_BASE = (byte) '_';

    public static final int OPEN_GAP_PENALTY = 5;
    public static final int EXTEND_GAP_PENALTY = 1;

    public static final String STAT_DELIMITER = "\t";
    public static final String[] STAT_HEADERS = {
            "read_name",
            "read1_length",
            "read2_length",
            "read1",
            "qual1",
            "read2",
            "qual2",
            "hairpin1_start_pos",
            "hairpin1_8mer_match_count",
            "hairpin1_suffix_match_length",
            "hairpin2_start_pos",
            "hairpin2_8mer_match_count",
            "hairpin2_suffix_match_length",
            "rev_comp_read_shift1",
            "rev_comp_high_qual_mismatch_count",
            "rev_comp_high_qual_mismatch_proportion",
            "rev_comp_total_mismatch_count",
            "rev_comp_total_mismatch_proportion",
            "naive_forward_consensus_read",
            "naive_forward_consensus_qual",
            "missing_count",
            "mismatch_count",
            "high_qual_GG_mismatch_count",
            "high_qual_other_mismatch_count",
            "low_qual_unambiguous_count",
            "low_qual_ambiguous_count",
            "methC_G_count",
            "methC_other_count",
            "read1_rc",
            "qual1_rc",
            "read2_rc",
            "qual2_rc",
            "naive_reverse_consensus_read",
            "naive_reverse_consensus_qual",
            "aligned_read1",
            "aligned_qual1",
            "aligned_read2",
            "aligned_qual2",
            "forward_cigar",
            "forward_match_count",
            "forward_insert1_count",
            "forward_insert2_count",
            "forward_indel_count",
            "forward_consensus_read",
            "forward_consensus_qual",
            "aligned_missing_count",
            "aligned_mismatch_count",
            "aligned_high_qual_GG_mismatch_count",
            "aligned_high_qual_other_mismatch_count",
            "aligned_low_qual_unambiguous_count",
            "aligned_low_qual_ambiguous_count",
            "aligned_methC_G_count",
            "aligned_methC_other_count",
            "rc_aligned_read1",
            "rc_aligned_qual1",
            "rc_aligned_read2",
            "rc_aligned_qual2",
            "rc_alignment_end_pos",
            "reverse_cigar",
            "reverse_match_count",
            "reverse_insert1_count",
            "reverse_insert2_count",
            "reverse_indel_count",
            "reverse_consensus_read",
            "reverse_consensus_qual",
            "aligned_forward_consensus_read",
            "aligned_forward_consensus_qual",
            "aligned_reverse_consensus_read",
            "aligned_reverse_consensus_qual",
            "reverse_consensus_trim_count",
            "modC_C_mismatch_count",
            "C_modC_mismatch_count",
            "final_consensus_read",
            "final_consensus_qual",
            "prefix_trim_count",
            "suffix_trim_count",
            "trimmed_final_consensus_read",
            "trimmed_final_consensus_qual",
            "biomodal_read",
            "biomodal_qual",
            "trimmed_final_consensus_length",
            "biomodal_length",
            "aligned_trimmed_final_consensus_read",
            "aligned_trimmed_final_consensus_qual",
            "aligned_biomodal_read",
            "aligned_biomodal_qual",
            "biomodal_cigar",
            "biomodal_offset",
            "biomodal_overlapping_count",
            "biomodal_N_mismatches",
            "biomodal_nonN_mismatches",
            "biomodal_indel_count"
    };

    public static final String COMPARE_DELIMITER = "\t";

    public static final String[] COMPARE_HEADERS = {
            "read_name",
            "ref_read",
            "ref_qual",
            "new_read",
            "new_qual",
            "ref_read_length",
            "ref_strand",
            "ref_cigar",
            "ref_mapq",
            "ref_chr",
            "ref_pos",
            "ref_alignment_length",
            "ref_missing_count",
            "ref_nonN_mismatches_<20",
            "ref_nonN_mismatches_20-30",
            "ref_nonN_mismatches_30+",
            "ref_M_count",
            "ref_left_soft_clip_length",
            "ref_right_soft_clip_length",
            "ref_S_count",
            "ref_I+D_count",
            "ref_short_inv_supp_count",
            "ref_other_supp_count",
            "ref_short_inv_supp_info",
            "ref_other_supp_info",
            "ref_MD",
            "new_read_length",
            "new_strand",
            "new_cigar",
            "new_mapq",
            "new_chr",
            "new_pos",
            "new_alignment_length",
            "new_missing_count",
            "new_nonN_mismatches_<20",
            "new_nonN_mismatches_20-30",
            "new_nonN_mismatches_30+",
            "new_M_count",
            "new_left_soft_clip_length",
            "new_right_soft_clip_length",
            "new_S_count",
            "new_I+D_count",
            "new_short_inv_supp_count",
            "new_other_supp_count",
            "new_short_inv_supp_info",
            "new_other_supp_info",
            "new_MD",
    };
}
