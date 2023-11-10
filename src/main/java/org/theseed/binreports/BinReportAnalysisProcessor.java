/**
 *
 */
package org.theseed.binreports;

import java.io.File;
import java.io.IOException;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.binreports.scores.ScorePackaging;
import org.theseed.binreports.scores.ScorePackaging.Type;
import org.theseed.basic.ParseFailureException;
import org.theseed.binreports.reports.BinReportReporter;
import org.theseed.utils.BaseBinReportsProcessor;

/**
 * This command analyzes bin reports for different conditions (output labels).  It can be used to produce useful
 * reports or application-ready classifier inputs.
 *
 * The first positional parameter must be the name of a tab-delimited file with headers containing the feature IDs in the first column.
 * and feature names in the second.  In our case, these are representative genome IDs.  The remaining positional parameters are
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
 * -O	output directory (default is the current directory)
 * -o	output file for single-file reports (default is "report.tbl" in the current directory
 *
 * --minScore	minimum score for THRESHOLD score normalization (default 1500.0)
 * --method		score-packaging method for score normalization (default THRESHOLD)
 * --minFrac	minimum fraction of samples required for a group to be considered common (default 0.80)
 * --negLabel	output label to be used for a negative outcome (default is the first one in the parameter list)
 * --format		output format (default XMATRIX)
 * --clear		if specified, the output directory will be erased before processing
 * --divisions	number of divisions for RANK score normalization (default 100)
 *
 * @author Bruce Parrello
 *
 */
public class BinReportAnalysisProcessor extends BaseBinReportsProcessor implements BinReportReporter.IParms {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(BinReportAnalysisProcessor.class);
    /** report writer */
    private BinReportReporter reportWriter;
    /** output file name (if any) */
    private String outFileName;

    // COMMAND-LINE OPTIONS

    /** output directory for reports */
    @Option(name = "--outDir", aliases = { "-O" }, metaVar = "reportDir", usage = "output direxctory for reports")
    private File outDir;

    /** output file for single-file reports */
    @Option(name = "--output", aliases = { "-o" }, metaVar = "ClassDir/testFile.csv", usage = "output file for single-file reports")
    private File outFile;

    /** minimum score for threshold normalization */
    @Option(name = "--minScore", metaVar = "100.0", usage = "minimum score for a representative group to be considered present (THRESHOLD only)")
    private double minScore;

    /** score normalization method */
    @Option(name = "--method", usage = "score normalization method (must be binary)")
    private ScorePackaging.Type methodType;

    /** minimum fraction of samples required to flag a group as common */
    @Option(name = "--minFrac", metaVar = "0.5", usage = "minimum fraction of samples with a condition required to consider a representation group common")
    private double minFrac;

    /** negative-outcome label */
    @Option(name = "--negLabel", metaVar = "control", usage = "output label indicating a negative sample condition (defaults to first label parameter)")
    private String negLabel;

    /** if specified, the output directory will be erased before processing */
    @Option(name = "--clear", usage = "if specified, the output directory will be erased before processing")
    private boolean clearFlag;

    /** output format */
    @Option(name = "--format", usage = "output format")
    private BinReportReporter.Type outputType;

    /** number of divisions for rank normalization */
    @Option(name = "--divisions", aliases = { "-N" }, usage = "number of quantile divisions for rank normalization")
    private int divisions;

    @Override
    protected void setBinReportDefaults() {
        this.minScore = 1500.0;
        this.methodType = ScorePackaging.Type.HIGH_LOW;
        this.minFrac = 0.80;
        this.outDir = new File(System.getProperty("user.dir"));
        this.outFile = null;
        this.outputType = BinReportReporter.Type.XMATRIX;
        this.negLabel = null;
        this.clearFlag = false;
        this.divisions = 100;
    }

    @Override
    protected void validateBinReportParms() throws IOException, ParseFailureException {
        // Validate the minimum fraction.
        if (this.minFrac <= 0.0 || this.minFrac > 1.0)
            throw new ParseFailureException("Minimum fraction must be between 0.0 and 1.0.");
        // Insure the threshold is non-negative.
        if (this.minScore < 0.0)
            throw new ParseFailureException("Minimum-score threshold cannot be negative.");
        // Set the negative label.
        if (this.negLabel == null) {
            this.negLabel = this.getLabel0();
            log.info("Negative-outcome label defaulting to {}.", this.negLabel);
        }
        // Validate the number of divisions.
        if (this.divisions < 2)
            throw new ParseFailureException("Number of divisions must be aat least 2.");
        // Parse out the output directory and file (if any).
        if (this.outFile != null) {
            this.outDir = this.outFile.getParentFile();
            this.outFileName = this.outFile.getName();
            log.info("Output file name specified.  Output directory set to {}.", this.outDir);
        } else
            this.outFileName = null;
        // Create the report writer.  This has to be done last, to insure all the parameter values are filled in.
        // It is here that the output directory is initialized and cleared.
        this.reportWriter = this.outputType.create(this);
    }

    @Override
    protected void runBinReportAnalysis(BinReport sampleData) throws Exception {
        // Store the normalization tuning parameters.
        sampleData.setMinScore(this.minScore);
        sampleData.setDivisions(this.divisions);
        // Process the report.
        this.reportWriter.process(sampleData);
    }

    @Override
    public Type getMethodType() {
        return this.methodType;
    }

    @Override
    public File getOutDir() {
        return this.outDir;
    }

    @Override
    public boolean isClearing() {
        return this.clearFlag;
    }

    @Override
    public String getNegativeLabel() {
        return this.negLabel;
    }

    @Override
    public double getCommonFrac() {
        return this.minFrac;
    }

    @Override
    public String getOutFileName() {
        return this.outFileName;
    }

}
