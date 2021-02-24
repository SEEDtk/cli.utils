/**
 *
 */
package org.theseed.rna.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.TreeSet;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.cli.CopyTask;
import org.theseed.genome.Feature;
import org.theseed.genome.Genome;
import org.theseed.io.TabbedLineReader;
import org.theseed.locations.Location;
import org.theseed.reports.FpkmReporter;
import org.theseed.utils.BaseProcessor;

/**
 * This class will produce a summary file from all the FPKM files in a PATRIC directory.  This summary file can
 * be used later to build a web page.
 *
 * The positional parameters are the name of the genome file for the aligned genome, the name of the input PATRIC directory,
 * the name of the relevant workspace, and the name of the output file.  If the input PATRIC directory has already been
 * copied to the local hard drive, the directory name on the local drive can be specified instead.
 *
 * The standard input should contain a tab-delimited metadata file describing each sample.  The file should contain the
 * sample ID in the first column, the production level in the second (in mg/l), the optical density in the third, and the
 * original sample name in the third.  It is expected that some values will be missing.  Only samples present in the file
 * will be processed.  The file should be tab-delimited with headers.
 *
 * The result file will be on the standard output.
 *
 * The command-line options are as follows.
 *
 * -h	display command-line usage
 * -v	display more detailed progress messages
 * -i	input file (if not STDIN)
 *
 * --format		output format (default TEXT)
 * --workDir	work directory for temporary files
 * --normalize	if specified, RNA features will be removed, and the FPKM numbers will be normalized to TPMs
 * --save		if specified, the name of a file to contain a binary version of the output
 * --abridged	if specified, suspicious samples will not be included in the output
 * --local		if specified, a local directory containing the FPKM input files
 *
 *
 * @author Bruce Parrello
 *
 */
public class FpkmSummaryProcessor extends BaseProcessor implements FpkmReporter.IParms {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(FpkmSummaryProcessor.class);
    /** alignment genome */
    private Genome baseGenome;
    /** output report object */
    private FpkmReporter outStream;
    /** features sorted by location */
    private TreeSet<Feature> featuresByLocation;
    /** output file stream */
    private FileOutputStream outFileStream;

    // COMMAND-LINE OPTIONS

    /** output report format */
    @Option(name = "--format", usage = "output format")
    private FpkmReporter.Type outFormat;

    /** work directory for temporary files */
    @Option(name = "--workDir", metaVar = "workDir", usage = "work directory for temporary files")
    private File workDir;

    /** input file */
    @Option(name = "-i", aliases = { "--input" }, metaVar = "meta.tbl", usage = "tab-delimited file of metadata (if not STDIN")
    private File inFile;

    /** if specified, suspicious samples will be skipped */
    @Option(name = "--abridge", aliases = { "--abridged" }, usage = "skip suspicious samples")
    private boolean abridgeFlag;

    /** optional save file */
    @Option(name = "--save", metaVar = "rnaData.ser", usage = "if specified, a file in which to save a binary version of the RNA data")
    private File saveFile;

    /** normalize */
    @Option(name = "--normalize", aliases = { "--normalized" }, usage = "if specified, FPKMs will be converted to TPMs")
    private boolean normalizeFlag;

    /** local file directory */
    @Option(name = "--local", usage = "if specified, a directory containing local copies of the FPKM gene-tracking files")
    private File localDir;

    /** GTO file for aligned genome */
    @Argument(index = 0, metaVar = "base.gto", usage = "GTO file for the base genome", required = true)
    private File baseGenomeFile;

    /** PATRIC directory containing the FPKM files */
    @Argument(index = 1, metaVar = "user@patricbrc.org/inputDirectory", usage = "PATRIC input directory for FPKM tracking files", required = true)
    private String inDir;

    /** controlling workspace name */
    @Argument(index = 2, metaVar = "user@patricbrc.org", usage = "controlling workspace", required = true)
    private String workspace;

    /** output file name */
    @Argument(index = 3, metaVar = "output.txt", usage = "output file name", required = true)
    private File outFile;

    /**
     * File name filter for gene-tracking files
     */
    private static class GeneFileFilter implements FilenameFilter {

        @Override
        public boolean accept(File dir, String name) {
            return (StringUtils.endsWith(name, "_genes.fpkm"));
        }

    }

    @Override
    protected void setDefaults() {
        this.workDir = new File(System.getProperty("user.dir"), "Temp");
        this.outFormat = FpkmReporter.Type.TEXT;
        this.inFile = null;
        this.saveFile = null;
        this.normalizeFlag = false;
        this.abridgeFlag = false;
        this.outFileStream = null;
        this.localDir = null;
    }

    @Override
    protected boolean validateParms() throws IOException {
        if (! this.workDir.isDirectory()) {
            log.info("Creating work directory {}.", this.workDir);
            FileUtils.forceMkdir(this.workDir);
            this.workDir.deleteOnExit();
        } else
            log.info("Temporary files will be stored in {}.", this.workDir);
        // Insure the genome file exists.
        if (! this.baseGenomeFile.canRead())
            throw new FileNotFoundException("Base genome file " + this.baseGenomeFile + " not found or unreadable.");
        this.baseGenome = new Genome(this.baseGenomeFile);
        // Create the reporter.
        this.outFileStream = new FileOutputStream(this.outFile);
        this.outStream = this.outFormat.create(this.outFileStream, this);
        // Verify the local-file directory.
        if (this.localDir != null && ! this.localDir.isDirectory())
            throw new FileNotFoundException("Local-copy directory " + this.localDir + " is not found or invalid.");
        // Process the input.
        if (this.inFile == null) {
            log.info("Reading metadata from standard input.");
            this.outStream.readMeta(System.in, this.abridgeFlag);
        } else {
            log.info("Reading metadata from {}.", this.inFile);
            try (FileInputStream inStream = new FileInputStream(this.inFile)) {
                this.outStream.readMeta(inStream, this.abridgeFlag);
            }
        }
        return true;
    }

