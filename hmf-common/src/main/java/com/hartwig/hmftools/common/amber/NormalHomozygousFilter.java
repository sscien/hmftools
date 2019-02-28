package com.hartwig.hmftools.common.amber;

import java.util.function.Predicate;

public class NormalHomozygousFilter implements Predicate<BaseDepth> {
    @Override
    public boolean test(final BaseDepth bafEvidence) {
        return bafEvidence.isValid(1) && bafEvidence.altSupport() == 0;
    }

}
