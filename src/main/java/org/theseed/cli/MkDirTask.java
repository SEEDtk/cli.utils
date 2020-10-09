/**
 *
 */
package org.theseed.cli;

import java.io.File;

/**
 * This PATRIC task creates a new directory in the workspace.
 *
 * @author Bruce Parrello
 */
public class MkDirTask extends CliTask {

    /**
     * Construct a new make-directory task.
     *
     * @param workDir		working directory for temporary files
     * @param workspace		current workspace
     */
    public MkDirTask(File workDir, String workspace) {
        super("MakeDirectory", workDir, workspace);
    }

    /**
     * Create a new folder in the specified directory.
     *
     * @param dir		name of the taget directory
     * @param folder	name of the new folder to create
     */
    public void make(String dir, String folder) {
        // Form the final path name.
        String path = folder + "/" + dir;
        this.run("p3-mkdir", path);
    }

}
