package com.hartwig.hmftools.compar.linx;

import static com.hartwig.hmftools.compar.common.Category.GERMLINE_SV;
import static com.hartwig.hmftools.compar.common.CommonUtils.FLD_QUAL;
import static com.hartwig.hmftools.compar.common.DiffFunctions.checkDiff;
import static com.hartwig.hmftools.compar.common.MismatchType.VALUE;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.linx.LinxBreakend;
import com.hartwig.hmftools.common.linx.LinxGermlineSv;
import com.hartwig.hmftools.common.region.BasePosition;
import com.hartwig.hmftools.compar.common.Category;
import com.hartwig.hmftools.compar.ComparableItem;
import com.hartwig.hmftools.compar.common.DiffThresholds;
import com.hartwig.hmftools.compar.common.MatchLevel;
import com.hartwig.hmftools.compar.common.Mismatch;

public class GermlineSvData implements ComparableItem
{
    public final LinxGermlineSv SvData;
    private final LinxBreakend Breakend;
    private final BasePosition mComparisonStartPosition;
    private final BasePosition mComparisonEndPosition;

    protected static final String FLD_GERMLINE_FRAGS = "GermlineFragments";

    public GermlineSvData(
            final LinxGermlineSv svData, final LinxBreakend breakend, final BasePosition comparisonStartPosition,
            final BasePosition comparisonEndPosition)
    {
        SvData = svData;
        Breakend = breakend;
        mComparisonStartPosition = comparisonStartPosition;
        mComparisonEndPosition = comparisonEndPosition;
    }

    @Override
    public Category category() { return GERMLINE_SV; }

    @Override
    public String key()
    {
        if(mComparisonStartPosition.Position != SvData.PositionStart || mComparisonEndPosition.Position != SvData.PositionEnd)
        {
            return String.format("%s:%s %s:%d:%d-%s:%d%d %s liftover(%s-%s)",
                    SvData.EventId, SvData.Type, SvData.ChromosomeStart, SvData.PositionStart, SvData.OrientStart,
                    SvData.ChromosomeEnd, SvData.PositionEnd, SvData.OrientEnd, Breakend.gene(),
                    mComparisonStartPosition, mComparisonEndPosition);
        }
        else
        {
            return String.format("%s:%s %s:%d:%d-%s:%d%d %s",
                    SvData.EventId, SvData.Type, SvData.ChromosomeStart, SvData.PositionStart, SvData.OrientStart,
                    SvData.ChromosomeEnd, SvData.PositionEnd, SvData.OrientEnd, Breakend.gene());
        }
    }

    @Override
    public List<String> displayValues()
    {
        List<String> values = LinxCommon.displayValuesBreakend(Breakend);
        values.add(String.format("%d", SvData.GermlineFragments));
        values.add(String.format("%d", (int) SvData.QualScore));
        return values;
    }

    @Override
    public boolean reportable()
    {
        return Breakend.reportedDisruption();
    }

    @Override
    public boolean matches(final ComparableItem other)
    {
        final GermlineSvData otherSv = (GermlineSvData)other;

        if(otherSv.SvData.Type != SvData.Type)
            return false;

        if(!otherSv.SvData.ChromosomeStart.equals(mComparisonStartPosition.Chromosome)
        || !otherSv.SvData.ChromosomeEnd.equals(mComparisonEndPosition.Chromosome))
            return false;

        if(otherSv.SvData.PositionStart != mComparisonStartPosition.Position
        || otherSv.SvData.PositionEnd != mComparisonEndPosition.Position)
            return false;

        if(otherSv.SvData.OrientStart != SvData.OrientStart || otherSv.SvData.OrientEnd != SvData.OrientEnd)
            return false;

        if(!otherSv.Breakend.gene().equals(Breakend.gene()))
            return false;

        return true;
    }

    @Override
    public Mismatch findMismatch(final ComparableItem other, final MatchLevel matchLevel, final DiffThresholds thresholds)
    {
        final GermlineSvData otherBreakend = (GermlineSvData)other;

        final List<String> diffs = Lists.newArrayList();

        LinxCommon.checkDiffsBreakends(diffs, Breakend, otherBreakend.Breakend, thresholds);

        checkDiff(diffs, FLD_GERMLINE_FRAGS, SvData.GermlineFragments, otherBreakend.SvData.GermlineFragments, thresholds);
        checkDiff(diffs, FLD_QUAL, (int) SvData.QualScore, (int) otherBreakend.SvData.QualScore, thresholds);

        return !diffs.isEmpty() ? new Mismatch(this, other, VALUE, diffs) : null;
    }
}
