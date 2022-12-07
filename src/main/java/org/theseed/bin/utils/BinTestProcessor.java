/**
 *
 */
package org.theseed.bin.utils;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.cli.DirTask;
import org.theseed.dl4j.eval.stats.QualityKeys;
import org.theseed.genome.Genome;
import org.theseed.io.LineReader;
import org.theseed.utils.BaseProcessor;
import org.theseed.utils.ParseFailureException;

import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * This command tests the latest binning and evaluation code.  It operates on a directory of samples.  The key
 * files in each sample directory are "contigs.fasta", which contains the assembled contigs, and "site.tbl", which
 * describes the sample itself.  Optionally, "excludes.tbl" can contain a list of reference genomes to exclude.
 *
 * Binning operates in five stages.
 *
 * 1.	Use "bins_coverage" to compute coverage.  This creates "output.contigs2reads.txt".
 * 2.	Use "bins_generate" to create the bins.  This creates many files, terminating with "bins.json".
 * 3.	For each "binX.fa" file, submit an annotation request to PATRIC.  This ultimately creates a "binX.gto" file.
 * 4.	Evaluate all the bins using the new evaluator.  This produces an "Eval" directory with an "index.html" file.
 * 5.	Run "vbins_generate" to find viruses.  This produces a "vbins.html" file.
 *
 * The processing for an individual sample is done sequentially, but the bins themselves are managed in parallel.  This helps
 * to keep the parallelism under control.
 *
 * Checking for results is done in a timer loop during the annotation.  The annotations are also run in parallel, but they
 * run on PATRIC itself.  For each bin, we submit the annotation and then copy back the resulting GTO file.
 *
 * The positional parameters are the name of the evaluation model directory, the name of the input directory,  and the
 * name of the PATRIC workspace directory to contain the annotation jobs.  The command-line options are as follows.
 *
 * -h	display command-line usage
 * -v	display more frequent log messages
 *
 * --binParms	name of an optional file containing overriding command-line parameters for the binning, one per line
 * 				(equal-sign format should be used for parameters with values, and none of the parameters should be quoted)
 * --virusDB	if specified, must be the name of the CheckV database directory, and virus binning will be turned on
 * --clear		erase all previous results before beginning
 * --path		path to the binning commands; the default is the value of the environment variable BIN_PATH
 *
 * @author Bruce Parrello
 *
 */
public class BinTestProcessor extends BaseProcessor {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(BinTestProcessor.class);
    /** list of directories containing binning requests */
    private File[] samples;
    /** optional binning parameter overrides */
    private List<String> binParms;
    /** filter for isolating sample directories */
    private static final FileFilter SAMPLE_DIR_FILTER = new SampleDirFilter();
    /** set of file names to preserve when clearing results */
    private static final Set<String> KEEP_NAMES = Set.of("contigs.fasta", "site.tbl", "exclude.tbl");
    /** file name filter to return output GTO files */
    private static final FilenameFilter BIN_RESULT_FILTER = new FilenameFilter() {

        @Override
        public boolean accept(File dir, String name) {
            return name.startsWith("bin.") && name.endsWith(".gto");
        }

    };

    // COMMAND-LINE OPTIONS

    /** optional parameter override file */
    @Option(name = "--binParms", metaVar = "parms.txt", usage = "if specified, file of binning override parameters")
    private File binParmFile;

    /** TRUE to suppress virus binning */
    @Option(name = "--virusDB", usage = "if specified, virus binning database for CheckV")
    private File virusDbDir;

    /** TRUE to clear prior results */
    @Option(name = "--clear", usage = "if specified, prior results will be deleted from the sample directories")
    private boolean clearFlag;

    /** path to binning commands */
    @Option(name = "--path", metaVar = "~/SEEDtk/bin", usage = "path to binning commands")
    private File binPath;

    /** name of evaluation model directory */
    @Argument(index = 0, metaVar = "modelDir", usage = "evaluation model directory", required = true)
    private File modelDir;

    /** name of sample directory */
    @Argument(index = 1, metaVar = "samplesDir", usage = "directory containing sample directories", required = true)
    private File samplesDir;

    /** target PATRIC workspace */
    @Argument(index = 2, metaVar = "workspace", usage = "name of output workspace in PATRIC", required = true)
    private String workspace;

