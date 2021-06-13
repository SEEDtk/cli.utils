/**
 *
 */
package org.theseed.dl4j.utils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * This creates an output directory for the SEED random-forest classifier scripts written using SciKit.
 *
 * @author Bruce Parrello
 *
 */
public class XfilesXMatrixDir extends XMatrixDir {

    // FIELDS
    /** output writer for row.h */
    private PrintWriter rowWriter;
    /** output writer for X */
    private PrintWriter xWriter;
    /** output writer for Y */
    private PrintWriter yWriter;
    /** row counter */
    private int rowCount;

    public XfilesXMatrixDir(File outDir) throws IOException {
        super(outDir);
        // Create the three parallel output files.
        this.rowWriter = new PrintWriter(this.getFile("row.h"));
        this.xWriter = new PrintWriter(this.getFile("X"));
        this.yWriter = new PrintWriter(this.getFile("Y"));
        // Initialize the row count.
        this.rowCount = 0;
    }

    @Override
    public void setLabels(Collection<String> labelSet) throws IOException {
        // Labels are not stored in this format.
    }

    @Override
    public void setColumns(String idName, String[] inputIds, String labelName) throws IOException {
        // Create and fill the col.h file.  Note that the label name and ID name are not needed in this
        // format.
        try (PrintWriter colWriter = new PrintWriter(this.getFile("col.h"))) {
            int colCount = 0;
            for (String inputId : inputIds) {
                colWriter.format("%d\t%s%n", colCount, inputId);
                colCount++;
            }
        }
    }

    @Override
    public void writeSample(String rowId, double[] inputValues, String labelVal) throws IOException {
        // Here we have a single sample's output line.  The row ID goes in row.h, the data array in
        // X, and the label value in Y.  First the row ID.
        this.rowWriter.format("%d\t%s%n", this.rowCount, rowId);
        this.rowCount++;
        // Next, the label value.
        this.yWriter.println(labelVal);
        // Finally, the input values.
        String dataLine = Arrays.stream(inputValues).mapToObj(x -> this.getFormatted(x)).collect(Collectors.joining("\t"));
        this.xWriter.println(dataLine);
    }

    @Override
    public void finish() throws IOException {
        // Close all the writers.
        this.rowWriter.close();
        this.xWriter.close();
        this.yWriter.close();
    }

}
