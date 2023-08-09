package com.hartwig.hmftools.amber;

import static com.hartwig.hmftools.amber.AmberUtils.depthAsSite;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.amber.AmberSite;
import com.hartwig.hmftools.common.amber.BaseDepth;
import com.hartwig.hmftools.common.amber.ImmutableAmberSite;

import org.jetbrains.annotations.NotNull;

import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFConstants;
import htsjdk.variant.vcf.VCFFilterHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFStandardHeaderLines;

public class AmberVCF
{
    private static final String PASS = "PASS";

    private final AmberConfig mConfig;

    public AmberVCF(final AmberConfig config)
    {
        mConfig = config;
    }

    public void writeBAF(final String filename, final Collection<TumorBAF> tumorEvidence,
            final AmberHetNormalEvidence hetNormalEvidence)
    {
        final List<TumorBAF> list = Lists.newArrayList(tumorEvidence);
        Collections.sort(list);

        final VariantContextWriter writer =
                new VariantContextWriterBuilder().setOutputFile(filename).modifyOption(Options.INDEX_ON_THE_FLY, true).build();

        final VCFHeader header = header(mConfig.isTumorOnly() ? Collections.singletonList(mConfig.TumorId) : mConfig.allSamples());

        writer.setHeader(header);
        writer.writeHeader(header);

        final ListMultimap<AmberSite, Genotype> genotypeMap = ArrayListMultimap.create();
        for(final String sample : hetNormalEvidence.samples())
        {
            for(BaseDepth baseDepth : hetNormalEvidence.evidence(sample))
            {
                genotypeMap.put(depthAsSite(baseDepth), createGenotype(sample, baseDepth));
            }
        }

        for(final TumorBAF tumorBAF : list)
        {
            AmberSite tumorSite = tumorSite(tumorBAF);
            genotypeMap.put(tumorSite, createGenotype(tumorBAF));
            writer.add(create(tumorBAF, genotypeMap.get(tumorSite)));
        }

        writer.close();
    }

    private static AmberSite tumorSite(final TumorBAF baseDepth)
    {
        return ImmutableAmberSite.builder()
                .from(baseDepth)
                .snpCheck(false)
                .ref(baseDepth.Ref)
                .alt(baseDepth.Alt)
                .build();
    }

    void writeContamination(final String filename, final Collection<TumorContamination> evidence)
    {
        final List<TumorContamination> list = Lists.newArrayList(evidence);
        Collections.sort(list);

        final VariantContextWriter writer =
                new VariantContextWriterBuilder().setOutputFile(filename).modifyOption(Options.INDEX_ON_THE_FLY, true).build();
        final VCFHeader header = header(Lists.newArrayList(mConfig.primaryReference(), mConfig.TumorId));
        writer.setHeader(header);
        writer.writeHeader(header);

        list.forEach(x -> writer.add(create(x)));
        writer.close();
    }

    public static void writeBaseDepths(final String filename, final Collection<BaseDepth> baseDepths, String sampleName)
    {
        final List<BaseDepth> list = Lists.newArrayList(baseDepths);
        Collections.sort(list);

        final VariantContextWriter writer =
                new VariantContextWriterBuilder().setOutputFile(filename).modifyOption(Options.INDEX_ON_THE_FLY, true).build();
        final VCFHeader header = header(Collections.singletonList(sampleName));
        writer.setHeader(header);
        writer.writeHeader(header);

        list.forEach(x -> writer.add(create(x, sampleName)));
        writer.close();
    }

    @NotNull
    private static VariantContext create(final TumorBAF tumorBaf, final List<Genotype> genotypes)
    {

        final List<Allele> alleles = alleles(tumorBaf);

        final VariantContextBuilder builder = new VariantContextBuilder().chr(tumorBaf.chromosome())
                .start(tumorBaf.position())
                .computeEndFromAlleles(alleles, tumorBaf.position())
                .genotypes(genotypes)
                .alleles(alleles);

        final VariantContext context = builder.make();
        context.getCommonInfo().setLog10PError(tumorBaf.TumorAltQuality / -10d);
        return context;
    }

