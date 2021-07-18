package com.hartwig.hmftools.sage.impact;

import static com.hartwig.hmftools.common.drivercatalog.panel.DriverGenePanelConfig.DRIVER_GENE_PANEL_OPTION;
import static com.hartwig.hmftools.common.ensemblcache.EnsemblDataCache.ENSEMBL_DATA_DIR;
import static com.hartwig.hmftools.common.genome.refgenome.RefGenomeSource.loadRefGenome;
import static com.hartwig.hmftools.common.utils.ConfigUtils.setLogLevel;
import static com.hartwig.hmftools.common.utils.FileWriterUtils.closeBufferedWriter;
import static com.hartwig.hmftools.common.utils.FileWriterUtils.createBufferedWriter;
import static com.hartwig.hmftools.common.variant.VariantConsequence.INTRAGENIC_VARIANT;
import static com.hartwig.hmftools.common.variant.VariantConsequence.NON_CODING_TRANSCRIPT_VARIANT;
import static com.hartwig.hmftools.common.variant.VariantConsequence.consequencesToString;
import static com.hartwig.hmftools.sage.SageCommon.SG_LOGGER;
import static com.hartwig.hmftools.sage.impact.ImpactConfig.REF_GENOME;
import static com.hartwig.hmftools.sage.impact.ImpactConstants.DELIM;
import static com.hartwig.hmftools.sage.impact.ImpactConstants.ITEM_DELIM;

import static htsjdk.tribble.AbstractFeatureReader.getFeatureReader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.StringJoiner;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.gene.GeneData;
import com.hartwig.hmftools.common.gene.TranscriptData;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeInterface;
import com.hartwig.hmftools.common.sage.SageMetaData;
import com.hartwig.hmftools.common.variant.impact.VariantImpact;
import com.hartwig.hmftools.common.variant.impact.VariantImpactSerialiser;
import com.hartwig.hmftools.common.variant.snpeff.SnpEffAnnotation;
import com.hartwig.hmftools.common.variant.snpeff.SnpEffAnnotationParser;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.jetbrains.annotations.NotNull;

import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFCodec;

public class ImpactAnnotator
{
    private final ImpactConfig mConfig;
    private final ImpactClassifier mImpactClassifier;
    private final VariantImpactBuilder mImpactBuilder;
    private final GeneDataCache mGeneDataCache;

    private BufferedWriter mCsvTranscriptWriter;

    public ImpactAnnotator(final CommandLine cmd)
    {
        mConfig = new ImpactConfig(cmd);

        mGeneDataCache = new GeneDataCache(
                cmd.getOptionValue(ENSEMBL_DATA_DIR), mConfig.RefGenVersion, cmd.getOptionValue(DRIVER_GENE_PANEL_OPTION));

        mImpactBuilder = new VariantImpactBuilder(mGeneDataCache);

        RefGenomeInterface refGenome = loadRefGenome(cmd.getOptionValue(REF_GENOME));

        mImpactClassifier = new ImpactClassifier(refGenome);

        if(mConfig.WriteTranscriptCsv)
            initialiseTranscriptWriter();
    }

    public void run()
    {
        if(!mConfig.singleSampleValid())
        {
            SG_LOGGER.error("invalid config, exiting");
            System.exit(1);
        }

        if(!mGeneDataCache.loadCache())
        {
            SG_LOGGER.error("Ensembl data cache loading failed, exiting");
            System.exit(1);
        }

        String sampleId = mConfig.SampleIds.get(0);
        processVcfFile(sampleId);

        closeBufferedWriter(mCsvTranscriptWriter);

        SG_LOGGER.info("sample({}) annotation complete", sampleId);
    }

    private void processVcfFile(final String sampleId)
    {
        SG_LOGGER.info("sample({}) reading VCF file({})", sampleId, mConfig.VcfFile);

        int variantCount = 0;

        try
        {
            final AbstractFeatureReader<VariantContext, LineIterator> reader = getFeatureReader(
                    mConfig.VcfFile, new VCFCodec(), false);

            for (VariantContext variantContext : reader.iterator())
            {
                // if (!filter.test(variantContext))
                //    continue;

                processVariant(variantContext);
            }
        }
        catch(IOException e)
        {
            SG_LOGGER.error(" failed to read somatic VCF file({}): {}", mConfig.VcfFile, e.toString());
        }

        SG_LOGGER.info("sample({}) processed {} variants", sampleId, variantCount);
    }

