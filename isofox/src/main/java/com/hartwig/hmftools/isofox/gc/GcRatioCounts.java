package com.hartwig.hmftools.isofox.gc;

import static java.lang.Math.floor;
import static java.lang.Math.round;

import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.createBufferedWriter;
import static com.hartwig.hmftools.isofox.IsofoxConfig.GC_RATIO_BUCKET;
import static com.hartwig.hmftools.isofox.IsofoxConfig.ISF_LOGGER;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.ensemblcache.EnsemblGeneData;
import com.hartwig.hmftools.common.utils.Doubles;
import com.hartwig.hmftools.isofox.IsofoxConfig;

public class GcRatioCounts
{
    private final List<Double> mRatios; // the ratios
    private final List<Double> mFrequencies; // the frequencies of the ratios

    public static final double DEFAULT_GC_RATIO_BUCKET = 0.01;

    public GcRatioCounts()
    {
        mRatios = Lists.newArrayList();
        mFrequencies = Lists.newArrayList();

        buildCache();
    }

    public final List<Double> getRatios() { return mRatios; }
    public final List<Double> getFrequencies() { return mFrequencies; }

    private void buildCache()
    {
        double gcRatio = 0;

        while(Doubles.lessOrEqual(gcRatio, 1))
        {
            mRatios.add(gcRatio);
            mFrequencies.add(0.0);
            gcRatio += GC_RATIO_BUCKET;
        }
    }

    public static double roundGcRatio(double ratio)
    {
        return round(ratio/GC_RATIO_BUCKET) * GC_RATIO_BUCKET;
    }

    public static double calcGcRatio(final String bases)
    {
        int gcCount = 0;
        for (int i = 0; i < bases.length(); ++i)
        {
            if (bases.charAt(i) == 'C' || bases.charAt(i) == 'G')
                ++gcCount;
        }

        double ratio = gcCount / (double) bases.length();
        return ratio;
    }

    public void clearCounts()
    {
        for(int i = 0; i < mFrequencies.size(); ++i)
        {
            mFrequencies.set(i, 0.0);
        }
    }

    public void addGcRatio(double gcRatio)
    {
        // split proportionally amongst the 2 closest buckets
        double lowerRatio = floor(gcRatio/GC_RATIO_BUCKET) * GC_RATIO_BUCKET;
        double upperRatio = lowerRatio + GC_RATIO_BUCKET;

        if(upperRatio >= 1)
        {
            addGcRatioCount(gcRatio, 1);
        }
        else
        {
            double upperFraction = (gcRatio - lowerRatio) / GC_RATIO_BUCKET;
            double lowerFraction = 1 - upperFraction;
            addGcRatioCount(lowerRatio, lowerFraction);
            addGcRatioCount(upperRatio, upperFraction);
        }
    }

    private void addGcRatioCount(double gcRatio, double count)
    {
        int ratioIndex = getRatioIndex(gcRatio);
        mFrequencies.set(ratioIndex, mFrequencies.get(ratioIndex) + count);
    }

    private int getRatioIndex(double gcRatio)
    {
        return (int)round(gcRatio * mRatios.size());
    }

    public void mergeRatioCounts(final List<Double> otherCounts)
    {
        if(otherCounts.size() != mFrequencies.size())
            return;

        for(int i = 0; i < mFrequencies.size(); ++i)
        {
            mFrequencies.set(i, mFrequencies.get(i) + otherCounts.get(i));
        }
    }

    public double getPercentileRatio(double percentile)
    {
        double totalCounts = mFrequencies.stream().mapToDouble(x -> x).sum();

        long currentTotal = 0;
        double prevRatio = 0;

        for(int i = 0; i < mRatios.size(); ++i)
        {
            double gcRatio = mRatios.get(i);
            double frequency = mFrequencies.get(i);

            double nextPercTotal = (currentTotal + frequency) / totalCounts;

            if(nextPercTotal >= percentile)
            {
                double medianRatio = prevRatio > 0 ? (prevRatio + gcRatio) * 0.5 : gcRatio;
                return medianRatio;
            }

            currentTotal += frequency;
            prevRatio = gcRatio;
        }

        return 0;
    }

    public static BufferedWriter createReadGcRatioWriter(final IsofoxConfig config)
    {
        try
        {
            final String outputFileName = config.formOutputFile("read_gc_ratios.csv");

            BufferedWriter writer = createBufferedWriter(outputFileName, false);
            writer.write("GeneId,GeneName,GcRatio,Count");
            writer.newLine();
            return writer;
        }
        catch (IOException e)
        {
            ISF_LOGGER.error("failed to create GC ratio writer: {}", e.toString());
            return null;
        }
    }

    public synchronized static void writeReadGcRatioCounts(
            final BufferedWriter writer, final EnsemblGeneData geneData, final GcRatioCounts ratioCounts)
    {
        try
        {
            for(int i = 0; i < ratioCounts.getRatios().size(); ++i)
            {
                double gcRatio = ratioCounts.getRatios().get(i);
                double frequency = ratioCounts.getFrequencies().get(i);

                if(frequency == 0)
                    continue;

                writer.write(String.format("%s,%s,%.4f,%.0f",
                        geneData != null ? geneData.GeneId : "ALL", geneData != null ? geneData.GeneName : "ALL",
                        gcRatio, frequency));

                writer.newLine();
            }
        }
        catch(IOException e)
        {
            ISF_LOGGER.error("failed to write GC ratio file: {}", e.toString());
        }
    }

}
