/**
 *
 */
package org.theseed.reports;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.Feature;
import org.theseed.io.TabbedLineReader;
import org.theseed.rna.RnaData;
import org.theseed.rna.RnaData.JobData;

/**
 * This is the base class for the FPKM summary report.  This class is responsible for converting the columnar inputs to
 * rows.  That is, the data comes in by features within sample, but we want to write samples within feature.
 *
 * @author Bruce Parrello
 *
 */
public abstract class FpkmReporter implements AutoCloseable {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(FpkmReporter.class);

    /** repository of collected data */
    private RnaData data;
    /** name of the current sample */
    private String jobName;


    /**
     * Enumeration of report formats
     */
    public static enum Type {
        TEXT, EXCEL, CSV;

        public FpkmReporter create(OutputStream output, IParms processor) {
            FpkmReporter retVal = null;
            switch (this) {
            case TEXT :
                retVal = new TextFpkmReporter.Tab(output, processor);
                break;
            case EXCEL:
                retVal = new ExcelFpkmReporter(output, processor);
                break;
            case CSV:
                retVal = new TextFpkmReporter.CSV(output, processor);
                break;
            }
            return retVal;
        }
    }


    /**
     * interface for retrieving parameters from the controlling processor.
     */
    public interface IParms {

        /**
         * @return the name of the input directory containing all the sample runs
         */
        public String getInDir();

        /**
         * @return the name to give to the main sheet
         */
        public String getSheetName();

    }


    /**
     * Initialize the report.
     */
    public void startReport() { }

    /**
     * Begin processing a single sample.
     *
     * @param jobName	sample name
     *
     * @return TRUE if the sample is valid, else FALSE
     */
    public boolean startJob(String jobName) {
        Integer jobIdx = this.data.findColIdx(jobName);
        boolean retVal = (jobIdx != null);
        if (retVal)
            // Save the sample name.
            this.jobName = jobName;
        return retVal;
    }

    /**
     * Record a hit.
     *
     * @param feat		feature hit
     * @param exactHit	TRUE if the hit was detected by the alignment
     * @param neighbor	neighboring feature
     * @param weight	weight of the hit
     */
    public void recordHit(Feature feat, boolean exactHit, Feature neighbor, double weight) {
        // Get the row for this feature.
        RnaData.Row fRow = this.data.getRow(feat, neighbor);
        // Add this weight.
        fRow.store(this.jobName, exactHit, weight);
    }

    /**
     * Terminate the report.  This actually produces the output.
     */
    public void endReport() {
        this.openReport(this.data.getSamples());
        for (RnaData.Row row : this.data)
            this.writeRow(row);
    }

    /**
     * Initialize the output.
     *
     * @param list		list of sample descriptors
     */
    protected abstract void openReport(List<JobData> list);

    /**
     * Write the data for a single row.
     *
     * @param row	row of weights to write for a specific feature.
     */
    protected abstract void writeRow(RnaData.Row row);

    /**
     * Finish the report and flush the output.
     */
    protected abstract void closeReport();

    @Override
    public void close() {
        this.closeReport();
    }

    /**
     * Read the meta-data file to get the JobData objects.
     *
     * @param in			input stream for meta-data file
     * @param abridgeFlag 	if specified, suspicious samples will be skipped
     *
     * @throws IOException
     */
    public void readMeta(InputStream in, boolean abridgeFlag) throws IOException {
        this.data = new RnaData();
        try (TabbedLineReader reader = new TabbedLineReader(in)) {
            for (TabbedLineReader.Line line : reader) {
                String jobName = line.get(0);
                double production = computeDouble(line.get(1)) / 1000.0;
                double density = computeDouble(line.get(2));
                String oldName = line.get(3);
                boolean suspicious = line.getFlag(5);
                if (! suspicious || ! abridgeFlag)
                    this.data.addJob(jobName, production, density, oldName, suspicious);
            }
        }
        log.info("{} samples found in meta-data file.", this.data.size());
    }

    /**
     * @return a string as a double, converting the empty string into NaN
     *
     * @param string	input string to parse
     */
    private double computeDouble(String string) {
        double retVal = Double.NaN;
        if (! string.isEmpty())
            retVal = Double.valueOf(string);
        return retVal;
    }

    /**
     * Save the accumulated RNA data in binary format
     *
     * @param saveFile		proposed save file
     *
     * @throws IOException
     */
    public void saveBinary(File saveFile) throws IOException {
        log.info("Saving binary output to {}.", saveFile);
        this.data.save(saveFile);
    }

    /**
     * Normalize the data accumulated for this report.
     */
    public void normalize() {
        this.data.normalize();
    }

}
