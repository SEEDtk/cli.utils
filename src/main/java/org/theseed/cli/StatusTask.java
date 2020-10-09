/**
 *
 */
package org.theseed.cli;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;

import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * This class finds the task IDs for the user's running jobs and determines the job name for each (if any).
 *
 * @author Bruce Parrello
 */
public class StatusTask extends CliTask {

    // FIELDS
    /** status code for a queued task */
    public static final String QUEUED = "queued";
    /** status code for a running task */
    public static final String RUNNING = "in-progress";
    /** status code for a completed task */
    public static final String COMPLETED = "completed";
    /** status code for a failed task */
    public static final String FAILED = "failed";
    /** limit of task status lines to return */
    private int limit;

    /**
     * Create a status task object.
     *
     * @param limit			task display limit
     * @param workDir		working directory
     * @param workspace		user workspace
     */
    public StatusTask(int limit, File workDir, String workspace) {
        super("TaskStatus", workDir, workspace);
        this.limit = limit;
    }

    /**
     * Find the task IDs of the user's running jobs.
     *
     * @return a map from the job names to the task IDs
     */
    public Map<String, String> getTasks() {
        // Get the basic task list.
        List<String> taskLines = this.run("appserv-enumerate-tasks", "--offset", "0", "--limit", Integer.toString(limit));
        // This will hold the set of running task IDs.
        Set<String> tasks = new TreeSet<String>();
        for (String taskLine : taskLines) {
            String[] fields = StringUtils.split(taskLine, '\t');
            switch (fields[2]) {
            case RUNNING :
            case QUEUED :
                tasks.add(fields[0]);
            }
        }
        // Now we need to get the job names for these tasks.
        Map<String, String> retVal = new HashMap<String, String>(tasks.size());
        if (tasks.size() > 0) {
            // Build an array from the parameters.  This is the verbose flag followed by the task IDs.
            String[] parms = new String[tasks.size() + 1];
            parms[0] = "-v";
            int i = 1;
            for (String task : tasks)
                parms[i++] = task;
            List<String> statusLines = this.run("appserv-query-task", parms);
            // Now we need to analyze the results.
            JsonObject taskObjects = PerlConverter.parse(statusLines);
            for (Map.Entry<String, Object> taskEntry : taskObjects.entrySet()) {
                String taskId = taskEntry.getKey();
                if (tasks.contains(taskId)) {
                    // Here we have a task of interest.
                    JsonObject status = (JsonObject) taskEntry.getValue();
                    JsonObject parmObject = (JsonObject) status.get("parameters");
                    String jobName = (String) parmObject.get("jobName");
                    // If the job name is NULL, the task is not one of ours.
                    if (jobName != null)
                        retVal.put(jobName, taskId);
                }
            }
        }
        return retVal;
    }

    /**
     * Compute the status of each of the specified tasks.
     *
     * @param taskIds	set of task IDs whose status is desired
     *
     * @return a map from each task ID to its current status
     */
    public Map<String, String> getStatus(Set<String> taskIds) {
        Map<String, String> retVal = new HashMap<String, String>(taskIds.size());
        if (! taskIds.isEmpty()) {
            String[] parms = new String[taskIds.size()];
            parms = taskIds.toArray(parms);
            List<String> statusLines = this.run("appserv-query-task", parms);
            // Loop through the result lines.  This is non-verbose, so we have tab-delimited data.
            for (String line : statusLines) {
                String[] fields = StringUtils.split(line, '\t');
                retVal.put(fields[0], fields[2]);
            }
        }
        return retVal;
    }

}
