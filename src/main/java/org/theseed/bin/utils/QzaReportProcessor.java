/**
 *
 */
package org.theseed.bin.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.counters.CountMap;
import org.theseed.io.TabbedLineReader;
import org.theseed.sequence.DnaKmers;
import org.theseed.sequence.SequenceKmers;
import org.theseed.sequence.fastq.FastqStream;
import org.theseed.sequence.fastq.SeqRead;
import org.theseed.utils.BaseReportProcessor;
import org.theseed.utils.ParseFailureException;

/**
 * This command processes a directory of Amplicon SSU rRNA samples to produce a report that can be used to build
 * an Xmatrix.  The positional parameter is the name of the input directory containing the samples and the name of
 * the four-column table for the appropriate RepGen set.  The following command-line options are supported.
 *
 * The report will be produced
 *
 * -h	display command-line usage
 * -v	display more frequent log messages
 * -o	output file (if not STDOUT)
 *
 * --source		type of input directory-- QZA or P3DIR
 * --phred		phred offset for quality strings in the FASTQ files (default 33)
 * --minSim		minimum similarity required for a good hit, as a fraction of the read length
 * --K			DNA kmer size
 * --minHits	minimum number of hits for a representative to be considered significant
 * --minQual	minimum acceptable quality for a read to be acceptable
 * --minLen		minimum acceptable length for a read to be acceptable
 *
 * @author Bruce Parrello
 *
 */
public class QzaReportProcessor extends BaseReportProcessor {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(QzaReportProcessor.class);
    /** map of repgen IDs to SSU rRNA kmer objects */
    private Map<String, DnaKmers> repGenSsuKmerMap;
    /** list of sample files */
    private File[] samples;

    // COMMAND-LINE OPTIONS

    /** format of input samples */
    @Option(name = "--source", usage = "format of samples in input directory")
    private FastqStream.Type sourceType;

    /** phred offset used to translate quality strings in FASTQ input */
    @Option(name = "--phred", metaVar = "50", usage = "zero-value offset for FASTQ quality strings")
    private int phredOffset;

    /** minimum amount of kmer hits required as fraction of read length */
    @Option(name = "--minSim", metaVar = "0.90", usage = "minimum fraction of read length that must be kmer hits")
    private double minSimFraction;

    /** minimum number of kmer hits for a representative genome to be considered significant */
    @Option(name = "--minHits", metaVar = "10", usage = "minimum number of reads hits for a representative genome to be considered present")
    private int minHitCount;

    /** minimum acceptable read length */
    @Option(name = "--minLen", metaVar = "100", usage = "minimum acceptable read length")
    private int minReadLen;

    /** minimum acceptable read quality */
    @Option(name = "--minQual", metaVar = "20.0", usage = "minimum acceptable read quality")
    private double minReadQual;

    /** kmer size */
    @Option(name = "--K", metaVar = "22", usage = "DNA kmer size")
    private int kmerSize;

    /** input directory name */
    @Argument(index = 0, metaVar = "inDir", usage = "input directory name", required = true)
    private File inDir;

    /** input repgen file (four-column table format */
    @Argument(index = 1, metaVar = "rep.seqs.tbl", usage = "input four-column repgen table.")
    private File repGenFile;

    @Override
    protected void setReporterDefaults() {
        this.sourceType = FastqStream.Type.QZA;
        this.phredOffset = 33;
        this.minSimFraction = 0.80;
        this.kmerSize = DnaKmers.kmerSize();
        this.minHitCount = 20;
        this.minReadLen = 50;
        this.minReadQual = 30.0;
    }

    @Override
    protected void validateReporterParms() throws IOException, ParseFailureException {
        // Verify the input directory is a directory.
        if (! this.inDir.isDirectory())
            throw new FileNotFoundException("Input directory " + this.inDir + " is not found or invalid.");
        // Get all the samples in the directory.
        this.samples = this.sourceType.findAll(this.inDir);
        log.info("{} samples of type {} found in directory {}.", this.samples.length, this.sourceType.toString(), this.inDir);
        // Verify the numbers.
        if (this.phredOffset < 32 || this.phredOffset > 127)
            throw new ParseFailureException("Invalid phred offset.  Must be between 32 and 127.");
        if (this.minSimFraction <= 0.0 || this.minSimFraction > 1.0)
            throw new ParseFailureException("Invalid minSim fraction.  Must be greater than 0 and less than or equal to 1.");
        if (this.kmerSize <= 1)
            throw new ParseFailureException("Invalid kmer size.  Must be greater than 1.");
        if (this.minHitCount < 1)
            throw new ParseFailureException("Invalid minimum hit count.  Must be greater than 0.");
        if (this.minReadLen < 1)
            throw new ParseFailureException("Invalid minimum read length.  Must be greater than 0.");
        if (this.minReadQual > 99.0 || this.minReadQual < 0.0)
            throw new ParseFailureException("Invalid minimum read quality.  Cannot be greater than 99 or less than 0.");
        // Store the globals.
        DnaKmers.setKmerSize(this.kmerSize);
        SeqRead.setPhredOffset(this.phredOffset);
        log.info("Kmer size is {} and phred offset is {}.", this.kmerSize, this.phredOffset);
        // Finally, process the repgen file.
        if (! this.repGenFile.canRead())
            throw new FileNotFoundException("Input repgen file " + this.repGenFile + " is not found or invalid.");
        log.info("Reading SSU rRNA sequences from {}.", this.repGenFile);
        this.repGenSsuKmerMap = buildKmerMap(this.repGenFile);
        log.info("{} representative genomes found.", this.repGenSsuKmerMap.size());
    }

