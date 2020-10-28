/**
 *
 */
package org.theseed.cli.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.cli.CopyTask;
import org.theseed.io.LineReader;
import org.theseed.rna.utils.FpkmSummaryProcessor;
import org.theseed.rna.utils.RnaJob;
import org.theseed.utils.BaseProcessor;

/**
 * This script runs the fpkmsummary command to generate all eight of the RNA Seq data files.  The command parameters
 * are read in from an input file, which must contain the command parameters, tab-delimited.  The parameter "$DIR" will
 * be replaced by the name of the local directory containing the gene-tracking files.
 *
 * The positional parameters for this command are the input file name, the RNASeq output directory name on PATRIC (which
 * contains the input files), and the workspace name.
 *
 * The command-line options are as follows.
 *
 * -h	display command-line usage
 * -v	show more detailed progress messages
 *
 * --workDir	work directory for temporary files
 *
 * @author Bruce Parrello
 *
 */
public class FpkmAllProcessor extends BaseProcessor {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(FpkmAllProcessor.class);

    // COMMAND-LINE OPTIONS

    /** work directory for temporary files */
    @Option(name = "--workDir", metaVar = "workDir", usage = "work directory for temporary files")
    private File workDir;

    /** input file name */
    @Argument(index = 0, metaVar = "parms.tbl", usage = "input file containing command specifications", required = true)
    private File inFile;

    /** PATRIC directory containing the FPKM files */
    @Argument(index = 1, metaVar = "user@patricbrc.org/inputDirectory", usage = "PATRIC input directory for FPKM tracking files", required = true)
    private String inDir;

    /** controlling workspace name */
    @Argument(index = 2, metaVar = "user@patricbrc.org", usage = "controlling workspace", required = true)
    private String workspace;

    @Override
    protected void setDefaults() {
        this.workDir = new File(System.getProperty("user.dir"), "Temp");
    }

    @Override
    protected boolean validateParms() throws IOException {
        if (! this.workDir.isDirectory()) {
            log.info("Creating work directory {}.", this.workDir);
            FileUtils.forceMkdir(this.workDir);
            this.workDir.deleteOnExit();
        } else
            log.info("Temporary files will be stored in {}.", this.workDir);
        if (! this.inFile.canRead())
            throw new FileNotFoundException("Input file " + this.inFile + " is not found or unreadable.");
        return true;
    }

    @Override
    protected void runCommand() throws Exception {
        // Insure we have an empty space for the work files.
        File fpkmDir = new File(this.workDir, RnaJob.FPKM_DIR);
        if (fpkmDir.exists())
            FileUtils.forceDelete(fpkmDir);
        // Get a directory of the input files.
        log.info("Copying FPKM tracking files from {}.", this.inDir);
        CopyTask copy = new CopyTask(this.workDir, this.workspace);
        File[] fpkmFiles = copy.copyRemoteFolder(this.inDir + "/" + RnaJob.FPKM_DIR);
        log.info("{} files copied into {}.", fpkmFiles.length, fpkmDir);
        // Now open the input file and read the commands.
        try (LineReader commandStream = new LineReader(this.inFile)) {
            for (String[] parms : commandStream.new Section(null)) {
                log.info("Processing command to create {}.", parms[parms.length - 1]);
                // Fix the $DIR parameter.
                for (int i = 0; i < parms.length; i++) {
                    if (parms[i].contentEquals("$DIR"))
                        parms[i] = fpkmDir.getPath();
                }
                FpkmSummaryProcessor processor = new FpkmSummaryProcessor();
                if (processor.parseCommand(parms))
                    processor.run();
            }
        }
    }

}
