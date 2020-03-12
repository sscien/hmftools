package com.hartwig.hmftools.isofox.novel;

import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.createBufferedWriter;
import static com.hartwig.hmftools.isofox.IsofoxConfig.ISF_LOGGER;
import static com.hartwig.hmftools.isofox.common.RnaUtils.positionWithin;
import static com.hartwig.hmftools.linx.types.SvVarData.SE_END;
import static com.hartwig.hmftools.linx.types.SvVarData.SE_PAIR;
import static com.hartwig.hmftools.linx.types.SvVarData.SE_START;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.isofox.IsofoxConfig;
import com.hartwig.hmftools.isofox.common.GeneCollection;
import com.hartwig.hmftools.isofox.common.GeneReadData;
import com.hartwig.hmftools.isofox.common.ReadRecord;
import com.hartwig.hmftools.isofox.common.RegionMatchType;
import com.hartwig.hmftools.isofox.common.RegionReadData;
import com.hartwig.hmftools.isofox.common.TransMatchType;

public class RetainedIntronFinder
{
    private GeneCollection mGenes;
    private final List<RetainedIntron> mRetainedIntrons;
    private final BufferedWriter mWriter;

    private static final int MIN_FRAG_COUNT = 3;
    private static final int MIN_SPLICED_FRAG_COUNT = 1;

    public RetainedIntronFinder(final BufferedWriter writer)
    {
        mRetainedIntrons = Lists.newArrayList();
        mWriter = writer;
        mGenes = null;
    }

    public void setGeneData(final GeneCollection genes)
    {
        mGenes = genes;
        mRetainedIntrons.clear();
    }

    public final List<RetainedIntron> getRetainedIntrons() { return mRetainedIntrons; }

    public int[] getPositionsRange()
    {
        int[] positionsRange = new int[SE_PAIR];

        positionsRange[SE_START] = mRetainedIntrons.stream().mapToInt(x -> (int)x.position()).min().orElse(-1);
        positionsRange[SE_END] = mRetainedIntrons.stream().mapToInt(x -> (int)x.position()).max().orElse(-1);

        return positionsRange;
    }

    public void evaluateFragmentReads(final ReadRecord read1, final ReadRecord read2)
    {
        // reads must span an exon boundary without being exonic in another transcript

        // scenarios: exon-intron read plus:
        // -  exonic read
        // - intronic read
        // - spliced read
        // - another exon-intron read - same exon - dismissed as likely unspliced
        final List<Integer> splicedTrans = Lists.newArrayList();

        List<RetainedIntron> retIntrons = Lists.newArrayList();

        for (int i = 0; i <= 1; ++i)
        {
            ReadRecord read = (i == 0) ? read1 : read2;

            if(read.containsSplit())
            {
                splicedTrans.addAll(read.getTranscriptClassifications().entrySet().stream()
                        .filter(x -> x.getValue() == TransMatchType.SPLICE_JUNCTION)
                        .map(x -> x.getKey()).collect(Collectors.toList()));

                continue;
            }

            RetainedIntron retIntron = evaluateRead(read);

            if(retIntron == null)
                continue;

            retIntrons.add(retIntron);
        }

        // skip any fragments where the bounds of an exon are spanned on both sides
        if(retIntrons.size() == 2)
        {
            RetainedIntron retIntron1 = retIntrons.get(0);
            RetainedIntron retIntron2 = retIntrons.get(1);

             if(retIntron1.isStart() != retIntron2.isStart())
             {
                 if(retIntron1.regions().stream().anyMatch(x -> retIntron2.regions().contains(x)))
                 {
                     ISF_LOGGER.trace("reads({}) support the same exon from exon-intron reads", read1.Id);
                     return;
                 }
             }
             else if(retIntron1.matches(retIntron2))
             {
                 // remove one since both reads support the same retained intron
                 retIntrons.remove(retIntron2);
             }
        }

        for(RetainedIntron retIntron : retIntrons)
        {
            boolean hasSpliceSupport = retIntron.regions().stream().anyMatch(x -> splicedTrans.stream().anyMatch(y -> x.hasTransId(y)));

            RetainedIntron existingRetIntron = mRetainedIntrons.stream().filter(x -> x.matches(retIntron)).findFirst().orElse(null);

            if (existingRetIntron != null)
            {
                existingRetIntron.addFragmentCount(hasSpliceSupport);
            }
            else
            {
                retIntron.addFragmentCount(hasSpliceSupport);
                mRetainedIntrons.add(retIntron);
            }
        }
    }