    @Override
    protected void runCommand() throws Exception {
        try {
            log.info("Sorting features in {}.", this.baseGenome);
            // Create a sorted list of the features in the genome.
            this.featuresByLocation = new TreeSet<Feature>(new Feature.LocationComparator());
            for (Feature feat : this.baseGenome.getFeatures())
                this.featuresByLocation.add(feat);
            // Insure we have local copies of the FPKM files.
            File[] fpkmFiles;
            if (this.localDir != null) {
                log.info("Locating FPKM tracking files in {}.", this.localDir);
                fpkmFiles = this.localDir.listFiles(new GeneFileFilter());
            } else {
                File tempDir = copyFpkmFiles();
                fpkmFiles = tempDir.listFiles(new GeneFileFilter());
            }
            log.info("{} files to process.", fpkmFiles.length);
            // Loop through the files.
            this.outStream.startReport();
            for (File fpkmFile : fpkmFiles) {
                // Get the sample ID for this file.
                String jobName = RnaJob.Phase.COPY.checkSuffix(fpkmFile.getName());
                if (jobName == null)
                    log.warn("Skipping non-tracking file {}.", fpkmFile.getName());
                else {
                    // Register the job name.
                    boolean ok = this.outStream.startJob(jobName);
                    if (! ok)
                        log.info("Skipping suppressed sample {}.", jobName);
                    else {
                        File samstatFile = new File(fpkmFile.getParentFile(), jobName + ".samstat.html");
                        log.info("Reading samstat information from {}.", samstatFile);
                        this.outStream.readSamStat(jobName, samstatFile);
                        try (TabbedLineReader fpkmStream = new TabbedLineReader(fpkmFile)) {
                            // Parse the file header.
                            log.info("Reading FPKM file for sample {}.", jobName);
                            int fidCol = fpkmStream.findField("tracking_id");
                            int locCol = fpkmStream.findField("locus");
                            int weightCol = fpkmStream.findField("FPKM");
                            // Count the records read.
                            int count = 1;
                            // Read the data lines.
                            for (TabbedLineReader.Line line : fpkmStream) {
                                count++;
                                // Check the weight.
                                double weight = line.getDouble(weightCol);
                                if (weight > 0.0) {
                                    // Get the target feature.
                                    String fid = line.get(fidCol);
                                    Location loc = this.baseGenome.computeLocation(line.get(locCol));
                                    Feature feat = this.baseGenome.getFeature(fid);
                                    boolean exactHit = (feat != null);
                                    // At this point, we either have a location or a feature.  If the feature is missing
                                    // we have NULL.  If the location is invalid (less likely), we also have NULL.  If
                                    // there is no feature, we use the location to find one.
                                    if (feat == null && loc == null)
                                        log.error("Record {} skipped-- no feature and invalid location.", count);
                                    else {
                                        // Get the location of the feature if the location was invalid.
                                        if (loc == null)
                                            loc = feat.getLocation();
                                        // This will hold a neighboring feature.
                                        Feature neighbor = null;
                                        if (feat == null) {
                                            // Find the closest feature to this location.
                                            Feature locus = new Feature("", "", loc);
                                            feat = this.featuresByLocation.ceiling(locus);
                                            if (badContig(loc, feat)) {
                                                // Nothing on the contig, blank the result.
                                                feat = null;
                                            }
                                        }
                                        if (feat == null) {
                                            log.error("Record {} skipped-- cannot find anchoring feature.", count);
                                        } else {
                                            // Here we have an actual feature.  Look for a neighbor.
                                            neighbor = this.featuresByLocation.lower(feat);
                                            if (badContig(loc, neighbor)) {
                                                // Nothing on the same contig, blank the result.
                                                neighbor = null;
                                            }
                                            // Record this result.
                                            this.outStream.recordHit(feat, exactHit, neighbor, weight);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Check for normalization.
            if (this.normalizeFlag) {
                log.info("Normalizing FPKMs to TPMs.");
                this.outStream.normalize();
            }
            // Close out the report.
            this.outStream.endReport();
            // Check for a save file.
            if (this.saveFile != null)
                this.outStream.saveBinary(this.saveFile);
        } finally {
            this.outStream.close();
            this.outFileStream.close();
        }
    }

    /**
     * Copy the necessary FPKM and SAMSTAT.HTML files to a local directory.
     *
     * @return the directory containing the FPKM files copied
     *
     * @throws IOException
     */
    private File copyFpkmFiles() throws IOException {
        log.info("Copying FPKM tracking files from {}.", this.inDir);
        CopyTask copy = new CopyTask(this.workDir, this.workspace);
        File[] allFiles = copy.copyRemoteFolder(this.inDir + "/" + RnaJob.FPKM_DIR);
        return allFiles[0].getParentFile();
    }

    /**
     * @return TRUE if the feature is NULL or has a different contig
     *
     * @param loc	location of interest
     * @param feat	potential neighboring feature
     */
    protected boolean badContig(Location loc, Feature feat) {
        return feat == null || ! feat.getLocation().isContig(loc.getContigId());
    }

    @Override
    public String getInDir() {
        return this.inDir;
    }

    @Override
    public String getSheetName() {
        return (this.normalizeFlag ? "TPM" : "FPKM");
    }

}