    /**
     * Filter to find sample directories.
     */
    public static class SampleDirFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            boolean retVal = pathname.isDirectory();
            if (retVal) {
                File checkFile = new File(pathname, "contigs.fasta");
                retVal = checkFile.canRead();
            }
            return retVal;
        }

    }

    @Override
    protected void setDefaults() {
        this.binParmFile = null;
        this.clearFlag = false;
        this.virusDbDir = null;
        String binPathEnv = System.getenv("BIN_PATH");
        if (StringUtils.isBlank(binPathEnv))
            binPathEnv = System.getProperty("user.dir");
        this.binPath = new File(binPathEnv);
    }

    @Override
    protected boolean validateParms() throws IOException, ParseFailureException {
        // Insure we have a binning path and a model directory.
        BinPipeline.setBinPath(this.binPath);
        log.info("Binning command path is {}.", this.binPath);
        BinPipeline.setModelDir(this.modelDir);
        log.info("Model directory is {}.", this.modelDir);
        // Verify the virus binning database.
        if (this.virusDbDir != null && ! this.virusDbDir.isDirectory())
            throw new FileNotFoundException("Virus binning database " + this.virusDbDir + " is not found or invalid.");
        // Verify that the input directory is valid.
        if (! this.samplesDir.isDirectory())
            throw new FileNotFoundException("Samples directory " + this.samplesDir + " is not found or invalid.");
        // Insure it has samples in it.
        this.samples = this.samplesDir.listFiles(SAMPLE_DIR_FILTER);
        if (this.samples.length == 0)
            throw new IOException("No samples found in " + this.samplesDir + ".");
        log.info("{} sample directories found in {}.", this.samples.length, this.samplesDir);
        // Verify the workspace directory.
        DirTask dirTask = new DirTask(this.samplesDir, this.workspace);
        if (! dirTask.check(this.workspace))
            throw new FileNotFoundException("PATRIC workspace folder " + this.workspace + " is not found.");
        // Get the bin override parameters.
        if (this.binParmFile == null) {
            log.info("No overrides specified for binning.");
            this.binParms = Collections.emptyList();
        } else if (! this.binParmFile.canRead())
            throw new FileNotFoundException("Binning parameter override file " + this.binParmFile + " is not found or unreadable.");
        else {
            // Read and fix up the parameters.
            List<String> parmRecords = LineReader.readList(this.binParmFile);
            this.binParms = parmRecords.stream().flatMap(x -> this.fixParm(x)).collect(Collectors.toList());
            log.info("Override parameters are {}.", StringUtils.join(this.binParms, " "));
        }
        // Finally, check for file-clearing.
        if (this.clearFlag) {
            log.info("Erasing old results.");
            for (File sample : this.samples) {
                File[] sampleFiles = sample.listFiles();
                int count = 0;
                for (File sampleFile : sampleFiles) {
                    if (! KEEP_NAMES.contains(sampleFile.getName())) {
                        FileUtils.forceDelete(sampleFile);
                        count++;
                    }
                }
                log.info("{} files and subdirectories deleted from {}.", count, sample);
            }
        }
        return true;
    }

    /**
     * Parse an override parameter.  If it uses an equal sign, split it into two parts.
     *
     * @param parm		parameter string to parse
     *
     * @return a stream containing the original parameter or the two parts of it
     */
    private Stream<String> fixParm(String parm) {
        Stream<String> retVal;
        String keyword = StringUtils.substringBefore(parm, "=");
        if (keyword.isEmpty()) {
            // Here there is no keyword.
            retVal = Stream.of(parm);
        } else {
            // Here we pass back the keyword and the value.
            retVal = Stream.of(keyword, StringUtils.substring(parm, keyword.length() + 1));
        }
        return retVal;
    }

    @Override
    protected void runCommand() throws Exception {
        // Create the pipelines.
        List<BinPipeline> pipelines = Arrays.stream(this.samples).sorted()
                .map(x -> new BinPipeline(x, this.binParms, this.workspace, this.virusDbDir))
                .collect(Collectors.toList());
        // Create a stream and call the bin process for each sample directory.
        pipelines.stream().forEach(x -> x.run());
        // Now produce a summary of the bins in the main directory.
        int badTotal = 0;
        int mostlyTotal = 0;
        int goodTotal = 0;
        try (PrintWriter writer = new PrintWriter(new File(this.samplesDir, "quality.tbl"))) {
            writer.println("Sample\tgood_bins\tmostly_good_bins\tbad_bins\ttotal");
            for (File sampleDir : this.samples) {
                File[] binGtos = sampleDir.listFiles(BIN_RESULT_FILTER);
                log.info("{} bin results found in {}.", binGtos.length, sampleDir);
                int goodCount = 0;
                int badCount = 0;
                int mostlyCount = 0;
                for (File gtoFile : binGtos) {
                    Genome gto = new Genome(gtoFile);
                    JsonObject quality = gto.getQuality();
                    // Compute the status.
                    if (! quality.getBooleanOrDefault(QualityKeys.MOSTLY_GOOD))
                        badCount++;
                    else if (quality.getBooleanOrDefault(QualityKeys.HAS_SSU_RNA))
                        goodCount++;
                    else
                        mostlyCount++;
                }
                int total = goodCount + badCount + mostlyCount;
                writer.format("%s\t%d\t%d\t%d\t%d%n", sampleDir.getName(), goodCount, mostlyCount, badCount, total);
                goodTotal += goodCount;
                mostlyTotal += mostlyCount;
                badTotal += badCount;
            }
            writer.println();
            int totalTotal = goodTotal + badTotal + mostlyTotal;
            writer.format("%s\t%d\t%d\t%d\t%d%n", "TOTAL", goodTotal, mostlyTotal, badTotal, totalTotal);
        }

    }

}
