/**
 *
 */
package org.theseed.bin.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.select.Evaluator;
import org.jsoup.select.Evaluator.Tag;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.cli.CopyTask;
import org.theseed.cli.DirEntry;
import org.theseed.cli.DirTask;
import org.theseed.cli.PatricException;
import org.theseed.reports.BinReporter;
import org.theseed.utils.BaseProcessor;
import org.theseed.utils.ParseFailureException;

/**
 * This command produces a report on the bins in an output directory.  The binning report for each job in the directory is
 * extracted and the bin counts displayed in a report.  The positional parameters are the name of the output directory (which
 * is the input directory to this program) and the controlling PATRIC user ID.
 *
 * The basic strategy is to do a DirList on the specified directory, then look at all the job directories.  In each job directory,
 * we pull out the "BinningReport.html" file and extract the key
 *
 * The command-line options are as follows
 *
 * -h	display command-line usage
 * -v	display more detailed log messages
 * -o	output file (if not STDOUT)
 *
 * --workDir	working directory for temporary files (the default is "Temp" in the current directory)
 * --format		type of report desired (bin counts, good genomes)
 * --all		if specified, both good and bad bin IDs will be displayed; the default is to display only good bins
 *
 * @author Bruce Parrello
 *
 */
public class BinReportProcessor extends BaseProcessor implements BinReporter.IParms {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(BinReportProcessor.class);
    /** output stream */
    private OutputStream outStream;
    /** match pattern for the bin counts */
    private static final Pattern COUNT_PATTERN = Pattern.compile("<p>(\\d+) <a href=\"#goodList\">good bins</a> and (\\d+) <a href=\"#badList\">bad bins</a> were found");
    /** match pattern for a bin table */
    protected static final Pattern TABLE_PATTERN = Pattern.compile("<a name=\"(good|bad)List\">.+?(<table.+?</table>)", Pattern.DOTALL);
    /** search criterion for hyperlinks */
    private static final Tag LINK_FINDER = new Evaluator.Tag("a");

    // COMMAND-LINE OPTIONS

    /** working directory for temporary files */
    @Option(name = "--workDir", metaVar = "Temp", usage = "temporary directory for working files")
    private File workDir;

    /** format of the output report */
    @Option(name = "--format", usage = "output report format")
    private BinReporter.Type outFormat;

    /** output file (if not STDOUT) */
    @Option(name = "-o", aliases = { "--output" }, usage = "output file for report (if not STDOUT)")
    private File outFile;

    /** if specified, both good and bad bins will be displayed in detail */
    @Option(name = "--all", usage = "if specified, both good and bad bin details will be output")
    private boolean allFlag;

    /** input directory */
    @Argument(index = 0, metaVar = "inDir", usage = "input directory in PATRIC workspace", required = true)
    private String inDir;

    /** if specified, the name of the RepGen database to use for computing repgen IDs */
    @Option(name = "--repdb", metaVar = "rep200.ser", usage = "repgen database for GENOMES report")
    private File repgenFile;

    /** workspace name */
    @Argument(index = 1, metaVar = "user@patricbrc.org", usage = "PATRIC workspace name", required = true)
    private String workspace;

    @Override
    protected void setDefaults() {
        this.workDir = new File(System.getProperty("user.dir"), "Temp");
        this.outFormat = BinReporter.Type.BINS;
        this.outFile = null;
        this.allFlag = false;
        this.repgenFile = null;
    }

    @Override
    protected boolean validateParms() throws IOException, ParseFailureException {
        if (! this.workDir.isDirectory()) {
            log.info("Creating working directory {}.", this.workDir);
            FileUtils.forceMkdir(this.workDir);
        } else {
            log.info("Using working directory {}.", this.workDir);
        }
        if (this.repgenFile != null && ! this.repgenFile.canRead())
            throw new FileNotFoundException("Repgen file " + this.repgenFile + " is not found or unreadable.");
        if (this.outFile == null) {
            log.info("Output will be to the standard output.");
            this.outStream = System.out;
        } else {
            log.info("Output will be to {}.", this.outFile);
            this.outStream = new FileOutputStream(this.outFile);
        }
        return true;
    }