    @NotNull
    private VariantContext create(final TumorContamination contamination)
    {
        assert (contamination.normal().altSupport() == 0);

        final Allele ref = Allele.create(contamination.tumor().ref().toString(), true);
        final Allele alt = Allele.create(contamination.tumor().alt().toString(), false);

        final List<Allele> alleles = Lists.newArrayList(ref, alt);

        final Genotype tumor = new GenotypeBuilder(mConfig.TumorId).DP(contamination.tumor().readDepth())
                .AD(new int[] { contamination.tumor().refSupport(), contamination.tumor().altSupport() })
                .alleles(alleles)
                .make();

        final Genotype normal = new GenotypeBuilder(mConfig.primaryReference()).DP(contamination.normal().readDepth())
                .AD(new int[] { contamination.normal().refSupport(), contamination.normal().altSupport() })
                .alleles(alleles)
                .make();

        final VariantContextBuilder builder = new VariantContextBuilder().chr(contamination.chromosome())
                .start(contamination.position())
                .computeEndFromAlleles(alleles, contamination.position())
                .genotypes(tumor, normal)
                .alleles(alleles);

        return builder.make();
    }

    @NotNull
    private static VariantContext create(final BaseDepth snp, String sampleName)
    {
        final List<Allele> alleles = Lists.newArrayList();
        alleles.add(Allele.create(snp.ref().toString(), true));
        alleles.add(Allele.create(snp.alt().toString(), false));

        final List<Integer> adField = Lists.newArrayList();
        adField.add(snp.RefSupport);
        adField.add(snp.AltSupport);

        final Genotype normal = new GenotypeBuilder(sampleName)
                .DP(snp.ReadDepth)
                .AD(adField.stream().mapToInt(i -> i).toArray())
                .alleles(alleles)
                .make();

        final VariantContextBuilder builder = new VariantContextBuilder().chr(snp.chromosome())
                .start(snp.position())
                .computeEndFromAlleles(alleles, snp.position())
                .genotypes(normal)
                .alleles(alleles);

        return builder.make();
    }

    private static VCFHeader header(final List<String> samples)
    {
        VCFHeader header = new VCFHeader(Collections.emptySet(), Lists.newArrayList(samples));
        header.addMetaDataLine(VCFStandardHeaderLines.getFormatLine((VCFConstants.GENOTYPE_KEY)));
        header.addMetaDataLine(VCFStandardHeaderLines.getFormatLine((VCFConstants.GENOTYPE_ALLELE_DEPTHS)));
        header.addMetaDataLine(VCFStandardHeaderLines.getFormatLine((VCFConstants.DEPTH_KEY)));
        header.addMetaDataLine(new VCFFilterHeaderLine(PASS, "All filters passed"));

        return header;
    }

    private static Genotype createGenotype(final String sample, final BaseDepth depth)
    {
        return new GenotypeBuilder(sample)
                .DP(depth.ReadDepth)
                .AD(new int[] { depth.RefSupport, depth.AltSupport })
                .alleles(alleles(depth))
                .make();
    }

    private Genotype createGenotype(final TumorBAF tumorBaf)
    {
        return new GenotypeBuilder(mConfig.TumorId).DP(tumorBaf.TumorReadDepth)
                .AD(new int[] { tumorBaf.TumorRefSupport, tumorBaf.TumorAltSupport })
                .alleles(alleles(tumorBaf))
                .make();
    }

    private static List<Allele> alleles(final BaseDepth baseDepth)
    {
        final Allele ref = Allele.create(baseDepth.ref().toString(), true);
        final Allele alt = Allele.create(baseDepth.alt().toString(), false);

        return Lists.newArrayList(ref, alt);
    }

    private static List<Allele> alleles(final TumorBAF baf)
    {
        final Allele ref = Allele.create(baf.Ref, true);
        final Allele alt = Allele.create(baf.Alt, false);

        return Lists.newArrayList(ref, alt);
    }

}
