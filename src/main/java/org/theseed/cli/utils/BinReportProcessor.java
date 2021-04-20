/**
 *
 */
package org.theseed.cli.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
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
 * extracted and the bin counts displayed in a report.  The positional parameters are the name of the output directory and
 * the controlling PATRIC user ID.
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
 *
 * @author Bruce Parrello
 *
 */
public class BinReportProcessor extends BaseProcessor {

    /**
     *
     */
    private static final Tag LINK_FINDER = new Evaluator.Tag("a");
    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(BinReportProcessor.class);
    /** output stream */
    private OutputStream outStream;
    /** match pattern for the bin counts */
    private static final Pattern COUNT_PATTERN = Pattern.compile("<p>(\\d+) <a href=\"#goodList\">good bins</a> and (\\d+) <a href=\"#badList\">bad bins</a> were found");
    /** match pattern for the good-bin table */
    protected static final Pattern TABLE_PATTERN = Pattern.compile("<p><a name=\"goodList\">.+?(<table.+?</table>)", Pattern.DOTALL);


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

    /** input directory */
    @Argument(index = 0, metaVar = "inDir", usage = "input directory in PATRIC workspace", required = true)
    private String inDir;

    /** workspace name */
    @Argument(index = 1, metaVar = "user@patricbrc.org", usage = "PATRIC workspace name", required = true)
    private String workspace;

    @Override
    protected void setDefaults() {
        this.workDir = new File(System.getProperty("user.dir"), "Temp");
        this.outFormat = BinReporter.Type.BINS;
        this.outFile = null;
    }

    @Override
    protected boolean validateParms() throws IOException, ParseFailureException {
        if (! this.workDir.isDirectory()) {
            log.info("Creating working directory {}.", this.workDir);
            FileUtils.forceMkdir(this.workDir);
        } else {
            log.info("Using working directory {}.", this.workDir);
        }
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
        try (BinReporter reporter = this.outFormat.create(this.outStream)) {
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
                    // Now we need to compute the bin counts.
                    int goodBins = 0;
                    int badBins = 0;
                    List<String> binIds = new ArrayList<String>();
                    // Start by trying to get the binning report.
                    try {
                        copyTask.copyRemoteFile(binningReportName, targetFile);
                        // Search the binning report for the desired bin counts.
                        String htmlString = FileUtils.readFileToString(targetFile, Charset.defaultCharset());
                        Matcher m = COUNT_PATTERN.matcher(htmlString);
                        if (! m.find())
                            log.warn("No bin counts found for sample {}.", sampleId);
                        else {
                            // Get the bin counts from the matched substring.
                            goodBins = Integer.valueOf(m.group(1));
                            badBins = Integer.valueOf(m.group(2));
                            if (goodBins > 0) {
                                m = BinReportProcessor.TABLE_PATTERN.matcher(htmlString);
                                if (m.find()) {
                                    log.info("Searching for {} good-bin genome IDs in sample {}.", goodBins, sampleId);
                                    String table = m.group(1);
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
                                            // third cell contains the genome name.
                                            Element cell = cells.get(1);
                                            Element link = cell.selectFirst(LINK_FINDER);
                                            String goodId = link.text();
                                            binIds.add(goodId);
                                            cell = cells.get(0);
                                            link = cell.selectFirst(LINK_FINDER);
                                            double score = Double.valueOf(link.text());
                                            String name = cells.get(2).text();
                                            reporter.goodGenome(goodId, score, name);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (PatricException e) {
                        log.error("Could not copy binning report for sample {}: {}", sampleId, e.getMessage());
                    }
                    // Write out the bin counts.
                    reporter.displaySample(sampleId, badBins, binIds);
                }
            }
            // Finish the report.
            reporter.closeReport();
        } finally {
            if (this.outFile != null)
                this.outStream.close();
        }
    }

}
