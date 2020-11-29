/**
 *
 */
package org.theseed.rna.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.cli.DirEntry;
import org.theseed.cli.DirTask;
import org.theseed.cli.MkDirTask;
import org.theseed.cli.StatusTask;
import org.theseed.io.TabbedLineReader;
import org.theseed.utils.BaseProcessor;
import org.theseed.utils.ParseFailureException;

/**
 * This command runs a directory of RNA sequence data.  This is a complicated process that involves multiple steps.  First, the
 * reads must be trimmed.  Next, the trimmed reads must be aligned to the base genome.  Finally, the FPKM files must be copied
 * into the main output directory.
 *
 * The positional parameters are the name of a file describing the base genomes to use, the name of the input directory
 * containing the fastq files, the name of the output directory, and the name of the workspace.  The directory names are
 * PATRIC directories, not files in the file system.
 *
 * The file describing the base genomes is tab-delimited with headers.  The "pattern" column contains job name patterns and
 * the "genome_id" column contains base genome IDs.
 *
 * The job name is computed from the FASTQ file names.  The command-line options specify the pattern for a fastq file name.
 * The first group is the job name, and the second identifies if the file is left or right.
 *
 * The command-line options are as follows.
 *
 * -h	display command-line usage
 * -v	show more detailed progress
 * -p	pattern for FASTQ files; default is "(.+)_(R[12])_001\\.fastq"
 * -l	identifier for left-read FASTQ files; default is "R1"
 *
 * --maxIter	maximum number of loop iterations
 * --wait		number of minutes to wait
 * --workDir	temporary working directory; default is "Temp" in the current directory
 * --limit		limit when querying the PATRIC task list (must be large enough to capture all of the running tasks); default 1000
 * --maxTasks	maximum number of tasks to run at one time
 *
 * @author Bruce Parrello
 *
 */
public class RnaSeqProcessor extends BaseProcessor {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(RnaSeqProcessor.class);
    /** map of currently-active jobs by name */
    private Map<String, RnaJob> activeJobs;
    /** task for getting task status */
    private StatusTask statusTask;
    /** task for reading a directory */
    private DirTask dirTask;
    /** compiled pattern for FASTQ files */
    private Pattern readPattern;
    /** map of patterns to base genome IDs, in priority order */
    private List<GenomePattern> genomeMap;
    /** update counter for job results */
    private int updateCounter;
    /** wait interval */
    private int waitInterval;

    // COMMAND-LINE OPTIONS

    /** pattern for left FASTQ files */
    @Option(name = "-p", aliases = { "--pattern" }, metaVar = "(.+)_(R[12])\\.fastq", usage = "regular expression for FASTQ file names")
    private String readRegex;

    /** identifier for left-read FASTQ files */
    @Option(name = "-l", aliases = { "--leftId" }, metaVar = "001", usage = "identifier for left-read FASTQ file names")
    private String leftId;

    /** maximum number of iterations to run */
    @Option(name = "--maxIter", metaVar = "70", usage = "maximum number of loop iterations to run before quitting (-1 to loop forever)")
    private int maxIter;

    /** number of minutes to wait between iterations */
    @Option(name = "--wait", metaVar = "5", usage = "number of minutes to wait between loop iterations")
    private int wait;

    /** work directory for temporary files */
    @Option(name = "--workDir", metaVar = "workDir", usage = "work directory for temporary files")
    private File workDir;

    /** maximum number of tasks to return when doing a task search */
    @Option(name = "--limit", metaVar = "100", usage = "maximum number of tasks to return during a task-progress query")
    private int limit;

    /** maximum number of tasks to run at one time */
    @Option(name = "--maxTasks", metaVar = "20", usage = "maximum number of running tasks to allow")
    private int maxTasks;

    /** base genome definition file */
    @Argument(index = 0, metaVar = "baseGenome.tbl", usage = "tab-delimited file containing mapping of job name regexes to base genome IDs", required = true)
    private File baseFile;

    /** input directory */
    @Argument(index = 1, metaVar = "inDir", usage = "input directory in PATRIC workspace", required = true)
    private String inDir;

    /** output directory */
    @Argument(index = 2, metaVar = "outDir", usage = "output directory in PATRIC workspace", required = true)
    private String outDir;

