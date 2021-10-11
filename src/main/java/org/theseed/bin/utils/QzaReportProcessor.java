/**
 *
 */
package org.theseed.bin.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.counters.CountMap;
import org.theseed.io.TabbedLineReader;
import org.theseed.sequence.DnaKmers;
import org.theseed.sequence.GenomeDescriptor;
import org.theseed.sequence.GenomeDescriptorSet;
import org.theseed.sequence.SequenceKmers;
import org.theseed.sequence.fastq.FastqSampleGroup;
import org.theseed.sequence.fastq.ReadStream;
import org.theseed.sequence.fastq.SeqRead;
import org.theseed.utils.BaseProcessor;
import org.theseed.utils.ParseFailureException;

/**
 * This command processes a directory of Amplicon SSU rRNA samples to produce a report that can be used to build
 * an Xmatrix.  The positional parameters are the name of the input directory containing the samples, the name of
 * the four-column table for the appropriate RepGen set, and the name of the output file.  The following
 * command-line options are supported.
 *
 * -h	display command-line usage
 * -v	display more frequent log messages
 * -b	batch size for parallel processing
 *
 * --source		type of input directory-- QZA or P3DIR
 * --phred		phred offset for quality strings in the FASTQ files (default 33)
 * --minSim		minimum similarity required for a good hit, as a fraction of the read length
 * --K			DNA kmer size
 * --minHits	minimum number of hits for a representative to be considered significant
 * --minQual	minimum acceptable quality for a read to be acceptable
 * --minLen		minimum acceptable length for a read to be acceptable
 * --resume		name of a progress file used to make the program restartable; the default is
 * 				qzaReport.progress.txt
 *
 * @author Bruce Parrello
 *
 */
public class QzaReportProcessor extends BaseProcessor {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(QzaReportProcessor.class);
    /** input sample group */
    private FastqSampleGroup inputGroup;
    /** set of sample IDs */
    private Set<String> samples;
    /** map of hit counts for each representative */
    private CountMap<String> hitCounts;
    /** number of good reads found in current sample */
    private int goodCount;
    /** start time for processing of current sample */
    private long start;
    /** number of reads processed in the current sample */
    private int readCount;
    /** genome descriptor set for repgens */
    private GenomeDescriptorSet repgens;
    /** array of input files to parse */
    private File[] inFiles;
    /** set of samples already processed */
    private Set<String> processed;
    /** TRUE if we are resuming, else FALSE */
    private boolean resumeFlag;
    /** counter of bad samples */
    private int badSampleCount;
    /** writer for the progress file */
    private PrintWriter progressStream;

    // COMMAND-LINE OPTIONS

    /** format of input samples */
    @Option(name = "--source", usage = "format of samples in input directory")
    private FastqSampleGroup.Type sourceType;

    /** phred offset used to translate quality strings in FASTQ input */
    @Option(name = "--phred", metaVar = "50", usage = "zero-value offset for FASTQ quality strings")
    private int phredOffset;

    /** minimum amount of kmer hits required as fraction of read length */
    @Option(name = "--minSim", metaVar = "0.90", usage = "minimum fraction of read length that must be kmer hits")
    private double minSimFraction;

    /** minimum number of kmer hits for a representative genome to be considered significant */
    @Option(name = "--minHits", metaVar = "150", usage = "minimum number of reads hits for a representative genome to be considered present")
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

    /** batch size */
    @Option(name = "--batch", aliases = { "-b" }, metaVar = "10", usage = "batch size for parallelization")
    private int batchSize;

    /** resume-processing flag */
    @Option(name = "--resume", usage = "file used to track progress for restarts")
    private File progressFile;

    /** input directory name */
    @Argument(index = 0, metaVar = "inDir", usage = "input file/directory name", required = true)
    private File inDir;

    /** input repgen file (four-column table format */
    @Argument(index = 1, metaVar = "rep.seqs.tbl", usage = "input four-column repgen table.", required = true)
    private File repGenFile;

    /** output file */
    @Argument(index = 2, metaVar = "output.tbl", usage = "output file", required = true)
    private File outFile;

    @Override
    protected void setDefaults() {
        this.sourceType = FastqSampleGroup.Type.QZA;
        this.phredOffset = 33;
        this.minSimFraction = 0.80;
        this.kmerSize = DnaKmers.kmerSize();
        this.minHitCount = 180;
        this.minReadLen = 50;
        this.minReadQual = 30.0;
        this.batchSize = 200;
        this.progressFile = new File(System.getProperty("user.dir"), "qzaReport.progress.txt");
    }

