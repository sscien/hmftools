package com.hartwig.hmftools.lilac.read

import com.hartwig.hmftools.common.genome.bed.NamedBed
import com.hartwig.hmftools.common.genome.region.*
import com.hartwig.hmftools.lilac.nuc.NucleotideFragment
import com.hartwig.hmftools.lilac.nuc.NucleotideFragmentFactory
import com.hartwig.hmftools.lilac.sam.SAMSlicer
import htsjdk.samtools.AlignmentBlock
import htsjdk.samtools.SAMRecord
import htsjdk.samtools.SamReaderFactory
import org.apache.logging.log4j.LogManager
import java.io.File
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.min

class SAMRecordReader(maxDistance: Int, private val refGenome: String, private val transcripts: List<HmfTranscriptRegion>, private val factory: NucleotideFragmentFactory) {
    private val codingRegions = transcripts.map { GenomeRegions.create(it.chromosome(), it.codingStart() - maxDistance, it.codingEnd() + maxDistance) }

    companion object {
        val logger = LogManager.getLogger(this::class.java)
    }

    fun readFromBam(bamFile: String): List<NucleotideFragment> {
        return transcripts.flatMap { readFromBam(it, bamFile) }
    }

    private fun readFromBam(transcript: HmfTranscriptRegion, bamFile: String): List<NucleotideFragment> {
        logger.info("... querying ${transcript.gene()} (${transcript.chromosome()}:${transcript.codingStart()}-${transcript.codingEnd()})")

        val reverseStrand = transcript.strand() == Strand.REVERSE
        val codingRegions = if (reverseStrand) codingRegions(transcript).reversed() else codingRegions(transcript)

        val realignedRegions = mutableListOf<NucleotideFragment>()
        var length = 0
        for (codingRegion in codingRegions) {
            realignedRegions.addAll(realign(length, codingRegion, reverseStrand, bamFile))
            length += codingRegion.bases().toInt()
        }

        return realignedRegions
                .groupBy { it.id }
                .map { it.value.reduce {x, y  -> NucleotideFragment.merge(x, y)} }
    }

    private fun codingRegions(transcript: HmfTranscriptRegion): List<NamedBed> {
        return CodingRegions.codingRegions(transcript)
    }

    private fun samReaderFactory(): SamReaderFactory {
        val default = SamReaderFactory.makeDefault()
        return if (refGenome.isNotEmpty()) {
            default.referenceSequence(File(refGenome))
        } else  {
            default
        }
    }

    private fun realign(hlaCodingRegionOffset: Int, region: NamedBed, reverseStrand: Boolean, bamFileName: String): List<NucleotideFragment> {
        val slicer = SAMSlicer(1)
        val result = mutableListOf<NucleotideFragment>()
        samReaderFactory().open(File(bamFileName)).use { samReader ->
            val consumer = Consumer<SAMRecord> { samRecord ->
                if (samRecord.bothEndsinRangeOfCodingTranscripts()) {
                    val fragment = factory.createFragment(samRecord, reverseStrand, hlaCodingRegionOffset, region)
                    fragment?.let { result.add(it) }
                }
            }

            slicer.slice(region, samReader, consumer)
        }
        return result
    }

    private fun AlignmentBlock.getReferenceEnd(): Int {
        return this.referenceStart + this.length - 1
    }

    private fun realignForwardStrand(gene: String, hlaExonOffset: Int, region: GenomeRegion, alignmentBlock: AlignmentBlock, samRecord: SAMRecord): SAMRecordRead {
        val alignmentStart = alignmentBlock.referenceStart
        val alignmentEnd = alignmentBlock.getReferenceEnd()
        val alignmentStartReadIndex = alignmentBlock.readStart - 1

        val hlaExonStartPosition = region.start().toInt()
        val hlaExonEndPosition = region.end().toInt()

        val hlaStart = max(alignmentStart, hlaExonStartPosition)
        val hlaEnd = min(alignmentEnd, hlaExonEndPosition)
        val length = hlaEnd - hlaStart + 1

        val readIndex = alignmentStartReadIndex + hlaStart - alignmentStart
        val hlaStartIndex = hlaStart - hlaExonStartPosition + hlaExonOffset

        return SAMRecordRead(gene, hlaStartIndex, readIndex, length, false, samRecord)
    }


    private fun realignReverseStrand(gene: String, hlaExonOffset: Int, region: GenomeRegion, alignmentBlock: AlignmentBlock, samRecord: SAMRecord): SAMRecordRead {
        val alignmentStart = alignmentBlock.referenceStart
        val alignmentEnd = alignmentBlock.getReferenceEnd()
        val alignmentStartReadIndex = alignmentBlock.readStart - 1

        val hlaExonStartPosition = region.end().toInt()
        val hlaExonEndPosition = region.start().toInt()

        val hlaStart = min(alignmentEnd, hlaExonStartPosition)
        val hlaEnd = max(alignmentStart, hlaExonEndPosition)
        val length = hlaStart - hlaEnd + 1

        val readIndex = alignmentStartReadIndex + hlaStart - alignmentStart
        val hlaStartIndex = hlaExonStartPosition - hlaStart + hlaExonOffset

        return SAMRecordRead(gene, hlaStartIndex, readIndex, length, true, samRecord)
    }

    private fun SAMRecord.bothEndsinRangeOfCodingTranscripts(): Boolean {
        val thisInRange = codingRegions.any { it.chromosome() == this.contig && this.alignmentStart >= it.start() && this.alignmentStart <= it.end() }
        val mateInRange = codingRegions.any { it.chromosome() == this.contig && this.mateAlignmentStart >= it.start() && this.mateAlignmentStart <= it.end() }

        return thisInRange && mateInRange
    }

}