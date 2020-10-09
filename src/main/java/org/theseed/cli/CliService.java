/**
 *
 */
package org.theseed.cli;

import java.io.File;

/**
 * This is a subclass of CliTask designed for asynchronously running services.
 *
 * @author Bruce Parrello
 */
public abstract class CliService extends CliTask {

    /**
     * Construct a new service object.
     *
     * @param jobName		job name for this service instance
     * @param workDir		work directory for temporary files
     * @param workspace		relevant workspace name
     */
    public CliService(String jobName, File workDir, String workspace) {
        super(jobName, workDir, workspace);
    }

    /**
     * Start the service.
     *
     * @return the task ID for the running service
     */
    public String start() {
        this.startService();
        return this.getTaskId();
    }

    /**
     * Submit the service request for this task
     */
    protected abstract void startService();

}
