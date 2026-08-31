package org.theseed.cli.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.basic.BaseProcessor;
import org.theseed.basic.ParseFailureException;
import org.theseed.io.TabbedLineReader;
import org.theseed.p3api.KeyBuffer;
import org.theseed.p3api.P3CursorConnection;
import org.theseed.p3api.SolrFilter;
import org.theseed.sequence.MD5Hex;

import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * This command searches the BV-BRC genome_sequence table ("contig" in the default P3CursorConnection data map)
 * for missing MD5 checksums and writes them to a table. Because this is a very long process, it is designed to be
 * restartable. We process one genome at a time, and we keep a file of the genomes that have already been processed.
 * If the program is interrupted, it can be restarted and will skip over the genomes already completed.
 * 
 * The output is a tab-delimited file containing the genome ID, the sequence ID, and the MD5 checksum. The file is
 * flushed after each genome is processed, and is opened for appending when we resume.
 * 
 * The positional parameters are the name of the genome ID output file and the name of the MD5 output file.
 * If the genome ID output file already exists, we presume that we are resuming after a failed run, in which
 * case the MD5 output file must already exists.
 * 
 * The command-line options are as follows:
 * 
 * -h       display command-line usage
 * -v       display more detailed progress messages
 * -b       number of genomes to process in each batch (default 10)
 * 
 * --genomes    if specified, a file containing a list of genome IDs to process. If the file does not exist, it will be created, and
 *              all the genomes from the database will be written to it. This saves us from having to read the entire genome table
 *              each time we restart.
 * 
 */
public class ContigRepairProcessor extends BaseProcessor {

    // FIELDS
    /** logging facility */
    private static final Logger log = LoggerFactory.getLogger(ContigRepairProcessor.class);
    /** set of genome IDs to process */
    private Set<String> genomesToProcess;
    /** connection to the BV-BRC */
    private P3CursorConnection p3;
    /** number of MD5 checksums output */
    private int md5Count;
    /** number of contigs in the current batch */
    private int contigCount;
    /** MD5 checksum utility object */
    private MD5Hex md5Computer;
    /** TRUE if we need to read genome IDs from the genome cache file, else FALSE */
    private boolean readGenomesFromCache;

    /** genome ID cache file */
    @Option(name = "--genomes", metaVar = "cache.tbl", usage = "genome ID cache file")
    private File genomeCacheFile;

    /** number of genomes to process in each query batch */
    @Option(name = "-b", aliases = { "--batchSize", "--batch" }, metaVar = "1", usage = "number of genomes to process in each query batch")
    private int batchSize;

    /** checkpoint file for genome IDs */
    @Argument(index = 0, metaVar = "checkpoint.tbl", usage = "checkpoint file for genome IDs", required = true)
    private File checkpointFile;

    /** output file for MD5 checksums */
    @Argument(index = 1, metaVar = "md5_output.tbl", usage = "output file for MD5 checksums", required = true)
    private File md5OutputFile;

    @Override
    protected void setDefaults() {
        this.batchSize = 10;
        this.genomeCacheFile = null;
    }

