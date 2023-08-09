package com.hartwig.hmftools.cider.blastn

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import com.hartwig.hmftools.common.genome.region.Strand
import com.hartwig.hmftools.common.utils.file.FileWriterUtils.createBufferedReader
import com.hartwig.hmftools.common.utils.file.FileWriterUtils.createBufferedWriter
import org.apache.commons.csv.CSVFormat
import org.apache.logging.log4j.LogManager
import java.io.BufferedWriter
import java.io.File
import java.time.Duration
import java.time.Instant

// run blast and process results
object BlastnRunner
{
    // For the scoring function, the match/mismatch score 1/-4 optimizes the scoring for 100% identical sequences and 1/-1 for 75% identical
    // sequences. The default for NCBI Blastn is 2/-3, which is optimal for 89% identical sequences. BWA uses 1/-4.
    // There is also gap opening and gap extension. BWA uses gap opening of -6 and gap extension of -1.
    // For blastn default scoring, see: https://www.ncbi.nlm.nih.gov/books/NBK279684/

    // we set it to 1/-4/-5/-2 which is optimal for 100% identical sequences
    // My test shows that this scoring would mostly prefer shorter matches with higher identity than longer matches with lower identity.
    const val MATCH_SCORE = 1
    const val MISMATCH_SCORE = -4
    const val GAP_OPENING_SCORE = -5
    const val GAP_EXTEND_SCORE = -2

    enum class BlastColumns
    {
        qseqid, qlen, sseqid, stitle, pident, qcovs, length, mismatch, gapopen,
        qstart, qend, sstart, send, qframe, sframe, evalue,
        bitscore, qseq, sseq
    }

    private val sLogger = LogManager.getLogger(BlastnRunner::class.java)

    // from my test, it evalue of 1 can only match minimum 20 bases. If we want to match D segment that is shorter
    // we will need a higher cut off, maybe 10, but will get many false positive hits that are longer but more mismatches
    fun runBlastn(sampleId: String, blastDir: String, blastDb: String, vdjSequences: Map<Int, String>, outputDir: String, numThreads: Int,
                  expectedValueCutoff: Double = 1.0)
    : Multimap<Int, BlastnMatch>
    {
        if (vdjSequences.isEmpty())
        {
            return ArrayListMultimap.create()
        }

        val start = Instant.now()

        //val fastaFile = File.createTempFile(sampleId, "blastn.fa")
        val fastaFile = "$outputDir/$sampleId.blastn.fa"

        writeBlastFasta(vdjSequences, fastaFile)

        val command = mutableListOf(
            "$blastDir/bin/blastn",
            "-db", "GCF_000001405.39_top_level",
            "-task", "blastn",
            // "-outfmt", "6 " + BLAST_COLUMNS.joinToString(" "),
            "-evalue", expectedValueCutoff.toString(),
            "-word_size", "9",
            "-reward", MATCH_SCORE.toString(),
            "-penalty", MISMATCH_SCORE.toString(),
            "-gapopen", (-GAP_OPENING_SCORE).toString(),
            "-gapextend", (-GAP_EXTEND_SCORE).toString(),
            "-mt_mode", "0",
            "-num_threads", numThreads.toString(),
            "-query", fastaFile)

        // create output file
        val outputFileCsv = File("$outputDir/$sampleId.blastn.csv")

        sLogger.info("running blastn on sample {}, {} sequences", sampleId, vdjSequences.size)

        /*
        val outputFile = File("$outputDir/$sampleId.blastn.out")
        val processBuilder1 = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).redirectOutput(outputFile)
        processBuilder1.environment()["BLASTDB"] = blastDb

        val result_ = processBuilder1.start().waitFor()
        if (result_ != 0)
        {
            sLogger.fatal("Error executing blastn")
        }*/

        // the output format to TSV
        command.add("-outfmt")
        command.add("6 " + BlastColumns.values().joinToString(" "))

        val processBuilder = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).redirectOutput(outputFileCsv)
        processBuilder.environment()["BLASTDB"] = blastDb

        sLogger.info("{}", processBuilder.command().joinToString(" "))

        val result = processBuilder.start().waitFor()
        if (result != 0)
        {
            sLogger.fatal("Error executing blastn")
        }

        val finish: Instant = Instant.now()
        val seconds: Long = Duration.between(start, finish).seconds
        sLogger.info("blastn run complete. Time taken: {}m {}s", seconds / 60, seconds % 60)

        val blastnMatches = processBlast(outputFileCsv.absolutePath)

        // delete the csv file
        outputFileCsv.delete()

        return blastnMatches
    }

    fun writeBlastFasta(vdjSequences: Map<Int, String>, fastaPath: String)
    {
        createBufferedWriter(fastaPath).use { writer: BufferedWriter ->
            for ((key, vdjSeq) in vdjSequences)
            {
                // each one assign an index
                writer.write(">$key\n")
                // writer.write(">${CiderFormatter.cdr3AminoAcid(vdjAnn.vdj)}\n")
                writer.write("${vdjSeq}\n")
            }
        }
    }

    fun processBlast(blastOutputCsv: String) : Multimap<Int, BlastnMatch>
    {
        val blastnResults: Multimap<Int, BlastnMatch> = ArrayListMultimap.create()

        // process them into a map of seq id -> BlastnMatch
        createBufferedReader(blastOutputCsv).use { reader ->
            val format = CSVFormat.Builder.create()
                .setDelimiter('\t')
                .setRecordSeparator('\n')
                .setHeader(BlastColumns::class.java)
                .build()

            for (record in format.parse(reader))
            {
                val qseqid = record[BlastColumns.qseqid].toInt()

                val blastnMatch = BlastnMatch(
                    querySeqLen = record[BlastColumns.qlen].toInt(),
                    subjectTitle = record[BlastColumns.stitle],
                    percentageIdent = record[BlastColumns.pident].toDouble(),
                    queryCoverage = record[BlastColumns.qcovs].toDouble(),
                    alignmentLength = record[BlastColumns.length].toInt(),
                    numMismatch = record[BlastColumns.mismatch].toInt(),
                    numGapOpenings = record[BlastColumns.gapopen].toInt(),
                    queryAlignStart = record[BlastColumns.qstart].toInt(),
                    queryAlignEnd = record[BlastColumns.qend].toInt(),
                    subjectAlignStart = record[BlastColumns.sstart].toInt(),
                    subjectAlignEnd = record[BlastColumns.send].toInt(),
                    queryFrame = record[BlastColumns.qframe].toInt(),
                    subjectFrame = Strand.valueOf(record[BlastColumns.sframe].toInt()),
                    expectedValue = record[BlastColumns.evalue].toDouble(),
                    bitScore = record[BlastColumns.bitscore].toDouble(),
                    alignedPartOfQuerySeq = record[BlastColumns.qseq],
                    alignedPartOfSubjectSeq = record[BlastColumns.sseq]
                )

                blastnResults.put(qseqid, blastnMatch)
            }
        }

        return blastnResults
    }
}
