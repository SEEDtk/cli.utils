/**
 *
 */
package org.theseed.cli.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.cli.CopyTask;
import org.theseed.cli.DirEntry;
import org.theseed.cli.DirTask;
import org.theseed.cli.PatricException;
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
 *
 * --workDir	working directory for temporary files (the default is "Temp" in the current directory)
 *
 * @author Bruce Parrello
 *
 */
public class BinReportProcessor extends BaseProcessor {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(BinReportProcessor.class);
    /** match pattern for the bin counts */
    private static final Pattern COUNT_PATTERN = Pattern.compile("<p>(\\d+) <a href=\"#goodList\">good bins</a> and (\\d+) <a href=\"#badList\">bad bins</a> were found");

    // COMMAND-LINE OPTIONS

    /** working directory for temporary files */
    @Option(name = "--workDir", metaVar = "Temp", usage = "temporaryt directory for working files")
    private File workDir;

    /** input directory */
    @Argument(index = 0, metaVar = "inDir", usage = "input directory in PATRIC workspace", required = true)
    private String inDir;

    /** workspace name */
    @Argument(index = 1, metaVar = "user@patricbrc.org", usage = "PATRIC workspace name", required = true)
    private String workspace;

    @Override
    protected void setDefaults() {
        this.workDir = new File(System.getProperty("user.dir"), "Temp");
    }

    @Override
    protected boolean validateParms() throws IOException, ParseFailureException {
        if (! this.workDir.isDirectory()) {
            log.info("Creating working directory {}.", this.workDir);
            FileUtils.forceMkdir(this.workDir);
        } else {
            log.info("Using working directory {}.", this.workDir);
        }
        return true;
    }

    @Override
    protected void runCommand() throws Exception {
        // Write the output header.
        System.out.println("sample\tgood\tbad");
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
            String dirName = dir.getName();
            // Only process if this is a job directory.
            if (dirName.startsWith(".")) {
                String binningReportName = this.inDir + "/" + dirName + "/BinningReport.html";
                // Chop off the dot to get the actual sample ID.
                String sampleId = dirName.substring(1);
                // Now we need to compute the bin counts.
                int goodBins = 0;
                int badBins = 0;
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
                    }
                } catch (PatricException e) {
                    log.error("Could not copy binning report for sample {}: {}", sampleId, e.getMessage());
                }
                // Write out the bin counts.
                System.out.format("%s\t%d\t%d%n", sampleId, goodBins, badBins);
            }
        }
    }

}
