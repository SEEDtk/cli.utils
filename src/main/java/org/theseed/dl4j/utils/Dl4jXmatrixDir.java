/**
 *
 */
package org.theseed.dl4j.utils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import org.theseed.io.MarkerFile;

/**
 * This creates an input directory for the DL4J random-forest classifier.  That directory contains a label file
 * (labels.txt), a dummy training file (training.tbl), the random-forest indicator file (decider.txt), and a prototype
 * training file with the actual data in it (data.tbl).
 *
 * @author Bruce Parrello
 *
 */
public class Dl4jXmatrixDir extends XMatrixDir {

    // FIELDS
    /** writer for the data table */
    private PrintWriter writer;
    /** buffer for building output lines */
    private StringBuilder buffer;

    public Dl4jXmatrixDir(File outDir) throws IOException {
        super(outDir);
        // Open the data file for output.
        this.writer = new PrintWriter(this.getFile("data.tbl"));
        // Create the decider.
        MarkerFile.write(this.getFile("decider.txt"), "RandomForest");
    }

    @Override
    public void setLabels(Collection<String> labelSet) throws IOException {
        // The labels go into the label file, one per line, in order.
        try (PrintWriter labelWriter = new PrintWriter(this.getFile("labels.txt"))) {
            for (String label : labelSet)
                labelWriter.println(label);
        }
    }

    @Override
    public void setColumns(String idName, String[] inputIds, String labelName) throws IOException {
        // Here we create the dummy training file, and also the first line of the main data file.
        // Both contain a formatted header line.  We take this opportunity to allocate the main
        // string buffer.
        this.buffer = new StringBuilder(inputIds.length * 10 + idName.length() + labelName.length() + 2);
        this.buffer.append(idName).append("\t");
        for (String inputId : inputIds)
            this.buffer.append(inputId).append("\t");
        this.buffer.append(labelName);
        String headerLine = this.buffer.toString();
        // Now write the training dummy.
        try (PrintWriter dummyWriter = new PrintWriter(this.getFile("training.tbl"))) {
            dummyWriter.println(headerLine);
        }
        // Start the data file.
        this.writer.println(headerLine);
    }

    @Override
    public void writeSample(String rowId, double[] inputValues, String labelVal) {
        // Build the output line in the buffer.
        this.buffer.setLength(0);
        this.buffer.append(rowId).append("\t");
        for (double inputVal : inputValues)
            this.buffer.append(this.getFormatted(inputVal)).append("\t");
        this.buffer.append(labelVal);
        // Write it to the output.
        this.writer.println(this.buffer.toString());
    }

    @Override
    public void finish() {
        // Close the output writer.
        this.writer.close();
    }

}
