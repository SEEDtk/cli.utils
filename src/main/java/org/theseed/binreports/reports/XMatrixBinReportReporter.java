/**
 *
 */
package org.theseed.binreports.reports;

import java.io.IOException;
import java.io.PrintWriter;

import org.theseed.binreports.BinReport;

/**
 * This report produces a classifier input directory for the DL4J utilities.  We must generate the label file,
 * the skeleton training table, and the data table from which it will be filled.  Finally, there is a marker
 * file to indicate we want to use random forest.
 *
 * This class is pretty much the same as XFileBinReportReporter, except that extra files are needed in the output
 * directory.
 *
 * @author Bruce Parrello
 *
 */
public class XMatrixBinReportReporter extends XFileBinReportReporter {

    public XMatrixBinReportReporter(IParms processor) throws IOException {
        super(processor, '\t');
    }

    @Override
    protected void startReport(BinReport binReport) throws IOException {
        // Create the label file.
        String[] labels = this.getLabels();
        PrintWriter labelWriter = this.openOutFile("labels.txt");
        for (String label : labels)
            labelWriter.println(label);
        labelWriter.flush();
        // Write the marker file.
        PrintWriter markerWriter = this.openOutFile("decider.txt");
        markerWriter.println("RandomForest");
        markerWriter.flush();
        // Now we create the header line for the skeleton training file and the data file.
        String lineString = this.setHeadings(binReport, "data.tbl");
        // Write out the skeleton training file.
        PrintWriter trainingWriter = this.openOutFile("training.tbl");
        trainingWriter.println(lineString);
        trainingWriter.flush();
    }

}