    /** workspace name */
    @Argument(index = 3, metaVar = "user@patricbrc.org", usage = "PATRIC workspace name", required = true)
    private String workspace;

    /**
     * This is a dinky nested class for pairing a genome ID with a regex pattern.
     */
    private static class GenomePattern {
        private Pattern pattern;
        private String genomeId;

        public GenomePattern(String regex, String genomeId) {
            this.genomeId = genomeId;
            this.pattern = Pattern.compile(regex);
        }

        public boolean matches(String jobName) {
            return this.pattern.matcher(jobName).matches();
        }

        public String getGenomeId() {
            return this.genomeId;
        }
    }


    @Override
    protected void setDefaults() {
        this.readRegex = "(.+)_(R[12])_001\\.fastq";
        this.leftId = "R1";
        this.workDir = new File(System.getProperty("user.dir"), "Temp");
        this.limit = 1000;
        this.wait = 7;
        this.maxIter = 100;
        this.maxTasks = 10;
    }

    @Override
    protected boolean validateParms() throws IOException, ParseFailureException {
        this.readPattern = Pattern.compile(this.readRegex);
        if (! this.workDir.isDirectory()) {
            log.info("Creating work directory {}.", this.workDir);
            FileUtils.forceMkdir(this.workDir);
            this.workDir.deleteOnExit();
        } else
            log.info("Temporary files will be stored in {}.", this.workDir);
        // Insure the task limit is reasonable.
        if (this.limit < 100)
            throw new ParseFailureException("Invalid task limit.  The number should be enough to find all running tasks, and must be >= 100.");
        // Verify the base genome table.
        if (! this.baseFile.canRead())
            throw new FileNotFoundException("Base-genome file " + this.baseFile + " not found or unreadable.");
        try (TabbedLineReader baseStream = new TabbedLineReader(this.baseFile)) {
            int patternIdx = baseStream.findField("pattern");
            int genomeIdx = baseStream.findField("genome_id");
            this.genomeMap = new ArrayList<GenomePattern>();
            for (TabbedLineReader.Line line : baseStream) {
                GenomePattern patternMatcher = new GenomePattern(line.get(patternIdx), line.get(genomeIdx));
                this.genomeMap.add(patternMatcher);
            }
            log.info("{} base-genome patterns saved.", this.genomeMap.size());
        }
        // Verify the wait interval.
        if (this.wait < 0)
            throw new ParseFailureException("Invalid wait interval " + Integer.toString(this.wait) + ". Must be >= 0.");
        // Convert the interval from minutes to milliseconds.
        this.waitInterval = wait * (60 * 1000);
        // Verify the task limit.
        if (this.maxTasks < 1)
            throw new ParseFailureException("Maximum number of tasks must be > 0.");
        return true;
    }

    @Override
    protected void runCommand() throws Exception {
        // Initialize the utility tasks.
        this.dirTask = new DirTask(this.workDir, this.workspace);
        this.statusTask = new StatusTask(this.limit, this.workDir, this.workspace);
        // Get all the input files from the input directory.
        this.scanInputDirectory();
        // Determine the completed jobs from the output directory.
        this.scanOutputDirectory();
        // Now we have a list of active jobs and their current state.  Check for existing jobs.
        this.checkRunningJobs();
        // Loop until all jobs are done or we exceed the iteration limit.
        int remaining = this.maxIter;
        Set<RnaJob> incomplete = this.getIncomplete();
        while (! incomplete.isEmpty() && remaining != 0) {
            log.info("{} jobs in progress.", incomplete.size());
            this.processJobs(incomplete);
            incomplete = this.getIncomplete();
            remaining--;
            log.info("Sleeping. {} cycles left.", remaining);
            Thread.sleep(this.waitInterval);
        }
    }

