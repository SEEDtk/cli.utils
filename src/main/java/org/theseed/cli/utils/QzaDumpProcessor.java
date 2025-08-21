/**
 *
 */
package org.theseed.cli.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.basic.BaseProcessor;
import org.theseed.basic.ParseFailureException;
import org.theseed.sequence.FastaOutputStream;
import org.theseed.sequence.Sequence;
import org.theseed.sequence.fastq.FastqSampleGroup;
import org.theseed.sequence.fastq.ReadStream;
import org.theseed.sequence.fastq.SeqRead;

/**
 * This command reads a QZA file and converts each sample to a pair of FASTA files in the specified output directory.
 * The positional parameters are the name of the input file or directory containing the samples, and the name of
 * the output directory.  The command-line options are as follows.
 *
 * -h	display command-line usage
 * -v	display more frequent log messages
 *
 * --source		type of input directory-- QZA or P3DIR (P3DIR is not available yet)
 * --phred		phred offset for quality strings in the FASTQ files (default 33)
 * --minQual	minimum quality for a read to be acceptable
 * --clear		erase the output directory before processing
 *
 * @author Bruce Parrello
 *
 */
public class QzaDumpProcessor extends BaseProcessor {

    // FIELDS
    /** logging facility */
    private static final Logger log = LoggerFactory.getLogger(QzaDumpProcessor.class);
    /** QZA sample group */
    private FastqSampleGroup sampleGroup;
    /** set of sample IDs */
    private Set<String> sampleIDs;

    // COMMAND-LINE OPTIONS

    /** format of input samples */
    @Option(name = "--source", usage = "format of samples in input directory")
    private FastqSampleGroup.Type sourceType;

    /** phred offset used to translate quality strings in FASTQ input */
    @Option(name = "--phred", metaVar = "50", usage = "zero-value offset for FASTQ quality strings")
    private int phredOffset;

    /** minimum acceptable read quality */
    @Option(name = "--minQual", metaVar = "20.0", usage = "minimum acceptable read quality")
    private double minReadQual;

    /** if specified, the output directory will be cleared before processing */
    @Option(name = "--clear", usage = "erase output directory before processing")
    private boolean clearFlag;

    /** input file or directory */
    @Argument(index = 0, metaVar = "inFile", usage = "input QZA file or P3 sample directory", required = true)
    private File inFile;

    /** output directory */
    @Argument(index = 1, metaVar = "outDir", usage = "output directory", required = true)
    private File outDir;

    @Override
    protected void setDefaults() {
        this.sourceType = FastqSampleGroup.Type.QZA;
        this.phredOffset = 33;
        this.minReadQual = 30.0;
        this.clearFlag = false;
    }

    @Override
    protected void validateParms() throws IOException, ParseFailureException {
        // Verify the numbers.
        if (this.phredOffset < 32 || this.phredOffset > 127)
            throw new ParseFailureException("Invalid phred offset.  Must be between 32 and 127.");
        if (this.minReadQual > 99.0 || this.minReadQual < 0.0)
            throw new ParseFailureException("Invalid minimum read quality.  Cannot be greater than 99 or less than 0.");
        // Check the input file.
        if (! this.inFile.exists())
            throw new FileNotFoundException("Input source " + this.inFile + " not found.");
        this.sampleGroup = this.sourceType.create(this.inFile);
        this.sampleIDs = this.sampleGroup.getSampleIDs();
        log.info("{} samples found in {}.", this.sampleIDs.size(), this.inFile);
        // Set up the output directory.
        if (! this.outDir.isDirectory()) {
            log.info("Creating output directory {}.", this.outDir);
            FileUtils.forceMkdir(this.outDir);
        } else if (this.clearFlag) {
            log.info("Erasing output directory {}.", this.outDir);
            FileUtils.cleanDirectory(this.outDir);
        } else
            log.info("Output will be to directory {}.", this.outDir);
    }

    @Override
    protected void runCommand() throws Exception {
        // Store the phred offset.
        SeqRead.setPhredOffset(this.phredOffset);
        // Get some counters.
        int sampCount = 0;
        int rejectCount = 0;
        int keepCount = 0;
        // Loop through the samples.
        for (String sampleId : this.sampleIDs) {
            sampCount++;
            log.info("Processing sample {}: {}.", sampCount, sampleId);
            // We need to create output files for the left and right read sets.
            File lOutFile = new File(this.outDir, sampleId + "-fwd.fna");
            File rOutFile = new File(this.outDir, sampleId + "-rev.fna");
            try (var lOutStream = new FastaOutputStream(lOutFile);
                    var rOutStream = new FastaOutputStream(rOutFile)) {
                // Open the input stream.
                ReadStream inStream = this.sampleGroup.sampleIter(sampleId);
                for (var read : inStream) {
                    if (read.getQual() < this.minReadQual)
                        rejectCount++;
                    else {
                        // Write the left and right sequences.
                        String label = read.getLabel();
                        Sequence seq = new Sequence(label, "", read.getLseq());
                        lOutStream.write(seq);
                        seq = new Sequence(label, "", read.getRseq());
                        rOutStream.write(seq);
                        keepCount++;
                    }
                }
            }
            log.info("{} reads kept and {} rejected for {} samples.", keepCount, rejectCount, sampCount);
        }
        // Insure we close the input sample group.
        this.sampleGroup.close();
    }

}