    private void processVariant(final VariantContext variantContext)
    {
        VariantData variant = VariantData.fromContext(variantContext);

        boolean phasedInframeIndel = variantContext.isIndel() && variantContext.getAttributeAsInt(SageMetaData.PHASED_INFRAME_INDEL, 0) > 0;

        variant.setVariantDetails(phasedInframeIndel, "", "");

        // extract SnpEff data for comparison sake
        List<SnpEffAnnotation> snpEffAnnotations = SnpEffAnnotationParser.fromContext(variantContext);

        List<GeneData> geneCandidates = mGeneDataCache.findGenes(variant.Chromosome, variant.Position);

        if(geneCandidates.isEmpty())
        {
            // could skip these if sure that valid genes aren't being incorrectly missed
            variant.addImpact(new VariantTransImpact(null, INTRAGENIC_VARIANT));

            if(!snpEffAnnotations.isEmpty())
            {
                for(SnpEffAnnotation annotation : snpEffAnnotations)
                {
                    String geneId = annotation.geneID();

                    if(geneId.isEmpty())
                        continue;

                    GeneData geneData = mGeneDataCache.getEnsemblCache().getGeneDataById(geneId);

                    if(geneData == null)
                    {
                        SG_LOGGER.debug("ignoring unknown gene({}:{})", annotation.geneID(), annotation.gene());
                    }
                    else
                    {
                        writeVariantTranscriptData(variant, geneData, snpEffAnnotations);
                    }
                }
            }
            else
            {
                writeVariantTranscriptData(variant, null, snpEffAnnotations);
            }

            return;
        }

        // analyse against each of the genes and their transcripts
        for(GeneData geneData : geneCandidates)
        {
            List<TranscriptData> transDataList = mGeneDataCache.findTranscripts(geneData.GeneId, variant.Position);

            if(transDataList.isEmpty())
            {
                variant.addImpact(new VariantTransImpact(null, NON_CODING_TRANSCRIPT_VARIANT));
                continue;
            }

            for(TranscriptData transData : transDataList)
            {
                VariantTransImpact transImpact = mImpactClassifier.classifyVariant(variant, transData);

                if(transImpact != null)
                    variant.addImpact(transImpact);

                // ++mTransVariantCount;
            }

            writeVariantTranscriptData(variant, geneData, snpEffAnnotations);
        }

        VariantImpact variantImpact = mImpactBuilder.createVariantImpact(variant);
    }

    private void initialiseVcfWriter()
    {
        // VariantImpactSerialiser.writeHeader();
    }

    private void writeVcfData()
    {
        // VariantImpactSerialiser.writeImpactDetails();
    }

    private void initialiseTranscriptWriter()
    {
        try
        {
            String transFileName = mConfig.OutputDir + mConfig.SampleIds.get(0) + ".sage.transcript_ann_compare.csv";
            mCsvTranscriptWriter = createBufferedWriter(transFileName, false);

            mCsvTranscriptWriter.write(VariantData.csvCommonHeader());
            mCsvTranscriptWriter.write(",Transcript,Consequence,ConsequenceEffect,SnpEffConsequence,SnpEffConsequenceEffect");
            mCsvTranscriptWriter.newLine();
        }
        catch(IOException e)
        {
            SG_LOGGER.error("failed to initialise CSV file output: {}", e.toString());
            return;
        }
    }

    private void writeVariantTranscriptData(final VariantData variant, final GeneData geneData, final List<SnpEffAnnotation> annotations)
    {
        if(mCsvTranscriptWriter == null)
            return;

        try
        {
            List<String> transcriptLines = Lists.newArrayList();

            List<SnpEffAnnotation> matchedAnnotations = Lists.newArrayList();

            for(VariantTransImpact impact : variant.getImpacts())
            {
                if(impact.TransData == null)
                    continue;

                SnpEffAnnotation annotation = impact.findMatchingAnnotation(annotations);

                StringJoiner sj = new StringJoiner(DELIM);
                sj.add(variant.csvCommonData(geneData));

                sj.add(impact.TransData.TransName);
                sj.add(String.valueOf(impact.consequencesStr()));
                sj.add(String.valueOf(impact.effectsStr()));

                if(annotation != null)
                {
                    sj.add(consequencesToString(annotation.consequences(), ITEM_DELIM));
                    sj.add(annotation.effects());
                    matchedAnnotations.add(annotation);
                }
                else
                {
                    sj.add("UNMATCHED");
                }

                transcriptLines.add(sj.toString());
            }

            for(SnpEffAnnotation annotation : annotations)
            {
                if(matchedAnnotations.contains(annotation))
                    continue;

                if(annotation.consequences().contains(NON_CODING_TRANSCRIPT_VARIANT))
                    continue;

                StringJoiner sj = new StringJoiner(DELIM);
                sj.add(variant.csvCommonData(geneData));

                sj.add(annotation.featureID());
                sj.add("UNMATCHED");
                sj.add(consequencesToString(annotation.consequences(), ITEM_DELIM));
                transcriptLines.add(sj.toString());
            }

            for(String transData : transcriptLines)
            {
                mCsvTranscriptWriter.write(transData);
                mCsvTranscriptWriter.newLine();
            }
        }
        catch(IOException e)
        {
            SG_LOGGER.error("failed to write variant CSV file: {}", e.toString());
            return;
        }
    }


    public static void main(@NotNull final String[] args) throws ParseException
    {
        final Options options = ImpactConfig.createOptions();
        final CommandLine cmd = createCommandLine(args, options);

        setLogLevel(cmd);

        ImpactAnnotator impactAnnotator = new ImpactAnnotator(cmd);
        impactAnnotator.run();
    }

    @NotNull
    private static CommandLine createCommandLine(@NotNull final String[] args, @NotNull final Options options) throws ParseException
    {
        final CommandLineParser parser = new DefaultParser();
        return parser.parse(options, args);
    }

}