    /**
     * Process all the currently-incomplete jobs.
     *
     * @param incomplete	set of currently-incomplete jobs
     */
    private void processJobs(Set<RnaJob> incomplete) {
        // Separate out the running tasks.
        log.info("Scanning job list.");
        Map<String, RnaJob> activeTasks = new HashMap<String, RnaJob>(incomplete.size());
        Set<RnaJob> needsTask = new TreeSet<RnaJob>();
        for (RnaJob job : incomplete) {
            String taskId = job.getTaskId();
            if (taskId == null)
                needsTask.add(job);
            else
                activeTasks.put(taskId, job);
        }
        // Check the state of the running tasks.
        Map<String, String> taskMap = this.statusTask.getStatus(activeTasks.keySet());
        for (Map.Entry<String, String> taskEntry : taskMap.entrySet()) {
            // Get the task state and the associated job.
            String state = taskEntry.getValue();
            RnaJob job = activeTasks.get(taskEntry.getKey());
            switch (state) {
            case StatusTask.FAILED :
                // Task failed.  Restart it.
                log.warn("Job {} failed in phase {}.  Retrying.", job.getName(), job.getPhase());
                job.startTask(this.workDir, this.workspace);
                break;
            case StatusTask.COMPLETED :
                // Task completed.  Move to the next phase.
                log.info("Job {} completed phase {}.", job.getName(), job.getPhase());
                boolean done = job.nextPhase();
                // The job is no longer active.
                activeTasks.remove(taskEntry.getKey());
                // If it is not done, denote it needs a task.
                if (! done)
                    needsTask.add(job);
                break;
            default :
                // Here the job is still in progress.
                log.debug("Job {} still executing phase {}.", job.getName(), job.getPhase());
            }
        }
        int remaining = this.maxTasks - activeTasks.size();
        // Advance the non-running tasks.
        Iterator<RnaJob> iter = needsTask.iterator();
        while (iter.hasNext() && remaining > 0) {
            RnaJob job = iter.next();
            log.info("Starting job {} phase {}.", job.getName(), job.getPhase());
            job.startTask(this.workDir, this.workspace);
            if (! job.needsTask()) remaining--;
        }
    }

    /**
     * @return the set of incomplete jobs
     */
    private Set<RnaJob> getIncomplete() {
        Set<RnaJob> retVal = this.activeJobs.values().stream().filter(x -> x.getPhase() != RnaJob.Phase.DONE).collect(Collectors.toSet());
        return retVal;
    }

    /**
     * Check for jobs that are already running.
     */
    private void checkRunningJobs() {
        log.info("Checking for status of running jobs.");
        Map<String, String> runningJobMap = this.statusTask.getTasks();
        log.info("{} jobs are already running.", runningJobMap.size());
        for (Map.Entry<String, String> runningJob : runningJobMap.entrySet()) {
            // Get the job name and task ID.
            String jobName = runningJob.getKey();
            RnaJob job = this.activeJobs.get(jobName);
            if (job != null) {
                // Here the job is one we are monitoring.
                job.setTaskId(runningJob.getValue());
            }
        }
    }

    /**
     * Determine which jobs are already partially complete.
     */
    private void scanOutputDirectory() {
        log.info("Scanning output directory {}.", this.outDir);
        List<DirEntry> outputFiles = this.dirTask.list(this.outDir);
        this.updateCounter = 0;
        boolean fpkmFound = false;
        for (DirEntry outputFile : outputFiles) {
            if (outputFile.getType() == DirEntry.Type.JOB_RESULT) {
                // Here we have a possible job result.  Check the phase and adjust the job accordingly.
                String jobFolder = outputFile.getName();
                if (! this.checkJobResult(jobFolder, RnaJob.Phase.ALIGN))
                    this.checkJobResult(jobFolder, RnaJob.Phase.TRIM);
            } else if (outputFile.getName().contentEquals(RnaJob.FPKM_DIR)) {
                this.checkFpkmDirectory();
                fpkmFound = true;
            }
        }
        if (! fpkmFound) {
            log.info("Creating FPKM output directory.");
            MkDirTask mkdir = new MkDirTask(this.workDir, this.workspace);
            mkdir.make(RnaJob.FPKM_DIR, this.outDir);
        }
        log.info("Output directory scan complete. {} job updates recorded.", this.updateCounter);
    }

