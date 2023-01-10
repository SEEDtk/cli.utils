/**
 *
 */
package org.theseed.bin.utils;

import static j2html.TagCreator.h1;
import static j2html.TagCreator.p;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.bins.Bin;
import org.theseed.bins.BinGroup;
import org.theseed.bins.generate.BinProcessor;
import org.theseed.cli.AnnoService;
import org.theseed.cli.DirEntry;
import org.theseed.cli.DirTask;
import org.theseed.cli.StatusTask;
import org.theseed.counters.Shuffler;
import org.theseed.dl4j.eval.BinEvalProcessor;
import org.theseed.genome.Genome;
import org.theseed.reports.Html;
import com.github.cliftonlabs.json_simple.JsonException;

/**
 * This object runs a single sample directory through the binning pipeline.
 *
 * Binning operates in four stages.
 *
 * 1.	Use "bins.generate" to create the bins.  This creates many files, terminating with "bins.json".
 * 2.	For each bin, submit an annotation request to PATRIC.  This ultimately creates a series of "bin.X.XXXXXXX.gto" files.
 * 3.	Evaluate all the bins using the new evaluator.  This produces an "Eval" directory with an "index.html" file.
 * 4.	Run "vbins_generate" to find viruses.  This produces a "vbins.html" file.
 *
 * The process is restartable, so if an output file is already present, the stage will be skipped.
 *
 * @author Bruce Parrello
 *
 */
public class BinPipeline {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(BinPipeline.class);
    /** sample directory name */
    private File sampleDir;
    /** binning parameter overrides */
    private List<String> binParms;
    /** directory containing checkV database, or NULL if virus binning is turned off */
    private File checkVDir;
    /** PATRIC workspace for this sample */
    private String sampleSpace;
    /** TRUE if we want to annotate and evaluate genomes */
    private boolean annotateFlag;
    /** maximum safe bin size */
    private static int maxBinSize = 200000000;
    /** path to binning commands */
    private static File BIN_PATH = null;
    /** PERL executable file */
    private static File PERL_PATH = null;
    /** evaluation model directory */
    private static File MODEL_DIR = new File(System.getProperty("user.dir"));

    /**
     * This dinky little object contains the data we need to manage annotation of a bin.
     */
    private class AnnoController {

        /** annotation service object for this bin */
        private AnnoService annotation;
        /** GTO file for this bin */
        private File gtoFile;
        /** original binning object */
        private Bin bin;

        /**
         * Construct an annotation controller for a bin.
         *
         * @param bin0	bin to annotate
         */
        protected AnnoController(Bin bin0) {
            File binFastaFile = bin0.getOutFile();
            if (binFastaFile.length() < BinPipeline.maxBinSize) {
                this.annotation = new AnnoService(bin0.getOutFile(), bin0.getTaxonID(), bin0.getName(), bin0.getDomain(),
                        bin0.getGc(), BinPipeline.this.sampleDir, BinPipeline.this.sampleSpace);
                this.annotation.requestRefGenome(bin0.getRefGenome());
                // Compute the GTO file name.
                this.gtoFile = this.annotation.gtoFileName();
            } else {
                log.info("Skipping annotation for bin {}:  file size too large.", bin0.getName());
                this.annotation = null;
                this.gtoFile = null;
            }
            this.bin = bin0;
        }

        /**
         * Start the annotation and return the task ID.
         *
         * @return the task ID for the annotation request
         */
        protected String start() {
            return this.annotation.start();
        }

        /**
         * @return the name of the output GTO file
         */
        protected File getGtoFile() {
            return this.gtoFile;
        }

        /**
         * @return TRUE if this bin has been annotated
         */
        protected boolean isAnnotated() {
            return this.gtoFile.exists();
        }

        /**
         * @return TRUE if this bin needs annotation
         */
        protected boolean needsAnnotation() {
            return this.annotation != null && ! this.isAnnotated();
        }

        /**
         * @return the name of this bin
         */
        public String getName() {
            return this.bin.getName();
        }

        /**
         * Get the result file from the annotation.
         *
         * @return the result file from the annotation process
         *
         * @throws IOException
         */
        public File getResultFile() throws IOException {
            return this.annotation.getResultFile();
        }


    }

