package com.hartwig.hmftools.linx.visualiser.data;

import static org.junit.Assert.assertEquals;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.linx.visualiser.circos.SegmentTerminal;

import org.junit.Test;

public class SegmentsTest
{
    @Test
    public void testTrackStrategies()
    {

        final List<Segment> segments = Lists.newArrayList(createSegment("1"), createSegment("2"), createSegment("1"));

        final List<Segment> alwaysIncrement = Segments.alwaysIncrement(segments);
        assertEquals(3, alwaysIncrement.size());
        assertEquals(1, alwaysIncrement.get(0).track());
        assertEquals(2, alwaysIncrement.get(1).track());
        assertEquals(3, alwaysIncrement.get(2).track());

        final List<Segment> incrementOnChromosome = Segments.incrementOnChromosome(segments);
        assertEquals(3, incrementOnChromosome.size());
        assertEquals(1, incrementOnChromosome.get(0).track());
        assertEquals(1, incrementOnChromosome.get(1).track());
        assertEquals(2, incrementOnChromosome.get(2).track());
    }

    private static Segment createSegment(String chromosome)
    {
        return ImmutableSegment.builder()
                .sampleId("sampleId")
                .clusterId(1)
                .chainId(1)
                .chromosome(chromosome)
                .start(1)
                .end(1000)
                .track(1)
                .startTerminal(SegmentTerminal.NONE)
                .endTerminal(SegmentTerminal.NONE)
                .ploidy(0)
                .build();
    }

}