    /**
     * Process the FPKM directory to determine which FPKM files have already been copied.
     */
    private void checkFpkmDirectory() {
        RnaJob.Phase nextPhase = RnaJob.Phase.values()[RnaJob.Phase.COPY.ordinal() + 1];
        log.info("Scanning {} folder in {}.", RnaJob.FPKM_DIR, this.outDir);
        List<DirEntry> outputFiles = this.dirTask.list(this.outDir + "/" + RnaJob.FPKM_DIR);
        for (DirEntry outputFile : outputFiles) {
            if (outputFile.getType() == DirEntry.Type.TEXT) {
                String fileName = outputFile.getName();
                String jobName = RnaJob.Phase.COPY.checkSuffix(fileName);
                if (jobName != null) {
                    // Here we have a potential FPKM file.  Insure it's for one of our jobs.
                    RnaJob job = this.activeJobs.get(jobName);
                    if (job != null) {
                        job.mergeState(nextPhase);
                        log.info("Job {} updated by found FPKM file {}.", jobName, fileName);
                        this.updateCounter++;
                    }
                }
            }
        }
    }

    /**
     * Check a job result to see if it requires updating a job's state.
     *
     * @param jobFolder		result folder from the output directory
     * @param possible		phase to check for
     *
     * @return TRUE if the folder represents a job result for the indicated phase, else FALSE
     */
    private boolean checkJobResult(String jobFolder, RnaJob.Phase possible) {
        boolean retVal = false;
        RnaJob.Phase nextPhase = RnaJob.Phase.values()[possible.ordinal() + 1];
        // Check to see if this is a possible job result for the indicated phase.
        String jobName = possible.checkSuffix(jobFolder);
        if (jobName != null) {
            // This is a result of the appropriate type.
            retVal = true;
            // Check to see if it is one of ours.
            RnaJob job = this.activeJobs.get(jobName);
            if (job != null) {
                // It is.  Check for failure.  Otherwise, update the state.
                List<DirEntry> folderFiles = this.dirTask.list(this.outDir + "/." + jobFolder);
                if (folderFiles.stream().anyMatch(x -> x.getName().contentEquals("JobFailed.txt")))
                    log.warn("Job {} folder {} contains failure data.", jobName, jobFolder);
                else if (job.mergeState(nextPhase)) {
                    this.updateCounter++;
                    log.info("Job {} updated by completed task {}.", jobName, jobFolder);
                }
            }
        }
        return retVal;
    }

    /**
     * Compute the set of jobs for the input directory's files.
     */
    private void scanInputDirectory() {
        log.info("Scanning input directory {}.", this.inDir);
        List<DirEntry> inputFiles = this.dirTask.list(this.inDir);
        this.activeJobs = new HashMap<String, RnaJob>(inputFiles.size());
        for (DirEntry inputFile : inputFiles) {
            if (inputFile.getType() == DirEntry.Type.READS) {
                // Here we have a file that could be used as input.
                Matcher m = this.readPattern.matcher(inputFile.getName());
                if (m.matches()) {
                    // Here the file is a good one.  Get the job for it.
                    String jobName = m.group(1);
                    boolean isLeft = m.group(2).contentEquals(this.leftId);
                    RnaJob job = this.activeJobs.computeIfAbsent(jobName, x -> new RnaJob(x, this.inDir, this.outDir, this.computeGenome(x)));
                    // Store the file.
                    if (isLeft)
                        job.setLeftFile(inputFile.getName());
                    else
                        job.setRightFile(inputFile.getName());
                }
            }
        }
        // Now we have collected all the jobs.  Delete the ones that lack one of the read files.
        log.info("{} jobs found in input directory.", this.activeJobs.size());
        Iterator<Map.Entry<String, RnaJob>> iter = this.activeJobs.entrySet().iterator();
        while (iter.hasNext()) {
            RnaJob job = iter.next().getValue();
            if (! job.isPrepared())
                iter.remove();
        }
        log.info("{} jobs remaining after incomplete file pairs removed.", this.activeJobs.size());
    }

    /**
     * @return the ID of the genome that should be used to align the specified job
     *
     * @param jobName	name of the job whose alignment genome is desired
     */
    private String computeGenome(String jobName) {
        // Loop through the pattern list until we find a match.
        String retVal = null;
        Iterator<GenomePattern> iter = this.genomeMap.iterator();
        while (iter.hasNext() && retVal == null) {
            GenomePattern curr = iter.next();
            if (curr.matches(jobName))
                retVal = curr.getGenomeId();
        }
        if (retVal == null)
            throw new IllegalArgumentException("No genome ID match found for job " + jobName + ".");
        return retVal;
    }

}
