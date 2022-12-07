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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.cli.AnnoService;
import org.theseed.cli.DirEntry;
import org.theseed.cli.DirTask;
import org.theseed.cli.StatusTask;
import org.theseed.counters.Shuffler;
import org.theseed.dl4j.eval.BinEvalProcessor;
import org.theseed.genome.Genome;
import org.theseed.io.LineReader;
import org.theseed.reports.Html;
import org.theseed.sequence.FastaInputStream;
import org.theseed.sequence.FastaOutputStream;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonKey;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

/**
 * This object runs a single sample directory through the binning pipeline.
 *
 * Binning operates in six stages.
 *
 * 1.	Use "bins_coverage" to compute coverage.  This creates "output.contigs2reads.txt".
 * 2.	Use "bins_generate" to create the bins.  This creates many files, terminating with "bins.json".
 * 3.	For each bin, submit an annotation request to PATRIC.  This ultimately creates a series of "bin.X.XXXXXXX.gto" files.
 * 4.	Evaluate all the bins using the new evaluator.  This produces an "Eval" directory with an "index.html" file.
 * 5.	Run "vbins_generate" to find viruses.  This produces a "vbins.html" file.
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
    /** path to binning commands */
    private static File BIN_PATH = null;
    /** PERL executable file */
    private static File PERL_PATH = null;
    /** empty list for contigs */
    private static final Collection<JsonArray> NO_CONTIGS = Collections.emptyList();
    /** empty list for refgenomes */
    private static final Collection<String> NO_REFS = Collections.emptyList();
    /** evaluation model directory */
    private static File MODEL_DIR = new File(System.getProperty("user.dir"));

    /**
     * This object represents an individual bin.
     */
    public static class Bin {

        /**
         * This enum contains the JSON key encoding.
         */
        private enum BinKey implements JsonKey {
            REF_GENOMES("refGenomes", NO_REFS),
            TAXON_ID("taxonID", 2),
            NAME("name", "unknown bin genome"),
            CONTIGS("contigs", NO_CONTIGS),
            GC("gc", 11),
            DOMAIN("domain", "Bacteria");

            private String keyName;
            private Object defaultVal;

            private BinKey(String keyName, Object defaultVal) {
                this.keyName = keyName;
                this.defaultVal = defaultVal;
            }

            @Override
            public String getKey() {
                return this.keyName;
            }

            @Override
            public Object getValue() {
                return this.defaultVal;
            }

        }

        /** taxonomic ID of the bin */
        private int taxID;
        /** domain of the bin */
        private String domain;
        /** genetic code of the bin */
        private int gc;
        /** contigs in the bin */
        private Collection<String> contigs;
        /** name of the bin */
        private String name;
        /** mean coverage */
        private double coverage;
        /** list of reference genome IDs */
        private List<String> refGenomes;
        /** length of the bin */
        private int len;
        /** ID number of this bin */
        private int binNum;
        /** name of the FASTA file for the bin */
        private File binFile;
        /** name of the GTO file for the bin */
        private File gtoFile;
        /** annotation service for this bin */
        private AnnoService annotation;

        /**
         * Build a bin descriptor out of its JSON object.
         *
         * @param binData	JSON object describing the bin, from the bins.json
         * @param num		bin number
         * @throws IOException
         */
        protected Bin(JsonObject binData, int num) throws IOException {
            this.binNum = num;
            this.taxID = binData.getIntegerOrDefault(BinKey.TAXON_ID);
            this.domain = binData.getStringOrDefault(BinKey.DOMAIN);
            this.gc = binData.getIntegerOrDefault(BinKey.GC);
            this.refGenomes = binData.getCollectionOrDefault(BinKey.REF_GENOMES);
            Collection<JsonArray> contigList = binData.getCollectionOrDefault(BinKey.CONTIGS);
            this.name = binData.getStringOrDefault(BinKey.NAME);
            double covgTotal = 0.0;
            this.len = 0;
            this.contigs = new ArrayList<String>(contigList.size());
            for (JsonArray contig : contigList) {
                this.contigs.add(contig.getString(0));
                int contigLen = contig.getInteger(1);
                covgTotal += contig.getDouble(2) * contigLen;
                this.len += contigLen;
            }
            this.coverage = covgTotal / this.len;
            if (this.refGenomes.size() < 1)
                throw new IOException(String.format("Reference genomes missing from bin %d with taxon %d.", num, this.taxID));
            // Denote there are no files yet and no annotation in progress.
            this.binFile = null;
            this.gtoFile = null;
            this.annotation = null;
        }

        /**
         * @return the taxonomic ID of the bin
         */
        public int getTaxID() {
            return this.taxID;
        }

        /**
         * @return the domain of the bin
         */
        public String getDomain() {
            return this.domain;
        }

        /**
         * @return the genetic code of the bin
         */
        public int getGc() {
            return this.gc;
        }

        /**
         * @return the list of contig IDs in the bin
         */
        public Collection<String> getContigs() {
            return this.contigs;
        }

        /**
         * @return the name of the bin
         */
        public String getName() {
            return this.name;
        }

        /**
         * @return the coverage of the bin
         */
        public double getCoverage() {
            return this.coverage;
        }

        /**
         * @return the list of reference genome IDs
         */
        public List<String> getRefGenomes() {
            return this.refGenomes;
        }

        /**
         * @return the primary reference genome ID
         */
        public String getRefGenome() {
            return this.refGenomes.get(0);
        }

        /**
         * @return the length of the bin in base pairs
         */
        public int getLen() {
            return this.len;
        }

        /**
         * @return the ID number of the bin
         */
        public int getBinNum() {
            return this.binNum;
        }

        /**
         * @return the name of the bin's FASTA file
         */
        protected File getBinFile() {
            return this.binFile;
        }

        /**
         * Specify the name of the bin's FASTA file.
         *
         * @param binFile 	the proposed bin file name
         */
        protected void setBinFile(File binFile) {
            this.binFile = binFile;
            String gtoName = StringUtils.substringBeforeLast(this.binFile.getName(), ".") + ".gto";
            this.gtoFile = new File(this.binFile.getParentFile(), gtoName);
        }

        /**
         * @return the name of the GTO file for this bin
         */
        protected File getGtoFile() {
            return this.gtoFile;
        }

        /**
         * @return TRUE if this bin already has a GTO
         */
        protected boolean isAnnotated() {
            return this.gtoFile != null && this.gtoFile.exists();
        }

        /**
         * @return the annotation service object for this bin
         */
        protected AnnoService getAnnotation() {
            return this.annotation;
        }

        /**
         * Save this bin's annotation service object.
         *
         * @param annotation 	the service object to save
         */
        protected void setAnnotation(AnnoService annotation) {
            this.annotation = annotation;
        }

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
    protected BinPipeline() { }

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
             if (! this.checkFile("output.contigs2reads.txt")) {
                 // Phase 1:  create the coverage file.
                 this.computeCoverage();
             }
             if (! this.checkFile("bins.json")) {
                 // Phase 2: organize the contigs into bins.
                 this.generateBins();
             }
             if (! this.checkFile("Eval/index.html")) {
                 // Phase 3: create GTOs for the bins.  We only create the ones not already there.
                 boolean ok = this.annotateBins();
                 if (ok) {
                     // Phase 4: evaluate the bins.
                     this.evaluateBins();
                 }
             }
             if (this.checkVDir != null && ! this.checkFile("vbins.html")) {
                 // Phase 5: bin the viruses.
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
     * Create the coverage map from the contig file.
     */
    private void computeCoverage() {
        log.info("Creating coverage file for {}.", this.sampleDir);
        File contigFile = new File(this.sampleDir, "contigs.fasta");
        boolean ok = runPerl("bins_coverage", contigFile.getAbsolutePath(), this.sampleDir.getAbsolutePath());
        if (! ok)
            throw new RuntimeException("Error in coverage phase for " + this.sampleDir + ".");
    }

    /**
     * Generate the bins.
     */
    private void generateBins() {
        log.info("Generating bins for {}.", this.sampleDir);
        // Build the parameter list using the overrides.
        var genParms = new Shuffler<String>(this.binParms.size() + 2);
        genParms.addAll(this.binParms);
        File statsFile = new File(this.sampleDir, "bins.stats.txt");
        genParms.add1("--statistics-file").add1(statsFile.getAbsolutePath());
        // Add the sample directory.
        genParms.add1(this.sampleDir.getAbsolutePath());
        String[] parmArgs = genParms.stream().toArray(String[]::new);
        // Call the generator.
        boolean ok = runPerl("bins_generate", parmArgs);
        if (! ok)
            throw new RuntimeException("Error in generate phase for " + this.sampleDir);
    }

    /**
     * Create the FASTA and GTO files for the bins.
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
        List<Bin> bins = this.readBins(binFile);
        // Skip all the work if there are no bins.
        if (bins.isEmpty()) {
            log.info("No bins found in sample {}.", this.sampleDir);
            // We create a special index.html here to stop further calls to the annotate/eval process.
            File evalDir = this.cleanEvalDir();
            String page = Html.page("Evaluation Summary Report",
                    h1("Evaluation Summary Report"),
                    p("No bins found in " + this.sampleDir.getName() + ".")
                );
            File summaryFile = new File(evalDir, "index.html");
            FileUtils.writeStringToFile(summaryFile, page, "UTF-8");
            // Return FALSE to skip the evaluation step.
            retVal = false;
        } else {
            // Now we create the FASTA files from these bins.  First, we build an array of FASTA output streams
            // and map each contig to its stream.
            log.info("Creating bin FASTA files.");
            List<FastaOutputStream> binStreams = new ArrayList<FastaOutputStream>(bins.size());
            try {
                // This will be the contig mapping.
                Map<String, FastaOutputStream> binMap = new HashMap<String, FastaOutputStream>(4000);
                // Now we loop through the bins.
                for (var bin : bins) {
                    // Compute the FASTA file name.
                    File fileName = new File(this.sampleDir, String.format("bin.%d.%d.fa", bin.getBinNum(), bin.getTaxID()));
                    bin.setBinFile(fileName);
                    // Only proceed if there is no GTO file for this bin.  If there is a GTO file, the FASTA file already exists
                    // and we've used it to annotate.
                    if (! bin.isAnnotated()) {
                        final FastaOutputStream binStream = new FastaOutputStream(fileName);
                        binStreams.add(binStream);
                        bin.getContigs().stream().forEach(x -> binMap.put(x, binStream));
                    }
                }
                // Are there any bin files to rebuild?
                if (binMap.size() <= 0)
                    log.info("All bins are already annotated for {}.", this.sampleDir);
                else {
                    // Now we can read the contig file and place the binned contigs.
                    try (FastaInputStream inStream = new FastaInputStream(new File(this.sampleDir, "contigs.fasta"))) {
                        int binnedCount = 0;
                        int skipCount = 0;
                        for (var contig : inStream) {
                            var outStream = binMap.get(contig.getLabel());
                            if (outStream == null)
                                skipCount++;
                            else {
                                outStream.write(contig);
                                binnedCount++;
                            }
                        }
                        log.info("{} new contigs binned and {} skipped for {}.", binnedCount, skipCount, this.sampleDir);
                    }
                }
            } finally {
                for (var stream : binStreams)
                    stream.close();
            }
            // Make sure we have a temporary working directory for the P3 temp files.
            File tempDir = new File(this.sampleDir, "Temp");
            if (! tempDir.isDirectory())
                FileUtils.forceMkdir(tempDir);
            // Now we annotate the bins that haven't been annotated yet.  We need a map from task IDs to bin objects.
            Map<String, Bin> taskMap = new HashMap<String, Bin>(bins.size() * 4 / 3 + 1);
            // Create the annotation tasks.
            log.info("Submitting annotation requests for {}.", this.sampleDir);
            for (var bin : bins) {
                if (! bin.isAnnotated()) {
                    AnnoService annotation = new AnnoService(bin.getBinFile(), bin.getTaxID(), bin.getName(), bin.getDomain(),
                            bin.getGc(), this.sampleDir, this.sampleSpace);
                    // Attach the reference genome.
                    annotation.requestRefGenome(bin.getRefGenome());
                    // Start up the annotation and connect the bin to the task ID.
                    bin.setAnnotation(annotation);
                    String taskID = annotation.start();
                    taskMap.put(taskID, bin);
                }
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
                    Bin statusBin = taskMap.get(taskId);
                    log.debug("Task {} has status {}, producing file {}.", taskId, statusEntry.getValue(),
                            statusBin.getGtoFile());
                    switch (statusEntry.getValue()) {
                    case StatusTask.FAILED :
                        // Here the annotation failed.  Log an error for later and remove the task.
                        log.error("Failed to annotate bin {} for sample {}.  Retry later.", statusBin.getName(), this.sampleDir);
                        retVal = false;
                        taskMap.remove(taskId);
                        break;
                    case StatusTask.COMPLETED :
                        // Here the annotation is done! log success and remove the task.
                        File gtoFile = statusBin.getAnnotation().getResultFile();
                        Genome genome = new Genome(gtoFile);
                        log.info("Genome {} created in file {} (check = {}).", genome, gtoFile, statusBin.gtoFile);
                        taskMap.remove(taskId);
                        break;
                    }
                }
            }
        }
        return retVal;
    }

    /**
     * Read all the bins from the specified bin file.
     *
     * @param binFile	bins.json file containing the bin definitions
     *
     * @return a list of the bin objects
     *
     * @throws IOException
     * @throws JsonException
     */
    private List<Bin> readBins(File binFile) throws IOException, JsonException {
        var retVal = new ArrayList<Bin>();
        try (LineReader binStream = new LineReader(binFile)) {
            StringBuffer binString = new StringBuffer(1000);
            int binCount = 1;
            for (var line : binStream) {
                if (line.contentEquals("//")) {
                    // Here we are at the end of a bin.  Deserialize it and clear the buffer for the next bin.
                    JsonObject binJson = (JsonObject) Jsoner.deserialize(binString.toString());
                    Bin bin = new Bin(binJson, binCount);
                    retVal.add(bin);
                    binCount++;
                    binString.setLength(0);
                } else
                    binString.append(line);
            }
            log.info("{} bins read for sample {}.", retVal.size(), this.sampleDir);
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
