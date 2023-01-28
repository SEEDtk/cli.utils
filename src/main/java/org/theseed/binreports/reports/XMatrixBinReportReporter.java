/**
 *
 */
package org.theseed.binreports.reports;

import java.io.IOException;
import java.io.PrintWriter;

import org.theseed.binreports.BinReport;
import org.theseed.binreports.BinReport.Sample;

/**
 * This report produces a classifier input directory for the DL4J utilities.  We must generate the label file,
 * the skeleton training table, and the data table from which it will be filled.  Finally, there is a marker
 * file to indicate we want to use random forest.
 *
 * @author Bruce Parrello
 *
 */
public class XMatrixBinReportReporter extends BinReportReporter {

    // FIELDS
    /** output writer for data file */
    private PrintWriter dataWriter;
    /** buffer for building output lines */
    private StringBuilder line;

    public XMatrixBinReportReporter(IParms processor) throws IOException {
        super(processor);
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
        var headings = binReport.getHeadings();
        this.line = new StringBuilder(headings.length * 15 + 50);
        this.line.append("sample_id");
        for (String heading : headings)
            this.line.append('\t').append(heading);
        this.line.append("\tcondition");
        PrintWriter trainingWriter = this.openOutFile("training.tbl");
        this.dataWriter = this.openOutFile("data.tbl");
        String lineString = this.line.toString();
        trainingWriter.println(lineString);
        trainingWriter.flush();
        this.dataWriter.println(lineString);
    }

    @Override
    protected void startLabel(String label) {
    }

    @Override
    protected void processSample(Sample sample, double[] scores) {
        this.line.setLength(0);
        this.line.append(sample.getSampleId());
        for (double score : scores)
            this.line.append('\t').append(score);
        this.line.append('\t').append(sample.getLabel());
        this.dataWriter.println(this.line.toString());
    }

    @Override
    protected void finishLabel() {
    }

    @Override
    protected void finishReport() {
    }

}