    @Override
    protected void runReporter(PrintWriter writer) throws Exception {
        // Write the header line. Note only "sample_id" and "repgen_id" are used by the xmatrix generator.
        // The count is the number of reads that the representative genome hit.
        writer.println("sample_id\trepgen_id\tcount");
        // This will count the repgen hits.  It is erased after each sample.
        CountMap<String> hits = new CountMap<String>();
        // Loop through the samples.
        log.info("Processing samples.");
        for (File sample : this.samples) {
            FastqStream sampleStream = this.sourceType.create(sample);
            String sampleID = sampleStream.getSampleId();
            log.info("Reading sample {} from {}.", sampleID, sample);
            // Now loop through the reads in the sample.
            int readCount = 0;
            int badCount = 0;
            int shortCount = 0;
            int goodCount = 0;
            for (SeqRead read : sampleStream) {
                // First we filter the read.
                if (read.getQual() < this.minReadQual)
                    badCount++;
                else if (read.maxlength() < this.minReadLen)
                    shortCount++;
                else {
                    readCount++;
                    // Get the read's kmers.
                    SequenceKmers readKmers = read.new Kmers();
                    // Find the best repgen hit.
                    String repGenFound = null;
                    double bestHit = 0.0;
                    double readLen = read.length();
                    for (Map.Entry<String, DnaKmers> mapEntry : this.repGenSsuKmerMap.entrySet()) {
                        DnaKmers mapKmers = mapEntry.getValue();
                        int simCount = readKmers.similarity(mapKmers);
                        double simFraction = simCount / readLen;
                        if (simFraction > bestHit) {
                            bestHit = simFraction;
                            repGenFound = mapEntry.getKey();
                        }
                    }
                    // Is the best hit good enough?
                    if (bestHit >= this.minSimFraction) {
                        // Yes.  Count it.
                        goodCount++;
                        hits.count(repGenFound);
                    }
                }
            }
            log.info("{} repgen instances found in {} good reads.  {} reads were short, {} were bad.", goodCount, readCount, shortCount, badCount);
            // Now process the counts.  We only keep the ones that are high enough.
            int keptCount = 0;
            int foundCount = 0;
            for (CountMap<String>.Count counter : hits.counts()) {
                int hitsCounted = counter.getCount();
                if (hitsCounted > 0) {
                    // Here we have a repgen that was found in the sample.
                    foundCount++;
                    // Does it have enough coverage to count?
                    if (hitsCounted >= this.minHitCount) {
                        keptCount++;
                        writer.format("%s\t%s\t%d%n", sampleID, counter.getKey(), hitsCounted);
                    }
                }
            }
            log.info("{} representatives kept out of {} found in sample {}.", keptCount, foundCount, sampleID);
            // Clear the counters for next time.
            hits.clear();
        }
        log.info("All done. {} samples processed.", this.samples.length);
    }

    /**
     * Build a map of repgen IDs to SSU rRNA kmer objects.
     *
     * @param rgFile	input four-column table for the repgen set
     *
     * @return a map from the repgen IDs to the corresponding kmer objects
     *
     * @throws IOException
     */
    public static Map<String, DnaKmers> buildKmerMap(File rgFile) throws IOException {
        Map<String, DnaKmers> retVal = new HashMap<String, DnaKmers>(3000);
        // Loop through the input file.
        try (TabbedLineReader inStream = new TabbedLineReader(rgFile)) {
            int idCol = inStream.findField("genome_id");
            int seqCol = inStream.findField("ssu_rna");
            for (TabbedLineReader.Line line : inStream) {
                String repGenId = line.get(idCol);
                DnaKmers kmers = new DnaKmers(line.get(seqCol));
                retVal.put(repGenId, kmers);
            }
        }
        return retVal;
    }

}