    /**
     * Specify the maximum binnable FASTA file size.
     *
     * @param maxSize	maximum file size
     */
    public static void setMaxFastaSize(int maxSize) {
        maxBinSize = maxSize;
    }

    /**
     * Create a binning pipeline for a particular sample.
     *
     * @param sampDir				directory containing the sample
     * @param overrides				list of override parameters for the binning step
     * @param workspace				name of the output PATRIC workspace
     * @param checkVdb				virus database to use, or NULL to skip virus binning
     */
    public BinPipeline(File sampDir, List<String> overrides, String workspace, File checkVdb) {
        this.sampleDir = sampDir;
        this.binParms = overrides;
        this.annotateFlag = true;
        // Process the virus bin directory.
        this.checkVDir = checkVdb;
        if (BIN_PATH == null)
            throw new IllegalStateException("Binning command path has not been set.");
        final String sampName = sampDir.getName();
        this.sampleSpace = workspace + "/" + sampName;
        DirTask checkDir = new DirTask(sampDir, workspace);
        var entries = checkDir.list(workspace);
        boolean found = entries.stream().anyMatch(x -> x.getName().contentEquals(sampName) && x.getType() == DirEntry.Type.FOLDER);
        if (! found) {
            log.info("Creating workspace folder {}.", this.sampleSpace);
            var output = checkDir.run("p3-mkdir", this.sampleSpace);
            for (String line : output)
                log.info("* " + line);
        }
        // Verify the model directory.
        File modelFile = new File(MODEL_DIR, "roles.to.use");
        if (! modelFile.canRead())
            throw new IllegalStateException(MODEL_DIR + " does not appear to be a valid model directory.");
    }

    /**
     * This is a special hook for testing.
     */
    protected BinPipeline() {
        this.annotateFlag = true;
    }

    /**
     * Suppress annotation.
     *
     * @param binOnly	 TRUE to suppress annotation, FALSE to allow it
     */
    public void suppressAnnotation(boolean binOnly) {
        this.annotateFlag = ! binOnly;
    }

    /**
     * Specify the binning command execution path.  An exception is throw if the binning path
     * is not valid.
     *
     * @param path		path to binning commands
     *
     * @throws FileNotFoundException
     */
    public static void setBinPath(File path) throws FileNotFoundException {
        BIN_PATH = path;
        // Validate the path.
        File binScript = new File(BIN_PATH, "bins_generate.pl");
        if (! binScript.canRead())
            throw new FileNotFoundException("Binning-command path does not contain the binning command.");
        // Now we have to find PERL.
        String[] paths = StringUtils.split(System.getenv("PATH"), File.pathSeparatorChar);
        int i = 0;
        while (PERL_PATH == null) {
            if (i >= paths.length)
                throw new FileNotFoundException("Cannot find a usable PERL executable in the path.");
            File perl0 = new File(paths[i], "perl");
            if (perl0.canExecute())
                PERL_PATH = perl0;
            else {
                perl0 = new File(paths[i], "perl.exe");
                if (perl0.canExecute())
                    PERL_PATH = perl0;
            }
            i++;
        }
        log.info("Perl path is {}.", PERL_PATH);
    }

    /**
     * Set up the evaluation model directory.
     *
     * @param modelDir	evaluation model directory to use
     */
    public static void setModelDir(File modelDir) {
        MODEL_DIR = modelDir;
    }

