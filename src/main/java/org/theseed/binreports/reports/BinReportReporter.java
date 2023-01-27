/**
 *
 */
package org.theseed.binreports.reports;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.binreports.BinReport;
import org.theseed.binreports.scores.ScorePackaging;
import org.theseed.utils.ParseFailureException;

/**
 * This method outputs data from sample bin reports to a specified directory.  The output directory will contain one or more
 * files describing the makeup of the samples based on a scoring method chosen by the client.
 *
 * @author Bruce Parrello
 *
 */
public abstract class BinReportReporter {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(BinReportReporter.class);
    /** scoring method type */
    private ScorePackaging.Type method;
    /** output directory name */
    private File outDir;
    /** value of the negative output label */
    private String negLabel;
    /** array of labels, with the negative output label first */
    private String[] labels;
    /** list of open output files */
    private List<PrintWriter> writers;

    /**
     * This interface must be supported by each command processor that uses this object.  It allows access to
     * subclass tuning parameters.
     */
    public interface IParms {

        /**
         * @return the scoring method type
         */
        public ScorePackaging.Type getMethodType();

        /**
         * @return the minimum fraction of presence required for a representation group to be considered common
         */
        public double getCommonFrac();

        /**
         * @return the output directory
         */
        public File getOutDir();

        /**
         * @return TRUE if the output directory should be erased before processing
         */
        public boolean isClearing();

        /**
         * @return the output label to be used as the negative outcome
         */
        public String getNegativeLabel();

        /**
         * @return a map from representative group IDs to group names
         */
        public Map<String, String> getFeatureMap();

    }

    /**
     * This enumeration describes the different types of reports that can be produced.
     */
    public static enum Type {
        /** report on the representation groups common to each label */
        COMMONS {
            @Override
            public BinReportReporter create(IParms processor) throws IOException, ParseFailureException {
                return new CommonsBinReportReporter(processor);
            }
        },
        /** produce a classifier input directory */
        XMATRIX {
            @Override
            public BinReportReporter create(IParms processor) throws IOException, ParseFailureException {
                return new XMatrixBinReportReporter(processor);
            }
        };

        /**
         * @return a reporting object of this type
         *
         * @param processor		controlling command processor
         *
         * @throws IOException
         * @throws ParseFailureException
         */
        public abstract BinReportReporter create(IParms processor) throws IOException, ParseFailureException;

    }

    /**
     * Construct a new bin report reporter and validate the tuning parameters.
     *
     * @param processor		name of the controlling command processor
     *
     * @throws IOException
     */
    public BinReportReporter(IParms processor) throws IOException {
        // Create the initially-empty list of output writers.
        this.writers = new ArrayList<PrintWriter>();
        // Validate the output directory.
        this.outDir = processor.getOutDir();
        if (! this.outDir.isDirectory()) {
            log.info("Creating output directory {}.", this.outDir);
            FileUtils.forceMkdir(this.outDir);
        } else if (processor.isClearing()) {
            log.info("Erasing output directory {}.", this.outDir);
            FileUtils.cleanDirectory(this.outDir);
        } else
            log.info("Output directory is {}.", this.outDir);
        // Save the method type.
        this.method = processor.getMethodType();
        // Save the negative label.
        this.negLabel = processor.getNegativeLabel();
        log.info("\"{}\" is considered the negative condition for these samples.", this.negLabel);
    }

    /**
     * This is a utility validation method for subclasses that require binary scoring normalization (all scores to 1 and 0).
     *
     * @throws ParseFailureException
     */
    protected void requireBinaryNormalization() throws ParseFailureException {
        // Insure the method type is binary.
        if (! this.method.isBinary()) {
            // Here we built a comma-delimited list of the acceptable types.
            String goodTypes = Arrays.stream(ScorePackaging.Type.values()).filter(x -> x.isBinary())
                    .map(x -> x.name()).collect(Collectors.joining(", "));
            // Denote we have the wrong type.
            throw new ParseFailureException("Method type must be binary.  Valid types are: " + goodTypes);
        }
    }

    /**
     * Create the report from the data.
     *
     * @param binReport		bin report object containing the samples and their populations
     *
     * @throws IOException
     */
    public void process(BinReport binReport) throws IOException {
        // Create the scoring method.
        ScorePackaging normalizer = this.method.create(binReport);
        // Loop through the labels.  We do a special sort on these, putting them in alphabetical order except with
        // the negative label first.
        this.labels = binReport.getLabels().stream().sorted().toArray(String[]::new);
        for (int i = 1; i < this.labels.length; i++) {
            if (this.labels[i].contentEquals(this.negLabel)) {
                this.labels[i] = labels[0];
                this.labels[0] = this.negLabel;
            }
        }
        // Initialize the report.
        this.startReport(binReport);
        // Loop through the labels.
        for (String label : this.labels) {
            log.info("Processing samples for output label {}.", label);
            this.startLabel(label);
            // Loop through this label's samples, normalizing the scores.
            for (BinReport.Sample sample : binReport.getSamplesForLabel(label)) {
                double[] scores = normalizer.getScores(sample);
                this.processSample(sample, scores);
            }
            // Summarize the label.
            this.finishLabel();
        }
        // Summarize the full report.
        this.finishReport();
        // Close all the open output files.
        this.closeOutFiles();
    }

    /**
     * Initialize the report.
     *
     * @param binReport		bin report object containing the samples and their data
     *
     * @throws IOException
     */
    protected abstract void startReport(BinReport binReport) throws IOException;

    /**
     * Start processing for a label.
     *
     * @param label		label being processed
     */
    protected abstract void startLabel(String label);

    /**
     * Process a sample.
     *
     * @param sample	sample to process
     * @param scores	normalized scores for the sample
     */
    protected abstract void processSample(BinReport.Sample sample, double[] scores);

    /**
     * Finish processing for a label.
     */
    protected abstract void finishLabel();

    /**
     * Finish the report.
     */
    protected abstract void finishReport();

    /**
     * @return a print writer for an output file with the specified name
     *
     * @param name		base name to give to file
     *
     * @throws IOException
     */
    protected PrintWriter openOutFile(String name) throws IOException {
        File outFile = new File(this.outDir, name);
        PrintWriter retVal = new PrintWriter(outFile);
        this.writers.add(retVal);
        return retVal;
    }

    /**
     * Close all the open output streams.
     */
    protected void closeOutFiles() {
        for (var writer : writers)
            writer.close();
    }

    /**
     * @return the normalization method type
     */
    protected ScorePackaging.Type getMethodType() {
        return this.method;
    }

    /**
     * This method returns the output labels sorted in lexical order, but with the negative label positioned first.
     * When creating the label file for a classifier input directory, this is the preferred output order.
     *
     * @return the ordered array of output labels
     */
    protected String[] getLabels() {
        return this.labels;
    }

}