    @Override
    protected boolean validateParms() throws IOException, ParseFailureException {
        // Insure the input directory exists.
        if (! this.inDir.isDirectory())
            throw new FileNotFoundException("Input " + this.inDir + " is not found or invalid.");
        // Get all the input sample files.
        log.info("Scanning for samples in {}.", this.inDir);
        this.inFiles = this.inDir.listFiles(this.sourceType.getFilter());
        log.info("{} sample files found in {}.", this.inFiles.length, this.inDir);
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
        if (this.batchSize < 1)
            throw new ParseFailureException("Invalid batch size.  Must be at least 1.");
        // Find out if we are resuming.
        this.resumeFlag = this.progressFile.exists();
        this.progressStream = null;
        // Store the globals.
        DnaKmers.setKmerSize(this.kmerSize);
        SeqRead.setPhredOffset(this.phredOffset);
        log.info("Kmer size is {} and phred offset is {}.", this.kmerSize, this.phredOffset);
        // Process the repgen file.
        if (! this.repGenFile.canRead())
            throw new FileNotFoundException("Input repgen file " + this.repGenFile + " is not found or invalid.");
        log.info("Reading SSU rRNA sequences from {}.", this.repGenFile);
        this.repgens = new GenomeDescriptorSet(this.repGenFile);
        log.info("{} representative genomes found.", this.repgens.size());
        return true;
    }

    @Override
    protected void runCommand() throws Exception {
        // Handle the resume processing.
        try (PrintWriter writer = this.prepareOutput()) {
            // Each batch of reads is stored in this list.
            List<SeqRead> batch = new ArrayList<SeqRead>(this.batchSize);
            // We will store the counters in here.  Counters are per-sample, so it will be cleared between samples.
            this.hitCounts = new CountMap<String>();
            // Loop through the sample files.
            int sampleCount = 0;
            for (File inFile : this.inFiles) {
                // Get the samples in this file.
                this.inputGroup = this.sourceType.create(inFile);
                this.samples = this.inputGroup.getSamples();
                log.info("{} samples of type {} found in source {}.", this.samples.size(), this.sourceType.toString(), inFile);
                // Remove the ones already processed.
                this.samples.removeAll(this.processed);
                // Loop through the remaining samples.
                log.info("Processing samples.");
                for (String sampleID : this.samples) {
                    try (ReadStream sampleIter = this.inputGroup.sampleIter(sampleID)) {
                        log.info("Reading sample {}", sampleID);
                        this.start = System.currentTimeMillis();
                        this.readCount = 0;
                        int badCount = 0;
                        int shortCount = 0;
                        this.goodCount = 0;
                        // Now loop through the reads in the sample.
                        while (sampleIter.hasNext()) {
                            SeqRead read = sampleIter.next();
                            // First we filter the read.
                            if (read.getQual() < this.minReadQual)
                                badCount++;
                            else if (read.maxlength() < this.minReadLen)
                                shortCount++;
                            else {
                                // This is a reasonable read, so we must queue it for processing.
                                // Insure there is room in the batch.
                                if (batch.size() >= this.batchSize)
                                    this.processBatch(batch);
                                // Add it to the batch.
                                batch.add(read);
                                this.readCount++;
                            }
                        }
                        // Process the residual batch.  This also clears the batch for the next sample.
                        this.processBatch(batch);
                        log.info("{} repgen instances found in {} good reads.  {} reads were short, {} were bad.", goodCount, readCount, shortCount, badCount);
                    }
                    // Now process the counts.  We only keep the ones that are high enough.
                    int keptCount = 0;
                    int foundCount = 0;
                    for (CountMap<String>.Count counter : this.hitCounts.counts()) {
                        int hitsCounted = counter.getCount();
                        if (hitsCounted > 0) {
                            // Here we have a repgen that was found in the sample.
                            foundCount++;
                            // Does it have enough coverage to count?
                            if (hitsCounted >= this.minHitCount) {
                                keptCount++;
                                String repId = counter.getKey();
                                String name = this.repgens.getName(repId);
                                writer.format("%s\t%s\t%s\t%d%n", sampleID, repId, name, hitsCounted);
                            }
                        }
                    }
                    // If there were representatives kept for this sample, flush the output.  Also, record
                    // the sample in the progress file.
                    this.progressStream.format("%s\t%d%n", sampleID, keptCount);
                    if (keptCount > 0)
                    	writer.flush();
                    else
                    	this.badSampleCount++;
                    // Always flush the progress stream.
                    this.progressStream.flush();
                    log.info("{} representatives kept out of {} found in sample {}.", keptCount, foundCount, sampleID);
                    log.info("PROGRESS:  {} samples processed, {} were bad.", this.processed.size(), this.badSampleCount);
                    // Clear the counters for next time.
                    hitCounts.clear();
                    sampleCount++;
                }
            }
            log.info("All done. {} samples processed in {} files, {} were bad.", sampleCount, this.inFiles.length,
            		this.badSampleCount);
        } finally {
        	// Insure we close the progress output file.
        	if (this.progressStream != null)
        		this.progressStream.close();
        }
    }

