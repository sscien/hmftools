package com.hartwig.hmftools.orange.algo.purple;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import com.hartwig.hmftools.common.variant.CodingEffect;
import com.hartwig.hmftools.common.variant.impact.VariantEffect;

import org.junit.Test;

public class CodingEffectDeterminerTest
{
    @Test
    public void testEmptyEffectListReturnsNoneCodingEffect()
    {
        var emptyVariantEffectList = new ArrayList<VariantEffect>();
        //act
        CodingEffect actualCodingEffect = CodingEffectDeterminer.determineCodingEffect(emptyVariantEffectList);

        assertEquals(CodingEffect.NONE, actualCodingEffect);
    }

    @Test
    public void testNonsenseOrFrameshiftDetermination()
    {
        var variantEffects = List.of(VariantEffect.START_LOST);
        // act
        CodingEffect actualCodingEffect = CodingEffectDeterminer.determineCodingEffect(variantEffects);

        assertEquals(CodingEffect.NONSENSE_OR_FRAMESHIFT, actualCodingEffect);
    }

    @Test
    public void testSpliceCodingEffectDetermination()
    {
        var variantEffects = List.of(VariantEffect.SPLICE_DONOR);
        //act
        CodingEffect actualCodingEffect = CodingEffectDeterminer.determineCodingEffect(variantEffects);

        assertEquals(CodingEffect.SPLICE, actualCodingEffect);
    }

    @Test
    public void testMissenseEffectDetermination()
    {
        var variantEffects = List.of(VariantEffect.MISSENSE);
        //act
        CodingEffect actualCodingEffect = CodingEffectDeterminer.determineCodingEffect(variantEffects);

        assertEquals(CodingEffect.MISSENSE, actualCodingEffect);
    }

    @Test
    public void testSynonymousEffectDetermination()
    {
        var variantEffects = List.of(VariantEffect.SYNONYMOUS);
        //act
        CodingEffect actualCodingEffect = CodingEffectDeterminer.determineCodingEffect(variantEffects);

        assertEquals(CodingEffect.SYNONYMOUS, actualCodingEffect);
    }

    @Test
    public void testOtherEffectDetermination()
    {
        var variantEffects = List.of(VariantEffect.INTRONIC);
        //act
        CodingEffect actualCodingEffect = CodingEffectDeterminer.determineCodingEffect(variantEffects);

        assertEquals(CodingEffect.NONE, actualCodingEffect);
    }

    @Test
    public void nonsenseOrFrameshiftTakesPrecedenceOverSplice()
    {
        var variantEffects = List.of(VariantEffect.START_LOST, VariantEffect.SPLICE_DONOR);
        //act
        CodingEffect actualCodingEffect = CodingEffectDeterminer.determineCodingEffect(variantEffects);

        assertEquals(CodingEffect.NONSENSE_OR_FRAMESHIFT, actualCodingEffect);
    }

    @Test
    public void spliceTakesPrecedenceOverMissense()
    {
        var variantEffects = List.of(VariantEffect.MISSENSE, VariantEffect.SPLICE_DONOR);
        //act
        CodingEffect actualCodingEffect = CodingEffectDeterminer.determineCodingEffect(variantEffects);

        assertEquals(CodingEffect.SPLICE, actualCodingEffect);
    }

    @Test
    public void missenseTakesPrecedenceOverSynonymous()
    {
        var variantEffects = List.of(VariantEffect.MISSENSE, VariantEffect.SYNONYMOUS);
        //act
        CodingEffect actualCodingEffect = CodingEffectDeterminer.determineCodingEffect(variantEffects);

        assertEquals(CodingEffect.MISSENSE, actualCodingEffect);
    }

    @Test
    public void synonymousTakesPrecedenceOverNone()
    {
        var variantEffects = List.of(VariantEffect.INTRONIC, VariantEffect.SYNONYMOUS);
        //act
        CodingEffect actualCodingEffect = CodingEffectDeterminer.determineCodingEffect(variantEffects);

        assertEquals(CodingEffect.SYNONYMOUS, actualCodingEffect);
    }
}
