package com.hartwig.hmftools.knowledgebasegenerator.transvar;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TransvarConverterTest {

    @Test
    public void canConvertTransvarSNVLineToRecord() {
        String line =
                "MTOR:p.L2230V\tENST00000361445 (protein_coding)\tMTOR\t-\tchr1:g.11182158A>C/c.6688T>G/p.L2230V\tinside_[cds_in_exon_48]"
                        + "\tCSQN=Missense;reference_codon=TTA;candidate_codons=GTA,GTC,GTG,GTT;candidate_mnv_variants="
                        + "chr1:g.11182156_11182158delTAAinsGAC,chr1:g.11182156_11182158delTAAinsCAC,chr1:g.11182156_11182158delTAAinsAAC;"
                        + "aliases=ENSP00000354558;source=Ensembl";

        TransvarRecord record = TransvarConverter.toTransvarRecord(line);

        assertEquals("ENST00000361445", record.transcript());
        assertEquals("1", record.chromosome());
        assertEquals(11182158, record.gdnaPosition());
        assertEquals("A", record.gdnaRef());
        assertEquals("C", record.gdnaAlt());
        assertEquals("TTA", record.referenceCodon());
        assertEquals("GTA", record.candidateCodons().get(0));
        assertEquals("GTC", record.candidateCodons().get(1));
        assertEquals("GTG", record.candidateCodons().get(2));
        assertEquals("GTT", record.candidateCodons().get(3));
    }

    @Test
    public void canConvertTransvarMNVLineToRecord() {
        String line = "TET2:p.Y1294A\tENST00000540549 (protein_coding)\tTET2\t+\t"
                + "chr4:g.106180852_106180853delTAinsGC/c.3880_3881delTAinsGC/p.Y1294A\tinside_[cds_in_exon_7]\t"
                + "CSQN=Missense;reference_codon=TAC;candidate_codons=GCA,GCC,GCG,GCT;candidate_mnv_variants="
                + "chr4:g.106180852_106180854delTACinsGCA,chr4:g.106180852_106180854delTACinsGCG,chr4:"
                + "g.106180852_106180854delTACinsGCT;aliases=ENSP00000442788;source=Ensembl";

        TransvarRecord record = TransvarConverter.toTransvarRecord(line);

        assertEquals("ENST00000540549", record.transcript());
        assertEquals("4", record.chromosome());
        assertEquals(106180852, record.gdnaPosition());
        assertEquals("TA", record.gdnaRef());
        assertEquals("GC", record.gdnaAlt());
        assertEquals("TAC", record.referenceCodon());
        assertEquals("GCA", record.candidateCodons().get(0));
        assertEquals("GCC", record.candidateCodons().get(1));
        assertEquals("GCG", record.candidateCodons().get(2));
        assertEquals("GCT", record.candidateCodons().get(3));
    }
}