    /**
     * Prepare the output file.  If it is new, we write the header.  If we are resuming, we read in the
     * processed samples to get a list.
     *
     * @return a writer for the output file.
     *
     * @throws IOException
     */
    private PrintWriter prepareOutput() throws IOException {
        // Denote we have no bad samples.
        this.badSampleCount = 0;
        // Create the already-processed set.
        this.processed = new HashSet<String>((XMatrixProcessor.EXPECTED_SAMPLES * 4 + 2) / 3);
        // Is this a new run?
        if (! this.resumeFlag) {
        	log.info("Initializing for new output to file {}.", this.outFile);
            // Here we have a brand-new file.  Write the header line. Note only "sample_id"
            // and "repgen_id" are used by the xmatrix generator.  The count is the number
            // of reads that the representative genome hit.
            try (PrintWriter writer = new PrintWriter(this.outFile)) {
                writer.println("sample_id\trepgen_id\trepgen_name\tcount");
            }
            // Initialize the progress stream.
            this.progressStream = new PrintWriter(this.progressFile);
            this.progressStream.println("sample_id\treps");
        } else if (! this.outFile.canRead())
            throw new FileNotFoundException("Output file " + this.outFile + " is not found or unreadable, but resume file exists.");
        else {
            // We will append to the output file, but first we need to memorize the samples we've already
            // processed.
            log.info("Resuming output to file {}.", this.outFile);
            // Get all the samples we've already processed.
            try (TabbedLineReader progressStream = new TabbedLineReader(this.progressFile)) {
            	int sampleCol = progressStream.findField("sample_id");
            	int countCol = progressStream.findField("reps");
            	for (TabbedLineReader.Line line : progressStream) {
            		String sampleId = line.get(sampleCol);
            		int count = line.getInt(countCol);
            		if (count == 0) this.badSampleCount++;
            		this.processed.add(sampleId);
            	}
            }
            log.info("{} samples already processed. {} were bad.", this.processed.size(), this.badSampleCount);
            // Set up to append to the progress stream.
            FileOutputStream outStream = new FileOutputStream(this.progressFile, true);
            this.progressStream = new PrintWriter(outStream);
        }
        // Set up to append to the output stream.
        FileOutputStream outStream = new FileOutputStream(this.outFile, true);
        PrintWriter retVal = new PrintWriter(outStream);
        // Return the main output stream.
        return retVal;
    }

    /**
     * Process a batch of reads.  The batch is cleared after processing so it can be used again.
     *
     * @param batch		list of reads to process
     */
    private void processBatch(List<SeqRead> batch) {
        // We process the batch in parallel.
        batch.parallelStream().forEach(x -> processRead(x));
        // Display progress.
        if (log.isInfoEnabled()) {
            double speed = (1000.0 * this.readCount) / (System.currentTimeMillis() - this.start);
            log.info("{} reads processed, {} good hits. {} reads/second.", this.readCount, this.goodCount, speed);
        }
        // Clear the batch to make room for more reads.
        batch.clear();
    }

    /**
     * Process a single read.  We find the read's repgen ID and record it in this object's data structures.
     *
     * @param read		read to process
     */
    private void processRead(final SeqRead read) {
        // Get the read's kmers.
        SequenceKmers readKmers = read.new Kmers();
        // Find the best repgen hit.
        String repGenFound = null;
        double bestHit = 0.0;
        double readLen = read.length();
        for (GenomeDescriptor desc : this.repgens) {
            DnaKmers mapKmers = desc.getSsuKmers();
            int simCount = readKmers.similarity(mapKmers);
            double simFraction = simCount / readLen;
            if (simFraction > bestHit) {
                bestHit = simFraction;
                repGenFound = desc.getId();
            }
        }
        // Is the best hit good enough?
        if (bestHit >= this.minSimFraction) {
            // Yes.  Count it.  This part must be synchronized so the method is thread-safe.
            synchronized(this) {
                this.goodCount++;
                this.hitCounts.count(repGenFound);
            }
        }
    }

}