    /**
     * Run the binning pipeline.
     */
    public void run() {
         try {
             if (! this.checkFile("bins.json")) {
                 // Phase 1: organize the contigs into bins.
                 this.generateBins();
             }
             // Note we check here for the possibility annotation is being suppressed.
             if (this.annotateFlag && ! this.checkFile("Eval/index.html")) {
                 // Phase 2: create GTOs for the bins.  We only create the ones not already there.
                 boolean ok = this.annotateBins();
                 if (ok) {
                     // Phase 3: evaluate the bins.
                     this.evaluateBins();
                 }
             }
             if (this.checkVDir != null && ! this.checkFile("vbins.html")) {
                 // Phase 4: bin the viruses.
                 this.binViruses();
             }
         } catch (IOException e) {
             throw new UncheckedIOException(e);
         } catch (JsonException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Check for the existence of a file in the sample directory.  If the file is unreadable, an exception will
     * be thrown.
     *
     * @param	fileName	base name of file
     *
     * @return TRUE if the file exists, else FALSE
     *
     * @throws IOException
     */
    private boolean checkFile(String fileName) throws IOException {
        File file = new File(this.sampleDir, fileName);
        boolean retVal = file.exists();
        if (retVal) {
            // Check for a file we can't read.
            if (! file.isFile())
                throw new IOException("Binning result file " + file + " is not readable.");
        }
        return retVal;
    }

    /**
     * Generate the bins.
     */
    private void generateBins() {
        log.info("Generating bins for {}.", this.sampleDir);
        // Build the parameter list using the overrides.
        List<String> overrides = this.binParms;
        File overrideFile = new File(this.sampleDir, "bin.parms");
        if (overrideFile.exists())
            overrides = BinTestProcessor.parseParmFile(overrideFile);
        var genParms = new Shuffler<String>(overrides.size() + 3);
        genParms.addAll(overrides);
        // Add the input FASTA file.
        genParms.add1(new File(this.sampleDir, "contigs.fasta").getAbsolutePath());
        // Add the sample directory.
        genParms.add1(this.sampleDir.getAbsolutePath());
        String[] parmArgs = genParms.stream().toArray(String[]::new);
        // Call the generator.
        BinProcessor processor = new BinProcessor();
        boolean ok = processor.parseCommand(parmArgs);
        if (! ok)
            throw new RuntimeException("Command-line parse error in bins.generate.");
        processor.run();
    }

    /**
     * Create the GTO files for the bins.
     *
     * @return TRUE if the GTOs are ready for evaluation, else FALSE
     *
     * @throws JsonException
     * @throws IOException
     */
    private boolean annotateBins() throws IOException, JsonException {
        boolean retVal = true;
        // Get the bins.json file.
        File binFile = new File(this.sampleDir, "bins.json");
        log.info("Reading bin data from {}.", binFile.getAbsolutePath());
        BinGroup binGroup = new BinGroup(binFile);
        // Track the number of annotations made.
        int annoCount = 0;
        // Skip all the work if there are no bins.
        Collection<Bin> bins = binGroup.getSignificantBins();
        if (! bins.isEmpty()) {
            // We are about to do the annotations. Make sure we have a temporary working directory for the P3 temp files.
            File tempDir = new File(this.sampleDir, "Temp");
            if (! tempDir.isDirectory())
                FileUtils.forceMkdir(tempDir);
            // Now we annotate the bins that haven't been annotated yet.  We need a map from task IDs to bin object
            // annotation controllers.
            Map<String, AnnoController> taskMap = new HashMap<String, AnnoController>(bins.size() * 4 / 3 + 1);
            // Create the annotation tasks.
            List<AnnoController> annotations = bins.stream().map(x -> new AnnoController(x))
                    .filter(x -> x.needsAnnotation()).collect(Collectors.toList());
            log.info("Submitting annotation requests for {}.", this.sampleDir);
            for (var annotation : annotations) {
                String taskID = annotation.start();
                taskMap.put(taskID, annotation);
            }
            // We use this to check our progress.
            StatusTask status = new StatusTask(1000, tempDir, this.sampleSpace);
            // Loop until all the annotations are done.
            while (! taskMap.isEmpty()) {
                // Here we wait to prevent wasteful spinning.  We quiesce the interruption exception since it merely means
                // we need to break out early.
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) { }
                var statusUpdates = status.getStatus(taskMap.keySet());
                var iter = statusUpdates.entrySet().iterator();
                while (iter.hasNext()) {
                    var statusEntry = iter.next();
                    String taskId = statusEntry.getKey();
                    AnnoController statusAnno = taskMap.get(taskId);
                    log.debug("Task {} has status {}, producing file {}.", taskId, statusEntry.getValue(),
                            statusAnno.getGtoFile());
                    switch (statusEntry.getValue()) {
                    case StatusTask.FAILED :
                        // Here the annotation failed.  Log an error for later and remove the task.
                        log.error("Failed to annotate bin \"{}\" for sample {}.  Retry later.", statusAnno.getName(), this.sampleDir);
                        retVal = false;
                        taskMap.remove(taskId);
                        break;
                    case StatusTask.COMPLETED :
                        // Here the annotation is done! log success and remove the task.
                        File gtoFile = statusAnno.getResultFile();
                        Genome genome = new Genome(gtoFile);
                        log.info("Genome {} created in file {} (check = {}).", genome, gtoFile, statusAnno.isAnnotated());
                        taskMap.remove(taskId);
                        annoCount++;
                        break;
                    }
                }
            }
        }
        // Check to see if there were no possible annotations.
        if (retVal && annoCount == 0) {
            log.info("No bins found in sample {}.", this.sampleDir);
            // We create a special index.html here to stop further calls to the annotate/eval process.
            File evalDir = this.cleanEvalDir();
            String page = Html.page("Evaluation Summary Report",
                    h1("Evaluation Summary Report"),
                    p("No annotatable bins found in " + this.sampleDir.getName() + ".")
                );
            File summaryFile = new File(evalDir, "index.html");
            FileUtils.writeStringToFile(summaryFile, page, "UTF-8");
            // Return FALSE to skip the evaluation step.
            retVal = false;
        }
        return retVal;
    }

    /**
     * Evaluate the bins into an "Eval" sub-directory.
     */
    private void evaluateBins() {
        try {
            File evalDir = cleanEvalDir();
            // Create a binning evaluator.
            var evaluator = new BinEvalProcessor();
            String[] parms = new String[] { MODEL_DIR.getAbsolutePath(), this.sampleDir.getAbsolutePath(), evalDir.getAbsolutePath() };
            evaluator.parseCommand(parms);
            log.info("Evaluating the bins for {}.", this.sampleDir);
            evaluator.run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Create the evaluation directory, insure it is empty, and return its name.
     *
     * @return the name of the evaluation output directory
     *
     * @throws IOException
     */
    private File cleanEvalDir() throws IOException {
        // We do all the evaluations at the same time, so we are ruthless with the Eval directory.
        File retVal = new File(this.sampleDir, "Eval");
        if (retVal.isDirectory())
            FileUtils.cleanDirectory(retVal);
        else
            FileUtils.forceMkdir(retVal);
        return retVal;
    }

    /**
     * Generate the virus bins.
     */
    private void binViruses() {
        boolean ok = this.runPerl("vbins_generate", this.checkVDir.getAbsolutePath(), this.sampleDir.getAbsolutePath());
        if (! ok)
            throw new RuntimeException("Error in virus phase for " + this.sampleDir + ".");
    }

    /**
     * This is a utility method that runs a PERL script and logs commingled output and error streams.
     *
     * @param name	name of the script to run
     * @param parms			array of parameters to use
     *
     * @return TRUE if successful, else FALSE
     */
    public boolean runPerl(String name, String... parms) {
        boolean retVal = false;
        try {
            File scriptName = new File(BIN_PATH, name + ".pl");
            if (! scriptName.canRead())
                throw new IOException("Cannot read script file " + scriptName + ".");
            // Build the command.
            String[] fullCommand = new String[parms.length + 2];
            fullCommand[0] = PERL_PATH.getAbsolutePath();
            fullCommand[1] = scriptName.getAbsolutePath();
            System.arraycopy(parms, 0, fullCommand, 2, parms.length);
            final ProcessBuilder pb = new ProcessBuilder(fullCommand);
            log.info("Executing command: " + StringUtils.join(fullCommand, ' '));
            pb.redirectErrorStream();
            pb.redirectOutput(new File(this.sampleDir, name + ".log"));
            // Start the process and log the output.
            final Process p = pb.start();
            int exitCode = p.waitFor();
            if (exitCode == 2)
                throw new RuntimeException("Exit code 2 from script.  Probable PERL5LIB error.");
            if (exitCode != 0)
                throw new RuntimeException("Exit code " + Integer.toString(exitCode) + " from " + scriptName.getName() + ".");
            // Denote we've succeeded.
            retVal = true;
        } catch (Exception e) {
            log.error("Exception running script: " + e.toString());
        }
        return retVal;
    }


}