    @Override
    protected void validateParms() throws IOException, ParseFailureException {
        // Connect to the BV-BRC.
        this.p3 = new P3CursorConnection();
        // Verify that the batch size is positive.
        if (this.batchSize <= 0)
            throw new ParseFailureException("Batch size must be at least 1.");
        if (this.batchSize > 10)
            log.warn("WARNING: large batch sizes can cause timeout errors.");
        // Check for a genome cache file. If there is no file specified, we will read the genome IDs from the database
        // without any flourish. If there is a file specified, we will read the genome IDs from it if it exists, or create it
        // and save the genome list to it if it does not.
        if (this.genomeCacheFile == null)
            this.readGenomesFromCache = false;
        else 
            this.readGenomesFromCache = this.genomeCacheFile.exists();
        // If the checkpoint file exists, we are resuming a previous run, so the MD5 output file must also exist.
        if (this.checkpointFile.exists()) {
            log.info("Resuming previous run using checkpoint file {}.", this.checkpointFile);
            if (! this.md5OutputFile.exists())
                throw new ParseFailureException("MD5 output file must exist if checkpoint file exists.");
            // Read the genome IDs from the checkpoint file.
            Set<String> oldGenomes = TabbedLineReader.readSet(this.checkpointFile, "1");
            // Read the genome IDs from the database.
            this.getGenomesToProcess(oldGenomes);
            log.info("{} genomes left to process.", this.genomesToProcess.size());
        } else {
            // If the checkpoint file does not exist, we are starting a new run.
            if (this.md5OutputFile.exists())
                log.info("Starting new run; MD5 output file {} will be overwritten.", this.md5OutputFile);
            else
                log.info("Starting new run; MD5 output file {} will be created.", this.md5OutputFile);
            // Initialize both files with headers. This means we can open them for appending just like we would for a resume.
            try (PrintWriter checkpointWriter = new PrintWriter(this.checkpointFile);
                 PrintWriter md5Writer = new PrintWriter(this.md5OutputFile)) {
                checkpointWriter.println("genome_id");
                md5Writer.println("genome_id\tsequence_id\tmd5");
            }
            // Read the genome IDs from the database.
            this.getGenomesToProcess(Collections.emptySet());
        }
    }

    /**
     * Extract the set of genome IDs to process from the database, removing any that are already in the checkpoint file.
     * 
     * @param oldGenomes    set of genome IDs already processed (from the checkpoint file)
     * 
     * @throws IOException
     */
    private void getGenomesToProcess(Set<String> oldGenomes) throws IOException {
        this.genomesToProcess = new HashSet<>(300000);
        if (this.readGenomesFromCache) {
            log.info("Reading genome IDs from the cache file {}.", this.genomeCacheFile);
            try (TabbedLineReader cacheReader = new TabbedLineReader(this.genomeCacheFile)) {
                for (var line : cacheReader) {
                    String genomeId = line.get(0);
                    this.storeGenomeId(genomeId, oldGenomes);
                }
            }
        } else {
            log.info("Reading genome IDs from the database.");
            p3.getRecords("genome", P3CursorConnection.MAX_LIMIT, "genome_id", List.of(SolrFilter.EQ("genome_id", "*")),
                x -> this.storeGenomeId(KeyBuffer.getString(x, "genome_id"), oldGenomes));
            // If we are using a genome cache file, we need to write the genome IDs to it.
            if (this.genomeCacheFile != null) {
                log.info("Writing genome IDs to the cache file {}.", this.genomeCacheFile);
                try (PrintWriter cacheWriter = new PrintWriter(this.genomeCacheFile)) {
                    for (String genomeId : oldGenomes)
                        cacheWriter.println(genomeId);
                    log.info("{} old genome IDs written to the cache file.", oldGenomes.size());
                    for (String genomeId : this.genomesToProcess)
                        cacheWriter.println(genomeId);
                    log.info("{} new genome IDs written to the cache file.", this.genomesToProcess.size());
                }
            }
        }
        log.info("{} genomes to process.", this.genomesToProcess.size());
    }

    /**
     * Store a genome ID in the master list unless it is in the old-genomes set. This is a utility method used as a consumer
     * in the genome ID query.
     * 
     * @param genomeId      genome ID to store
     * @param oldGenomes    set of genome IDs already processed (from the checkpoint file
     */
    private void storeGenomeId(String genomeId, Set<String> oldGenomes) {
        if (! oldGenomes.contains(genomeId)) {
            this.genomesToProcess.add(genomeId);
            if (this.genomesToProcess.size() % 100000 == 0)
                log.info("{} genomes found so far.", this.genomesToProcess.size());
        }
    }

