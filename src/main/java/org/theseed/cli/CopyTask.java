/**
 *
 */
package org.theseed.cli;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used for various useful file-copy operations.
 *
 * @author Bruce Parrello
 *
 */
public class CopyTask extends CliTask {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(CopyTask.class);


    /**
     * Construct a copy task.
     *
     * @param workDir		local working directory for temporary files
     * @param workspace		relevant workspace
     */
    public CopyTask(File workDir, String workspace) {
        super("FileCopy", workDir, workspace);
    }

    /**
     * Copy a workspace file to a workspace location.
     *
     * @param sourceFile		full name of the source file
     * @param targetFile		full name of the target file
     */
    public void copyRemoteFile(String sourceFile, String targetFile) {
        List<String> result = this.run("p3-cp", "ws:" + sourceFile, "ws:" + targetFile);
        // The command succeeds if it returns one line that begins with "Copy".
        if (result.size() == 0)
            throw new RuntimeException("PATRIC copy command for " + sourceFile + " failed with no response.");
        String resultString = result.get(0);
        if (! resultString.startsWith("Copy "))
            throw new RuntimeException("Error in PATRIC copy: " + resultString);
        // Here the copy worked.  We can continue.
    }

    /**
     * Copy a remote directory to a temporary local location and return a file list.
     *
     * @param sourceFolder		full name of the source folder
     *
     * @return a list of file objects for the temporary files copied
     *
     * @throws IOException
     */
    public File[] copyRemoteFolder(String sourceFolder) throws IOException {
        // Extract the name of the folder.  Note this is a remote folder so there's no ambiguity about the
        // directory separator.
        String baseName = StringUtils.substringAfterLast(sourceFolder, "/");
        // Insure there is room for this folder in the work directory.
        File tempDir = new File(this.getWorkDir(), baseName);
        if (tempDir.exists()) {
            log.warn("Erasing temporary directory {}.", tempDir);
            FileUtils.forceDelete(tempDir);
        }
        // Run the copy command.
        List<String> result = this.run("p3-cp", "-r", "ws:" + sourceFolder, this.getWorkDir().getPath());
        if (result.size() == 0)
            throw new RuntimeException("PATRIC copy command for " + sourceFolder + " failed with no output.");
        // Now we check the results.
        if (! tempDir.isDirectory())
            throw new RuntimeException("PATRIC copy command for " + sourceFolder + " failed to create directory.");
        // Insure the target directory is deleted when we're done.
        FileUtils.forceDeleteOnExit(tempDir);
        // Get the files copied.
        File[] retVal = tempDir.listFiles();
        return retVal;
    }
}
