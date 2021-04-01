/**
 *
 */
package org.theseed.rna.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.cli.CopyTask;
import org.theseed.cli.DirEntry;
import org.theseed.cli.DirEntry.Type;
import org.theseed.cli.DirTask;
import org.theseed.utils.BaseProcessor;
import org.theseed.utils.ParseFailureException;

/**
 * This command consolidates the RNA sequence expression data for multiple processing batches.  The positional parameters
 * are the names of the input directory, the output directory, and the PATRIC user name.  Both directories should be PATRIC
 * workspaces.
 *
 * Each sample will have a SAMSTAT report with a filename of the form
 *
 * 	Tuxedo_0_replicateN_XXXXXXXX_1_ptrim.fq_XXXXXXXX_2.ptrim.fq.bam.samstat.html
 *
 * where "N" is a number and "XXXXXXXX" is the sample ID.  The FPKM mapping file corresponding to this will have the name
 *
 * 	Tuxedo_0_replicateN_genes.fpkm_tracking
 *
 * These files will be renamed to
 *
 * 	XXXXXXXX.samstat.html
 * 	XXXXXXXX_genes.fpkm
 *
 * respectively, in the output folder.
 *
 * PATRIC does not allow overwriting existing files, so samples already in the output folder will be skipped.
 *
 * The command-line options are as follows.
 *
 * -h	display command-line usage
 * -v	show more detailed progress
 *
 * --workDir	temporary working directory; default is "Temp" in the current directory
 *
 * @author Bruce Parrello
 *
 */
public class RnaMapProcessor extends BaseProcessor {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(RnaMapProcessor.class);
    /** list of samples already present in the output folder */
    private Set<String> outputSamples;
    /** map of replicate numbers to sample IDs */
    private Map<String, String> sampleMap;
    /** set of replicate numbers with FPKM files */
    private Set<String> replicateSet;
    /** pattern for SAMSTAT html file name */
    private static final Pattern SAMSTAT_PATTERN = Pattern.compile("Tuxedo_0_replicate(\\d+)_([^_]+)_\\S+\\.bam\\.samstat\\.html");
    /** pattern for FPKM file name */
    private static final Pattern FPKM_PATTERN = Pattern.compile("Tuxedo_0_replicate(\\d+)_genes\\.fpkm_tracking");
    /** SAMSTAT output file name suffix */
    private static final String SAMSTAT_SUFFIX = ".samstat.html";
    /** FPKM output file name suffix */
    private static final String FPKM_SUFFIX = "_genes.fpkm";

    // COMMAND-LINE OPTIONS

    /** work directory for temporary files */
    @Option(name = "--workDir", metaVar = "workDir", usage = "work directory for temporary files")
    private File workDir;

    /** input directory */
    @Argument(index = 0, metaVar = "inDir", usage = "input directory in PATRIC workspace", required = true)
    private String inDir;

    /** output directory */
    @Argument(index = 1, metaVar = "outDir", usage = "output directory in PATRIC workspace", required = true)
    private String outDir;

    /** workspace name */
    @Argument(index = 2, metaVar = "user@patricbrc.org", usage = "PATRIC workspace name", required = true)
    private String workspace;

    @Override
    protected void setDefaults() {
        this.workDir = new File(System.getProperty("user.dir"), "Temp");
    }

    @Override
    protected boolean validateParms() throws IOException, ParseFailureException {
        if (! this.workDir.isDirectory()) {
            log.info("Creating work directory {}.", this.workDir);
            FileUtils.forceMkdir(this.workDir);
            this.workDir.deleteOnExit();
        } else
            log.info("Temporary files will be stored in {}.", this.workDir);
        return true;
    }

