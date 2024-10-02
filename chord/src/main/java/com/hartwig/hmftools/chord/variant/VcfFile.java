package com.hartwig.hmftools.chord.variant;

import static com.hartwig.hmftools.chord.ChordConstants.CHORD_LOGGER;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;

import com.hartwig.hmftools.common.variant.VcfFileReader;

import htsjdk.variant.variantcontext.VariantContext;

public class VcfFile
{
    public final String mPath;
    private final boolean mPassOnly;

    public VcfFile(String path, boolean passOnly)
    {
        mPath = path;
        mPassOnly = passOnly;
    }

    public List<VariantContext> loadVariants() throws NoSuchFileException
    {
        if(!new File(mPath).isFile())
            throw new NoSuchFileException(mPath);

        VcfFileReader vcfFileReader = new VcfFileReader(mPath);

        List<VariantContext> variants = new ArrayList<>();

        for(VariantContext variantContext : vcfFileReader.iterator())
        {
            boolean isPassVariant = variantContext.getFilters().isEmpty();

            if(mPassOnly && !isPassVariant)
                continue;

            variants.add(variantContext);
        }

        if(variants.size()==0)
            CHORD_LOGGER.warn("No {}variants found in vcf file: {}",
                    (mPassOnly) ? "PASS " : "",
                    mPath
            );

        return variants;
    }
}