    @Override
    protected void runCommand() throws Exception {
        try (BinReporter reporter = this.outFormat.create(this.outStream, this)) {
            // Get the input directory name.
            String dirName = StringUtils.substringAfterLast(this.inDir, "/");
            // Write the output header.
            reporter.openReport(dirName);
            // Create a copy task for copying the binning files.
            CopyTask copyTask = new CopyTask(this.workDir, this.workspace);
            // This temporary file will hold the binning report.
            File targetFile = new File(this.workDir, "binReport.html");
            // Get the samples from the input directory.
            log.info("Reading PATRIC directory {}.", this.inDir);
            DirTask dirTask = new DirTask(this.workDir, this.workspace);
            List<DirEntry> dirList = dirTask.list(this.inDir);
            // Loop through the directories, extracting the the binning reports.
            for (DirEntry dir : dirList) {
                // Construct the binning report name.
                String sampleName = dir.getName();
                // Only process if this is a job directory.
                if (sampleName.startsWith(".")) {
                    String binningReportName = this.inDir + "/" + sampleName + "/BinningReport.html";
                    // Chop off the dot to get the actual sample ID.
                    String sampleId = sampleName.substring(1);
                    List<List<String>> binIds = Arrays.asList(new ArrayList<String>(), new ArrayList<String>());
                    // Start by trying to get the binning report.
                    try {
                        copyTask.copyRemoteFile(binningReportName, targetFile);
                        // Search the binning report for the desired bin counts.
                        String htmlString = FileUtils.readFileToString(targetFile, Charset.defaultCharset());
                        Matcher m = COUNT_PATTERN.matcher(htmlString);
                        if (! m.find())
                            log.warn("No bin counts found for sample {}.", sampleId);
                        else {
                            m = BinReportProcessor.TABLE_PATTERN.matcher(htmlString);
                            while (m.find()) {
                                int type = (m.group(1).contentEquals("good") ? 1 : 0);
                                log.info("Searching for {}-bin genome IDs in sample {}.", m.group(1), sampleId);
                                String table = m.group(2);
                                // Parse the table HTML.
                                Document doc = Jsoup.parse(table);
                                // Loop through the table rows, keeping the genome IDs.
                                Elements rows = doc.select(new Evaluator.Tag("tr"));
                                for (Element row : rows) {
                                    Elements cells = row.select(new Evaluator.Tag("td"));
                                    // Only proceed if this table row has data cells (not headers).
                                    if (cells.size() >= 3) {
                                        // The second cell contains the genome ID as text inside a link.
                                        // The first cell contains the score in the same way.  The
                                        // third cell contains the genome name.  The fourth contains
                                        // the reference genome ID.  The 11th contains the DNA size.
                                        Element cell = cells.get(1);
                                        Element link = cell.selectFirst(LINK_FINDER);
                                        String genomeId = link.text();
                                        binIds.get(type).add(genomeId);
                                        cell = cells.get(0);
                                        link = cell.selectFirst(LINK_FINDER);
                                        double score = Double.valueOf(link.text());
                                        String name = cells.get(2).text();
                                        cell = cells.get(3);
                                        link = cell.selectFirst(LINK_FINDER);
                                        String refId;
                                        if (link == null) {
                                            log.warn("No reference genome found for {} in {}.", genomeId, sampleId);
                                            refId = "";
                                        } else
                                            refId = link.text();
                                        cell = cells.get(10);
                                        int dnaSize = Integer.valueOf(cell.text());
                                        reporter.binGenome(sampleId, type, genomeId, score, name, refId, dnaSize);
                                    }
                                }
                            }
                        }
                    } catch (PatricException e) {
                        log.error("Could not copy binning report for sample {}: {}", sampleId, e.getMessage());
                    }
                    // Write out the bin counts.  The good bins are in (1), the bad bins in (0).
                    reporter.displaySample(sampleId, binIds.get(1), binIds.get(0));
                }
            }
            // Finish the report.
            reporter.closeReport();
        } finally {
            if (this.outFile != null)
                this.outStream.close();
        }
    }

    @Override
    public boolean isAllFlag() {
        return this.allFlag;
    }

    @Override
    public File getRepGenDB() {
        return this.repgenFile;
    }

}
