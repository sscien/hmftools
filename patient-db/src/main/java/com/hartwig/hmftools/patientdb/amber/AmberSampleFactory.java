package com.hartwig.hmftools.patientdb.amber;

import java.util.List;

import com.hartwig.hmftools.common.amber.BaseDepth;
import com.hartwig.hmftools.common.amber.NormalHeterozygousFilter;

public class AmberSampleFactory
{
    private final int mMminReadDepth;
    private final NormalHeterozygousFilter mHeterozygousFilter;

    public AmberSampleFactory(final int minReadDepth, final double minHetAFPercentage, final double maxHetAFPercentage)
    {
        mMminReadDepth = minReadDepth;
        mHeterozygousFilter = new NormalHeterozygousFilter(minHetAFPercentage, maxHetAFPercentage);
    }

    public AmberSample fromBaseDepth(final String sample, final List<BaseDepth> baseDepths)
    {
        byte[] entries = new byte[baseDepths.size()];
        for(int i = 0; i < baseDepths.size(); i++)
        {
            entries[i] = asByte(baseDepths.get(i));
        }

        return ImmutableAmberSample.builder().sampleId(sample).entries(entries).build();
    }

    public byte asByte(BaseDepth depth)
    {
        if(!depth.isValid() || depth.ReadDepth < mMminReadDepth)
        {
            return AmberSample.DO_NOT_MATCH;
        }

        if(depth.RefSupport == depth.ReadDepth)
        {
            return (byte) 1;
        }

        if(mHeterozygousFilter.test(depth))
        {
            return (byte) 2;
        }

        if(depth.AltSupport == depth.ReadDepth)
        {
            return (byte) 3;
        }

        return AmberSample.DO_NOT_MATCH;
    }
}