    @Override
    protected void runCommand() throws Exception {
        // Create the MD5 checksum utility object.
        this.md5Computer = new MD5Hex();
        // We track the number of genomes processed, the number of batches processed, and the number of MD5 checksums output.
        int genomeCount = 0;
        int batchCount = 0;
        this.md5Count = 0;
        // Open the output files for appending.
        try (PrintStream checkpointStream = this.openCheckpointFile();
             PrintStream md5Stream = this.openOutputFile()) {
            // Loop through the genomes to process in batches.
            List<String> genomeBatch = new java.util.ArrayList<>(this.batchSize);
            for (String genomeId : this.genomesToProcess) {
                genomeBatch.add(genomeId);
                genomeCount++;
                if (genomeBatch.size() >= this.batchSize) {
                    this.processGenomeBatch(genomeBatch, checkpointStream, md5Stream);
                    genomeBatch.clear();
                    batchCount++;
                    log.info("Processed {} genomes in {} batches so far: {} MD5 checksums computed.", 
                            genomeCount, batchCount, this.md5Count);
                }
            }
            // Process any remaining genomes.
            if (! genomeBatch.isEmpty()) {
                this.processGenomeBatch(genomeBatch, checkpointStream, md5Stream);
                batchCount++;
            }
        }
        log.info("Processed {} genomes in {} batches. {} MD5 checksums computed.", genomeCount,
                batchCount, this.md5Count);
    }


    /**
     * Process a batch of genomes. This method is called once for each batch of genomes to process. This is where we read
     * the contigs and compute the MD5 checksums.
     * 
     * @param genomeBatch       list of genome IDs to process
     * @param checkpointStream  stream for the checkpoint file
     * @param md5Stream         stream for the MD5 output file
     * 
     * @throws IOException 
     */
    private void processGenomeBatch(List<String> genomeBatch, PrintStream checkpointStream, PrintStream md5Stream) throws IOException {
        // First, we read the contigs for the genomes in the batch and map them to the genome IDs.
        this.contigCount = 0;
        Map<String, List<JsonObject>> genomeContigs = new HashMap<>();
        p3.getRecords("contig", P3CursorConnection.MAX_LIMIT, this.batchSize, "genome_id", 
                genomeBatch, "genome_id,sequence_id,sequence", 
                List.of(SolrFilter.NE("sequence_md5", "*")), x -> this.storeContig(genomeContigs, x));
        log.info("{} contigs with missing MD5 checksums found for {} genomes.", this.contigCount, genomeBatch.size());
        // Now we loop through the genome IDs in the batch and process the contigs for each.
        for (String genomeId : genomeBatch) {
            List<JsonObject> contigs = genomeContigs.get(genomeId);
            if (contigs != null) {
                for (JsonObject contig : contigs) {
                    String sequenceId = KeyBuffer.getString(contig, "sequence_id");
                    String sequence = KeyBuffer.getString(contig, "sequence");
                    String md5 = this.md5Computer.contigMD5(sequence);
                    md5Stream.printf("%s\t%s\t%s%n", genomeId, sequenceId, md5);
                    this.md5Count++;
                }
            }
            checkpointStream.println(genomeId);
        }
    }

    /**
     * Store the specified contig in the map list for its genome ID. This is a utility method used as a consumer in the contig query.
     * 
     * @param genomeContigs     map of genome IDs to lists of contigs
     * @param contig              the contig to store
     * 
     */
    private void storeContig(Map<String, List<JsonObject>> genomeContigs, JsonObject contig) {
        String genomeId = KeyBuffer.getString(contig, "genome_id");
        List<JsonObject> contigList = genomeContigs.computeIfAbsent(genomeId, k -> new java.util.ArrayList<>());
        contigList.add(contig);
        this.contigCount++;
    }

    /**
     * Open the checkpoint file for appending with autoflush. Autoflush insures that every genome is remembered in the checkpoint file.
     * 
     * @return a PrintStream for the checkpoint file
     */
    private PrintStream openCheckpointFile() throws IOException {
        FileOutputStream outStream = new FileOutputStream(this.checkpointFile, true);
        return new PrintStream(outStream, true);
    }

    /**
     * Open the MD5 output file for appending. This file is flushed after each genome is processed, so we don't need autoflush here.
     * 
     * @return a PrintStream for the MD5 output file
     */
    private PrintStream openOutputFile() throws IOException {
        FileOutputStream outStream = new FileOutputStream(this.md5OutputFile, true);
        return new PrintStream(outStream, false);
    }

}