    private RetainedIntron evaluateRead(ReadRecord read)
    {
        long spannedPosition = 0;
        boolean spannedIsStart = false;

        final List<RegionReadData> candidateRegions = Lists.newArrayList();

        if(read.getMappedRegions().values().stream().anyMatch(x -> x != RegionMatchType.EXON_INTRON))
            return null;

        for(Map.Entry<RegionReadData,RegionMatchType> entry : read.getMappedRegions().entrySet())
        {
            RegionReadData region = entry.getKey();

            // check each end in turn
            for (int se = SE_START; se <= SE_END; ++se)
            {
                boolean usesStart = se == SE_START;
                long regionPos = usesStart ? region.start() : region.end();

                if (!read.getMappedRegionCoords().stream().anyMatch(x -> positionWithin(regionPos, x[SE_START], x[SE_END])))
                    continue;

                // cannot be the last or first exon
                if ((usesStart && region.getPreRegions().isEmpty()) || (!usesStart && region.getPostRegions().isEmpty()))
                    continue;

                spannedIsStart = usesStart;

                // take the outer-most region(s) if there are more than one
                if (!candidateRegions.isEmpty())
                {
                    if (usesStart)
                    {
                        if (regionPos > spannedPosition)
                            continue;

                        if (spannedPosition > regionPos)
                        {
                            candidateRegions.clear();
                            spannedPosition = regionPos;
                        }
                    }
                    else
                    {
                        if (regionPos < spannedPosition)
                            continue;

                        if (spannedPosition < regionPos)
                        {
                            candidateRegions.clear();
                            spannedPosition = regionPos;
                        }
                    }
                }
                else
                {
                    spannedPosition = regionPos;
                }

                candidateRegions.add(region);
            }
        }

        if(candidateRegions.isEmpty())
            return null;

        RetainedIntron retIntron = new RetainedIntron(candidateRegions, spannedIsStart);

        ISF_LOGGER.trace("retained intron({}) supported by read({})", retIntron, read);

        return retIntron;
    }

    public void setPositionDepthFromRead(final List<long[]> readCoords)
    {
        long readMinPos = readCoords.get(0)[SE_START];
        long readMaxPos = readCoords.get(readCoords.size() - 1)[SE_END];

        for(RetainedIntron retIntron : mRetainedIntrons)
        {
            int position = (int) retIntron.position();

            if(!positionWithin(position, readMinPos, readMaxPos))
                continue;

            if (readCoords.stream().anyMatch(x -> positionWithin(position, x[SE_START], x[SE_END])))
            {
                retIntron.addReadDepth();
            }
        }
    }

    /*
    Average unspliced coverage of gene
    Pair intron retentions with novel 5’SS, novel 3’ SS or other retained intron evidence.
     */

    public static BufferedWriter createWriter(final IsofoxConfig config)
    {
        try
        {
            final String outputFileName = config.formOutputFile("retained_intron.csv");

            BufferedWriter writer = createBufferedWriter(outputFileName, false);
            writer.write("GeneId,GeneName,Chromosome,Strand,Position");
            writer.write(",Type,FragCount,SplicedFragCount,TotalDepth,TranscriptInfo");
            writer.newLine();
            return writer;
        }
        catch (IOException e)
        {
            ISF_LOGGER.error("failed to create retained intron writer: {}", e.toString());
            return null;
        }
    }

    public void writeRetainedIntrons()
    {
        if(mWriter != null)
        {
            writeRetainedIntrons(mWriter, mRetainedIntrons, mGenes.genes());
        }
    }

    private synchronized static void writeRetainedIntrons(
            final BufferedWriter writer, final List<RetainedIntron> retainedIntrons, final List<GeneReadData> genes)
    {
        try
        {
            for(final RetainedIntron retIntron: retainedIntrons)
            {
                if(retIntron.getFragmentCount() < MIN_FRAG_COUNT && retIntron.getSplicedFragmentCount() < MIN_SPLICED_FRAG_COUNT)
                    continue;

                for(final GeneReadData gene : genes)
                {
                    // log if the gene can be linked to one of the transcripts
                    if(!gene.getTranscripts().stream().anyMatch(x -> retIntron.regions().stream().anyMatch(y -> y.hasTransId(x.TransId))))
                        continue;

                    writer.write(String.format("%s,%s,%s,%d",
                            gene.GeneData.GeneId, gene.GeneData.GeneName,
                            gene.GeneData.Chromosome, gene.GeneData.Strand));

                    writer.write(String.format(",%d,%s,%d,%d,%d,%s",
                            retIntron.position(), retIntron.type(gene.GeneData.forwardStrand()), retIntron.getFragmentCount(),
                            retIntron.getSplicedFragmentCount(), retIntron.getDepth(), retIntron.transcriptInfo()));

                    writer.newLine();
                }
            }
        }
        catch(IOException e)
        {
            ISF_LOGGER.error("failed to write retained intron file: {}", e.toString());
        }
    }

}