    @Override
    protected void runCommand() throws Exception {
        // Scan the output directory to find the samples already copied.
        scanOutputDirectory();
        // The input directory contains multiple jobs.  We get the list of jobs and process them individually.
        List<String> jobs = scanMainDirectory();
        // Scan this input directory to find the replicate numbers and sample IDs.
        for (String job : jobs) {
            scanInputDirectory(job);
            // Create the copy helper.
            CopyTask copyTask = new CopyTask(this.workDir, this.workspace);
            // Loop through the FPKM files.
            for (String replicateNum : this.replicateSet) {
                String sampleId = this.sampleMap.get(replicateNum);
                if (sampleId == null)
                    log.warn("No SAMSTAT file exists for replicate {}.", replicateNum);
                else {
                    log.info("Copying files for sample {}.", sampleId);
                    // Compute the input directory file names.
                    String samStatName = job + "/Tuxedo_0_replicate" + replicateNum + "_" + sampleId
                            + "_1_ptrim.fq_" + sampleId + "_2_ptrim.fq.bam.samstat.html";
                    String fpkmName = job + "/Tuxedo_0_replicate" + replicateNum + "_genes.fpkm_tracking";
                    // Copy them to the output.
                    copyTask.copyRemoteFile(samStatName, this.outDir + "/" + sampleId + SAMSTAT_SUFFIX);
                    copyTask.copyRemoteFile(fpkmName, this.outDir + "/" + sampleId + FPKM_SUFFIX);
                }
            }
        }
    }

    /**
     * Scan the main input directory to find all the job directories in it.
     *
     * @return a list of job directory names
     */
    private List<String> scanMainDirectory() {
        // List the input directory.
        DirTask dirTask = new DirTask(this.workDir, this.workspace);
        log.info("Scanning main input directory {}.", this.inDir);
        List<DirEntry> inFiles = dirTask.list(this.inDir);
        // Create the output list.
        List<String> retVal = new ArrayList<String>(inFiles.size());
        for (DirEntry inFile : inFiles) {
            if (inFile.getType() == Type.JOB_RESULT)
                retVal.add(this.inDir + "/." + inFile.getName());
        }
        log.info("{} job directories found in {}.", retVal.size(), this.inDir);
        return retVal;
    }

    /**
     * Analyze the input directory to find the replicate numbers and the associated sample IDs.
     *
     * @param jobDir	name of job directory
     */
    private void scanInputDirectory(String jobDir) {
        // List the job directory.
        DirTask dirTask = new DirTask(this.workDir, this.workspace);
        log.info("Scanning job directory {}.", jobDir);
        List<DirEntry> inFiles = dirTask.list(jobDir);
        // Run through the job directory files, creating the sample map.  We will also track the replicate numbers
        // for which FPKM files exist.
        this.replicateSet = new HashSet<String>(inFiles.size());
        this.sampleMap = new HashMap<String, String>(inFiles.size());
        for (DirEntry inFile : inFiles) {
            Matcher m = SAMSTAT_PATTERN.matcher(inFile.getName());
            if (m.matches()) {
                // Here we have a SAMSTAT file.  Map the replicate number to the sample ID.
                this.sampleMap.put(m.group(1), m.group(2));
            } else {
                m = FPKM_PATTERN.matcher(inFile.getName());
                if (m.matches()) {
                    // Here we have an FPKM file.  Save the replicate number.
                    replicateSet.add(m.group(1));
                }
            }
        }
        log.info("{} SAMSTAT files found.  {} FPKM files found.", this.sampleMap.size(), this.replicateSet.size());
    }

    /**
     * Scan the output directory for samples already copied.
     */
    private void scanOutputDirectory() {
        DirTask dirTask = new DirTask(this.workDir, this.workspace);
        // List all the files in the output directory.
        log.info("Scanning output directory {}.", this.outDir);
        List<DirEntry> outFiles = dirTask.list(this.outDir);
        // Save all the samples already copied.
        this.outputSamples = outFiles.stream().map(x -> x.getName()).filter(x -> x.endsWith(FPKM_SUFFIX))
                .map(x -> StringUtils.removeEnd(x, FPKM_SUFFIX)).collect(Collectors.toSet());
        log.info("{} samples found in output directory.", this.outputSamples.size());
    }

}
