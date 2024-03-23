package com.hartwig.hmftools.esvee.prep.types;

import com.hartwig.hmftools.common.region.ChrBaseRegion;

public class PartitionTask
{
    public final ChrBaseRegion Region;
    public final int TaskId;

    public PartitionTask(final ChrBaseRegion region, final int taskId)
    {
        Region = region;
        TaskId = taskId;
    }
}