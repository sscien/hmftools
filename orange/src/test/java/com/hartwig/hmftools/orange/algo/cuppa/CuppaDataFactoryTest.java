package com.hartwig.hmftools.orange.algo.cuppa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.io.Resources;
import com.hartwig.hmftools.common.cuppa2.CuppaPredictions;
import com.hartwig.hmftools.datamodel.cuppa.CuppaData;
import com.hartwig.hmftools.datamodel.cuppa.CuppaPrediction;
import com.hartwig.hmftools.datamodel.cuppa.ImmutableCuppaPrediction;

import org.junit.Test;

public class CuppaDataFactoryTest
{
    private static final double EPSILON = 1.0E-10;

    private static final String CUPPA_VIS_DATA_TSV = Resources.getResource("test_run/cuppa/tumor_sample.cuppa.vis_data.tsv").getPath();

    @Test
    public void canExtractPredictionsFromCuppaV2() throws IOException
    {
        CuppaPredictions cuppaPredictions = CuppaPredictions.fromTsv(CUPPA_VIS_DATA_TSV);

        CuppaData cuppaData = CuppaDataFactory.create(cuppaPredictions);
        List<CuppaPrediction> predictionEntries = cuppaData.predictions();
        //predictionEntries.stream().forEach(o -> System.out.println(o.toString()));

        assertEquals(40, predictionEntries.size());
        assertEquals(cuppaData.bestPrediction().cancerType().toString(), "Skin: Melanoma");

        Map<String, CuppaPrediction> actualPredictionsByCancerType = new HashMap<>();
        for(CuppaPrediction prediction : predictionEntries)
        {
            actualPredictionsByCancerType.put(prediction.cancerType(), prediction);
        }

        Map<String, CuppaPrediction> expectedPredictionsByCancerType = new HashMap<>();
        expectedPredictionsByCancerType.put(
                "Skin: Melanoma",
                ImmutableCuppaPrediction.builder()
                        .cancerType("Skin: Melanoma").likelihood(0.9968)
                        .genomicPositionClassifier(0.9966).snvPairwiseClassifier(0.8176).featureClassifier(0.7976)
                        .expressionPairwiseClassifier(1.0).altSjCohortClassifier(0.9999)
                        .build()
        );
        expectedPredictionsByCancerType.put(
                "Skin: Other",
                ImmutableCuppaPrediction.builder()
                        .cancerType("Skin: Other").likelihood(1.005E-4)
                        .genomicPositionClassifier(4.215E-4).snvPairwiseClassifier(0.1806).featureClassifier(4.02E-5)
                        .expressionPairwiseClassifier(8.853E-6).altSjCohortClassifier(1.528E-7)
                        .build()
        );
        expectedPredictionsByCancerType.put(
                "Prostate",
                ImmutableCuppaPrediction.builder()
                        .cancerType("Prostate").likelihood(1.005E-4)
                        .genomicPositionClassifier(9.123E-5).snvPairwiseClassifier(5.793E-12).featureClassifier(3.039E-4)
                        .expressionPairwiseClassifier(7.483E-7).altSjCohortClassifier(9.215E-7)
                        .build()
        );

        for(String cancerType : expectedPredictionsByCancerType.keySet())
        {
            CuppaPrediction expectedPrediction = expectedPredictionsByCancerType.get(cancerType);
            CuppaPrediction actualPrediction = actualPredictionsByCancerType.get(cancerType);

            assertEquals(expectedPrediction.genomicPositionClassifier(), actualPrediction.genomicPositionClassifier(), EPSILON);
            assertEquals(expectedPrediction.snvPairwiseClassifier(), actualPrediction.snvPairwiseClassifier(), EPSILON);
            assertEquals(expectedPrediction.featureClassifier(), actualPrediction.featureClassifier(), EPSILON);
            assertEquals(expectedPrediction.expressionPairwiseClassifier(), actualPrediction.expressionPairwiseClassifier(), EPSILON);
            assertEquals(expectedPrediction.altSjCohortClassifier(), actualPrediction.altSjCohortClassifier(), EPSILON);
        }
    }

    @Test
    public void canGetCorrectSvFeatureValue() throws IOException
    {
        CuppaPredictions cuppaPredictions = CuppaPredictions.fromTsv(CUPPA_VIS_DATA_TSV);
        Integer featureValue = CuppaDataFactory.getFeatureValue(cuppaPredictions, "sv.MAX_COMPLEX_SIZE").intValue();
        Integer expectedFeatureValue = 751;
        assertEquals(expectedFeatureValue, featureValue);
    }

    @Test
    public void doNotCrashOnMissingEntries()
    {
        CuppaPredictions cuppaPredictions = new CuppaPredictions(new ArrayList<>());
        assertNotNull(CuppaDataFactory.create(cuppaPredictions));
    }
}