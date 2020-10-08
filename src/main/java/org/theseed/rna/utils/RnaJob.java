/**
 *
 */
package org.theseed.rna.utils;

import java.io.File;

import org.apache.commons.lang3.StringUtils;
import org.theseed.cli.utils.CliService;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * This object represents an RNA-Seq processing job in progress.  The job has a task ID that indicates a current
 * task in progress, a name, and a phase.  The phase indicates what the current task is doing.  All phases for
 * a job must be completed before we can call the job complete.
 *
 * @author Bruce Parrello
 *
 */
public class RnaJob implements Comparable<RnaJob> {

    // FIELDS
    /** name of this job */
    private String name;
    /** ID of the current task (or NULL if none) */
    private String taskId;
    /** left input FASTQ */
    private String leftFile;
    /** right input FASTQ */
    private String rightFile;
    /** input directory name */
    private String inDir;
    /** output directory name */
    private String outDir;
    /** current phase */
    private Phase phase;
    /** alignment genome */
    private String alignmentGenomeId;

    /**
     * Enumeration for job phases
     */
    public enum Phase {
        TRIM("_fq"), ALIGN("_rna"), DONE("");

        private String suffix;

        /**
         * Construct a phase
         *
         * @param jobSuffix		suffix for output folders of this job phase
         */
        private Phase(String jobSuffix) {
            this.suffix = jobSuffix;
        }

        /**
         * @return the job name if an output folder is from this phase, else NULL
         *
         * @param jobResult		output folder name to check
         */
        public String checkSuffix(String jobResult) {
            String retVal = null;
            if (StringUtils.endsWith(jobResult, this.suffix))
                retVal = StringUtils.removeEnd(jobResult, this.suffix);
            return retVal;
        }

        /**
         * @return the output folder name for the task required by this phase
         *
         * @param jobName	relevant job name
         */
        public String getOutputName(String jobName) {
            return jobName + this.suffix;
        }

    }

    /**
     * Create an RNA job.
     *
     * @param name			job name
     * @param inDir			input directory name
     * @param outDir		output directory name
     * @param genomeId		alignment genome ID
     */
    public RnaJob(String name, String inDir, String outDir, String genomeId) {
        this.name = name;
        this.taskId = null;
        this.leftFile = null;
        this.rightFile = null;
        this.inDir = inDir;
        this.outDir = outDir;
        this.phase = Phase.TRIM;
        this.alignmentGenomeId = genomeId;
    }

    /**
     * @return the job name
     */
    public String getName() {
        return this.name;
    }

    /**
     * @return the ID of the current task (or NULL if there is none)
     */
    public String getTaskId() {
        return this.taskId;
    }

    /**
     * @return the current phase
     */
    public Phase getPhase() {
        return this.phase;
    }

    /**
     * Specify a left-read file.
     *
     * @param leftFile 		the base name of the left file
     */
    public void setLeftFile(String leftFile) {
        this.leftFile = this.inDir + "/" + leftFile;
    }

    /**
     * Specify a right-read file.
     *
     * @param rightFile 	the base name of the right file
     */
    public void setRightFile(String rightFile) {
        this.rightFile = this.inDir + "/" + rightFile;
    }

    /**
     * @return TRUE if this job has both read files
     */
    public boolean isPrepared() {
        return (this.leftFile != null && this.rightFile != null);
    }

    /**
     * Update this job so that it is at least at the specified phase.
     *
     * @param nextPhase		new phase to store
     *
     * @return TRUE if the job was updated, else FALSE
     */
    public boolean mergeState(Phase nextPhase) {
        boolean retVal = false;
        if (nextPhase.ordinal() > this.phase.ordinal()) {
            this.phase = nextPhase;
            retVal = true;
        }
        return retVal;
    }

    /**
     * Store the ID of the currently-running task for this job.
     *
     * @param value		new task ID to store
     */
    public void setTaskId(String value) {
        this.taskId = value;
    }

    /**
     * @return TRUE if this job does not have a running task
     */
    public boolean needsTask() {
        return (this.taskId != null);
    }

    /**
     * Start the task for the current phase.
     *
     * @param workDir		work directory for temporary files
     * @param workspace		workspace name
     */
    public void startTask(File workDir, String workspace) {
        CliService service = null;
        switch (this.phase) {
        case TRIM:
            service = new TrimService(this, workDir, workspace);
            break;
        case ALIGN:
            service = new AlignService(this, workDir, workspace);
            break;
        case DONE:
            service = null;
        }
        // If this phase has a service, start it.
        if (service != null)
            this.taskId = service.start();
    }

    /**
     * Increment this job to the next phase.
     *
     * @return TRUE if the job is done, else FALSE
     */
    public boolean nextPhase() {
        // A phase change means the task in progress is complete.
        this.taskId = null;
        // Move to the next phase.
        this.phase = Phase.values()[this.phase.ordinal() + 1];
        return (this.phase == Phase.DONE);
    }

    /**
     * @return the output directory name
     */
    public String getOutDir() {
        return this.outDir;
    }

    /**
     * @return the alignment genome ID
     */
    public String getAlignmentGenomeId() {
        return this.alignmentGenomeId;
    }

    /**
     * @return the name of the left-read file
     */
    public String getLeftFile() {
        return this.leftFile;
    }

    /**
     * @return the name of the right-read file
     */
    public String getRightFile() {
        return this.rightFile;
    }

    /**
     * @return a paired-end library list for a pair of read files
     *
     * @param leftFile2		left read file name
     * @param rightFile2	right read file name
     */
    public static JsonArray pairList(String leftFile2, String rightFile2) {
        return new JsonArray()
                .addChain(new JsonObject()
                        .putChain("read1", leftFile2)
                        .putChain("read2", rightFile2));
    }

    /**
     * Jobs are sorted by name.
     */
    @Override
    public int compareTo(RnaJob o) {
        return this.name.compareTo(o.name);
    }

}
