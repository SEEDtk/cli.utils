/**
 *
 */
package org.theseed.binreports.reports;

import java.io.IOException;
import java.io.PrintWriter;

import org.theseed.binreports.BinReport;
import org.theseed.binreports.BinReport.Sample;

/**
 * This is a subclass of a bin-report reporter that produces a machine learning input file.
 *
 * @author Bruce Parrello
 *
 */
public class XFileBinReportReporter extends BinReportReporter {

    /** output writer for data file */
    private PrintWriter dataWriter;
    /** buffer for building output lines */
    private StringBuilder line;
    /** delimiter to use for the file (comma for CSV, tab for TBL) */
    private char delim;
    /** output file name to use */
    private String outFileName;

    /**
     * Construct a bin reporter that produces a machine learning input file.
     * @param processor
     * @param delimiter		delimiter to use (tab or comma)
     *
     * @throws IOException
     */
    public XFileBinReportReporter(IParms processor, char delimiter) throws IOException {
        super(processor);
        this.delim = delimiter;
        this.outFileName = processor.getOutFileName();
        // We need an output file name, so default one if it is not provided.
        if (this.outFileName == null) {
            this.outFileName = "testFile.txt";
            log.info("Using default output file name {} for report.", this.outFileName);
        }
    }

    /**
     * Initialize the line buffer and output the data file header.
     *
     * @param binReport		source bin report
     * @oaram name			name to give to the data file
     *
     * @return the heading line being output
     *
     * @throws IOException
     */
    protected String setHeadings(BinReport binReport, String name) throws IOException {
        // Get the input column headings and form the header.
        var headings = binReport.getHeadings();
        this.line = new StringBuilder((headings.length + 2) * 15);
        this.line.append("sample_id");
        for (String heading : headings)
            this.line.append(this.delim).append(heading);
        this.line.append(this.delim).append("condition");
        this.dataWriter = this.openOutFile(name);
        String retVal = this.line.toString();
        this.dataWriter.println(retVal);
        return retVal;
    }

    @Override
    protected void startReport(BinReport binReport) throws IOException {
        this.setHeadings(binReport, this.outFileName);
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
