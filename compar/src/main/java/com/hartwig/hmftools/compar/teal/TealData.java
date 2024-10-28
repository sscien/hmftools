package com.hartwig.hmftools.compar.teal;

import static java.lang.String.format;

import static com.hartwig.hmftools.compar.common.Category.TELOMERE_LENGTH;
import static com.hartwig.hmftools.compar.common.DiffFunctions.checkDiff;
import static com.hartwig.hmftools.compar.common.MismatchType.VALUE;

import java.util.ArrayList;
import java.util.List;

import com.hartwig.hmftools.common.teal.TelomereLength;
import com.hartwig.hmftools.compar.ComparableItem;
import com.hartwig.hmftools.compar.common.Category;
import com.hartwig.hmftools.compar.common.DiffThresholds;
import com.hartwig.hmftools.compar.common.MatchLevel;
import com.hartwig.hmftools.compar.common.Mismatch;

public class TealData implements ComparableItem
{
    public final TelomereLength TelomereLength;

    protected static final String FLD_TELOMERE_LENGTH = "TelomereLength";

    public TealData(final TelomereLength telomereLength)
    {
        TelomereLength = telomereLength;
    }

    @Override
    public Category category() { return TELOMERE_LENGTH; }

    @Override
    public String key()
    {
        return String.format("%s", TelomereLength.type());
    }

    @Override
    public List<String> displayValues()
    {
        return List.of(format("%s", TelomereLength.finalTelomereLength()));
    }

    @Override
    public boolean reportable() { return true; }

    @Override
    public boolean matches(final ComparableItem other)
    {
        final TelomereLength otherTelomereLength = ((TealData) other).TelomereLength;

        return TelomereLength.type().equals(otherTelomereLength.type());
    }

    @Override
    public Mismatch findMismatch(final ComparableItem other, final MatchLevel matchLevel, final DiffThresholds thresholds)
    {
        final TelomereLength otherTelomereLength = ((TealData) other).TelomereLength;

        final List<String> diffs = new ArrayList<>();

        checkDiff(diffs, FLD_TELOMERE_LENGTH, TelomereLength.finalTelomereLength(), otherTelomereLength.finalTelomereLength(), thresholds);

        return !diffs.isEmpty() ? new Mismatch(this, other, VALUE, diffs) : null;
    }

    public String toString()
    {
        return format("type(%s) telomere length(%.3f)",
            TelomereLength.type(), TelomereLength.finalTelomereLength());
    }
}