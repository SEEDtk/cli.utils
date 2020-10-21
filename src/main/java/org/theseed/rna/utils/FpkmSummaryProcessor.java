/**
 *
 */
package org.theseed.rna.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.TreeSet;
import org.apache.commons.io.FileUtils;
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
 * and the name of the relevant workspace.
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

    /** optional save file */
    @Option(name = "--save", metaVar = "rnaData.ser", usage = "if specified, a file in which to save a binary version of the RNA data")
    private File saveFile;

    /** normalize */
    @Option(name = "--normalize", usage = "if specified, FPKMs will be converted to TPMs")
    private boolean normalizeFlag;

    /** GTO file for aligned genome */
    @Argument(index = 0, metaVar = "base.gto", usage = "GTO file for the base genome")
    private File baseGenomeFile;

    /** PATRIC directory containing the FPKM files */
    @Argument(index = 1, metaVar = "user@patricbrc.org/inputDirectory", usage = "PATRIC input directory for FPKM tracking files")
    private String inDir;

    /** controlling workspace name */
    @Argument(index = 2, metaVar = "user@patricbrc.org", usage = "controlling workspace")
    private String workspace;

    @Override
    protected void setDefaults() {
        this.workDir = new File(System.getProperty("user.dir"), "Temp");
        this.outFormat = FpkmReporter.Type.TEXT;
        this.inFile = null;
        this.saveFile = null;
        this.normalizeFlag = false;
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
        this.outStream = this.outFormat.create(System.out, this);
        // Process the input.
        if (this.inFile == null) {
            log.info("Reading metadata from standard input.");
            this.outStream.readMeta(System.in);
        } else {
            log.info("Reading metadata from {}.", this.inFile);
            try (FileInputStream inStream = new FileInputStream(this.inFile)) {
                this.outStream.readMeta(inStream);
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
            // Get local copies of the FPKM files.
            log.info("Copying FPKM tracking files from {}.", this.inDir);
            CopyTask copy = new CopyTask(this.workDir, this.workspace);
            File[] fpkmFiles = copy.copyRemoteFolder(this.inDir + "/" + RnaJob.FPKM_DIR);
            // Loop through the files.
            this.outStream.startReport();
            for (File fpkmFile : fpkmFiles) {
                try (TabbedLineReader fpkmStream = new TabbedLineReader(fpkmFile)) {
                    // Get the sample ID for this file.
                    String jobName = RnaJob.Phase.COPY.checkSuffix(fpkmFile.getName());
                    if (jobName == null)
                        log.warn("Skipping non-tracking file {}.", fpkmFile.getName());
                    else {
                        // Parse the file header.
                        log.info("Reading FPKM file for sample {}.", jobName);
                        int fidCol = fpkmStream.findField("tracking_id");
                        int locCol = fpkmStream.findField("locus");
                        int weightCol = fpkmStream.findField("FPKM");
                        // Register the job name.
                        this.outStream.startJob(jobName);
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
        }
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

}
