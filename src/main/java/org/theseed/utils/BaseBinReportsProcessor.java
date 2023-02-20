/**
 *
 */
package org.theseed.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.kohsuke.args4j.Argument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.binreports.BinReport;
import org.theseed.io.TabbedLineReader;

/**
 * This is the base processor for commands that process sets of bin reports.  A bin report is a four-column tab-delimited file with
 * headers containing a sample ID, a representative genome ID, a representative genome name, and a score in each record.  Each bin
 * report is associated with a particular condition label, and roughly describes the microbial population of each sample.  The full
 * set of bin reports can be used to compare such populations for different conditions or to generate xmatrix inputs for
 * classifiers.
 *
 * The first positional parameter must be the name of a tab-delimited file with headers containing the feature IDs in the first column
 * and names in the second.  In our case, these are representative genome IDs and names.  The remaining positional parameters are
 * arranged in pairs.  Each pair consists of a label string followed by a file name.
 *
 *  	rep100.stats.tbl parkinsons diseased.report.tbl control healthy.report.tbl
 *
 * specifies two bin reports:  "diseased.report.tbl" contains samples to be labeled "parkinsons", while "healthy.report.tbl" contains
 * samples to be labeled "control".
 *
 * Multiple bin reports can use the same label, in which case the data is merged; however, two sample IDs cannot be used in different
 * files without causing unpredictable results.
 *
 * The following command-line options are supported.
 *
 * -h	display command-line usage
 * -v	display more frequent log messages
 *
 * @author Bruce Parrello
 *
 */
public abstract class BaseBinReportsProcessor extends BaseProcessor {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(BaseBinReportsProcessor.class);
    /** set of feature IDs */
    private SortedMap<String, String> featureMap;
    /** bin report data object */
    private BinReport data;
    /** list of condition labels on command line, in order */
    private List<String> labels;
    /** list of report files on command line, in order */
    private List<File> inFiles;

    // COMMAND-LINE OPTIONS

    /** feature ID table */
    @Argument(index = 0, metaVar = "repXX.stats.tbl", usage = "file containing feature IDs", required = true)
    private File featIdFile;

    /** label/file pairs */
    @Argument(index = 1, metaVar = "label1 file1 label2 file2 ...", usage = "list of output labels and file names", required = true)
    private List<String> specs;

    @Override
    protected final void setDefaults() {
        this.setBinReportDefaults();
    }

    /**
     * Specify default values for the parameters.
     */
    protected abstract void setBinReportDefaults();

    @Override
    protected final boolean validateParms() throws IOException, ParseFailureException {
        // Insure the file/label parameters are properly paired.
        final int pairParmCount = this.specs.size();
        if (pairParmCount % 2 != 0)
            throw new ParseFailureException("Label/File parameters must be matched, but " + pairParmCount
                    + " found, which is an odd number.");
        final int pairCount = pairParmCount / 2;
        if (pairCount < 1)
            throw new ParseFailureException("At least one label/file pair must be specified (but 2 or more is better).");
        // Verify the feature ID set.
        if (! this.featIdFile.canRead())
            throw new FileNotFoundException("Feature ID file " + this.featIdFile + " is not found or unreadable.");
        // Read in the feature IDs.  We sort them so that the ordering is predictable.
        this.featureMap = new TreeMap<String, String>(TabbedLineReader.readMap(this.featIdFile, "1", "2"));
        log.info("{} feature IDs read from {}.", this.featureMap.size(), this.featIdFile);
        // Now collect the labels and files.
        this.labels = new ArrayList<String>(pairCount);
        this.inFiles = new ArrayList<File>(pairCount);
        for (int i = 0; i < pairParmCount; i += 2) {
            final String label = this.specs.get(i);
            this.labels.add(label);
            File inFile = new File(this.specs.get(i+1));
            if (! inFile.canRead())
                throw new FileNotFoundException("Bin report file " + inFile + " is not found or unreadable.");
            this.inFiles.add(inFile);
            log.info("File {} will be imported with output label {}.", inFile, label);
        }
        // Finally, validate the subclass options.
        this.validateBinReportParms();
        return true;
    }

    /**
     * Insure that the command-line parameters are valid.  This is called after the files and labels have been
     * validated and the feature IDs loaded, but before the bin reports are imported.
     *
     * @throws IOException
     * @throws ParseFailureException
     */
    protected abstract void validateBinReportParms() throws IOException, ParseFailureException;

    @Override
    protected final void runCommand() throws Exception {
        // Initialize the bin report data object.
        this.data = new BinReport(this.featureMap.keySet());
        // Import the bin report files.
        final int pairCount = this.labels.size();
        for (int i = 0; i < pairCount; i++) {
            String label = this.labels.get(i);
            File inFile = this.inFiles.get(i);
            log.info("Importing file {} of {} using label {}: {}", i+1, pairCount, label, inFile);
            this.data.processFile(label, inFile);
        }
        log.info("{} samples imported using {} features.", this.data.size(), this.data.width());
        // Process the sample data.
        this.runBinReportAnalysis(this.data);

    }

    /**
     * Process the data from the imported samples.
     *
     * @param sampleData	bin report object containing the sample data
     *
     * @throws Exception
     */
    protected abstract void runBinReportAnalysis(BinReport sampleData) throws Exception;

    /**
     * @return the map of feature IDs to feature names
     */
    public Map<String, String> getFeatureMap() {
        return this.featureMap;
    }

    /**
     * @return the first input label specified
     */
    public String getLabel0() {
        return this.labels.get(0);
    }


}
