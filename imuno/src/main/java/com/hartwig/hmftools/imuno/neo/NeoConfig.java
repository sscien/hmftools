package com.hartwig.hmftools.imuno.neo;

import static com.hartwig.hmftools.common.genome.refgenome.RefGenomeSource.loadRefGenome;
import static com.hartwig.hmftools.common.neo.NeoEpitopeFile.DELIMITER;
import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.OUTPUT_DIR;
import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.parseOutputDir;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_END;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_PAIR;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_START;
import static com.hartwig.hmftools.imuno.common.ImunoCommon.IM_LOGGER;
import static com.hartwig.hmftools.imuno.common.ImunoCommon.LOG_DEBUG;
import static com.hartwig.hmftools.imuno.common.ImunoCommon.loadGeneIdsFile;
import static com.hartwig.hmftools.imuno.common.ImunoCommon.loadSampleDataFile;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeInterface;
import com.hartwig.hmftools.patientdb.dao.DatabaseAccess;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class NeoConfig
{
    public final List<SampleData> Samples;
    public final int[] PeptideLengths;

    public final RefGenomeInterface RefGenome;

    public final List<String> RestrictedGeneIds;

    public final int RequiredAminoAcids;
    public final boolean WriteTransData;
    public final boolean WriteCohortFile;
    public final String SvFusionsDir;
    public final String OutputDir;

    public static final String SAMPLE = "sample";
    public static final String CANCER_TYPE = "cancer_type";
    public static final String HLA_TYPES = "hla_types";
    public static final String PEPTIDE_LENGTHS = "peptide_lengths";

    public static final String GENE_TRANSCRIPTS_DIR = "gene_transcripts_dir";
    public static final String SV_FUSION_DATA_DIR = "sv_fusion_data_dir";
    public static final String GENE_ID_FILE = "gene_id_file";
    public static final String REF_GENOME = "ref_genome";
    public static final String CANCER_TPM_FILE = "cancer_tpm_file";
    public static final String REQ_AMINO_ACIDS = "req_amino_acids";
    public static final String WRITE_TRANS_DATA = "write_trans_data";
    public static final String WRITE_COHORT_FILE = "write_cohort_file";

    public static final int DEFAULT_AMINO_ACID_REF_COUNT = 18;

    public NeoConfig(final CommandLine cmd)
    {
        Samples = Lists.newArrayList();

        final String sampleIdConfig = cmd.getOptionValue(SAMPLE);

        if(sampleIdConfig.contains(".csv"))
        {
            loadSampleDataFile(sampleIdConfig, Samples);
        }
        else
        {
            if(sampleIdConfig.contains(DELIMITER))
            {
                SampleData sample = SampleData.fromCsv(sampleIdConfig);

                if(sample == null)
                {
                    IM_LOGGER.error("invalid sample data: {}", sampleIdConfig);
                }
                else
                {
                    Samples.add(sample);
                }
            }
            else
            {
                Samples.add(new SampleData(sampleIdConfig, "", Lists.newArrayList()));
            }
        }

        PeptideLengths = new int[SE_PAIR];

        if(cmd.hasOption(PEPTIDE_LENGTHS))
        {
            String[] lengths = cmd.getOptionValue(PEPTIDE_LENGTHS).split("-");

            if(lengths.length == 1)
            {
                PeptideLengths[SE_START] = PeptideLengths[SE_END] = Integer.parseInt(lengths[0]);
            }
            else if(lengths.length == 2)
            {
                PeptideLengths[SE_START] = Integer.parseInt(lengths[SE_START]);
                PeptideLengths[SE_END] = Integer.parseInt(lengths[SE_END]);
            }
            else
            {
                IM_LOGGER.error("invalid peptide lengths: {}", cmd.getOptionValue(PEPTIDE_LENGTHS));
            }
        }

        final String refGenomeFilename = cmd.getOptionValue(REF_GENOME);
        RefGenome = loadRefGenome(refGenomeFilename);

        RestrictedGeneIds = Lists.newArrayList();
        SvFusionsDir = cmd.getOptionValue(SV_FUSION_DATA_DIR);
        OutputDir = parseOutputDir(cmd);

        RequiredAminoAcids = Integer.parseInt(cmd.getOptionValue(REQ_AMINO_ACIDS, String.valueOf(DEFAULT_AMINO_ACID_REF_COUNT)));
        WriteTransData = cmd.hasOption(WRITE_TRANS_DATA);
        WriteCohortFile = cmd.hasOption(WRITE_COHORT_FILE);

        if(cmd.hasOption(GENE_ID_FILE))
        {
            loadGeneIdsFile(cmd.getOptionValue(GENE_ID_FILE), RestrictedGeneIds);
        }
    }

    public boolean isMultiSample() { return Samples.size() > 1; }

    public NeoConfig(
            final List<SampleData> samples, final RefGenomeInterface refGenome, final List<String> restrictedGeneIds,
            final int requiredAminoAcids)
    {
        Samples = samples;
        PeptideLengths = new int[SE_PAIR];
        RefGenome = refGenome;
        RestrictedGeneIds = restrictedGeneIds;
        RequiredAminoAcids = requiredAminoAcids;
        SvFusionsDir = "";
        OutputDir = "";
        WriteTransData = false;
        WriteCohortFile = false;
    }

    public static void addCmdLineArgs(Options options)
    {
        options.addOption(SAMPLE, true, "Sample - Id(s) separated by ';' or CSV file");
        options.addOption(CANCER_TYPE, true, "Tumor cancer type (optional) - to retrieve cancer median TPM");
        options.addOption(HLA_TYPES, true, "Sample HLA types, separated by ';'");
        options.addOption(PEPTIDE_LENGTHS, true, "Peptide length min-max, separated by '-', eg 8-12");
        options.addOption(GENE_TRANSCRIPTS_DIR, true, "Ensembl data cache directory");
        options.addOption(GENE_ID_FILE, true, "Restrict to specific genes");
        options.addOption(REF_GENOME, true, "Ref genome");
        options.addOption(SV_FUSION_DATA_DIR, true, "SV fusion file (single sample or cohort)");
        options.addOption(CANCER_TPM_FILE, true, "TPM per cancer type and pan-cancer");
        options.addOption(WRITE_TRANS_DATA, false, "Write transcript data for each neo-epitope");
        options.addOption(WRITE_COHORT_FILE, false, "Write cohort files for multiple samples");
        options.addOption(REQ_AMINO_ACIDS, true, "Number of amino acids in neo-epitopes (default: 18)");
        options.addOption(OUTPUT_DIR, true, "Output directory");
        options.addOption(LOG_DEBUG, false, "Log verbose");
        DatabaseAccess.addDatabaseCmdLineArgs(options);
    }



}
