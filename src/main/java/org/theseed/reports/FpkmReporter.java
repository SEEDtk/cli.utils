/**
 *
 */
package org.theseed.reports;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.Feature;

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
    /** list of samples */
    private List<String> jobNames;
    /** maximum number of samples */
    private int estimatedSamples;
    /** map of features to data rows */
    private SortedMap<Feature, Row> rowMap;

    /**
     * This nested class represents a weight report
     */
    protected static class Weight {
        private boolean exactHit;
        private double weight;

        /**
         * Create a weight record.
         *
         * @param exact		TRUE if this is the weight for an exact hit
         * @param wValue	weight of the hit
         */
        private Weight(boolean exact, double wValue) {
            this.exactHit = exact;
            this.weight = wValue;
        }

        /**
         * @return TRUE if this was an exact hit
         */
        public boolean isExactHit() {
            return this.exactHit;
        }

        /**
         * @return the weight of the hit
         */
        public double getWeight() {
            return this.weight;
        }

    }

    /**
     * This nested class represents a feature row.
     */
    public class Row {
        /** feature hit */
        private Feature feat;
        /** neighbor feature (or NULL) */
        private Feature neighbor;
        /** hit list */
        private Weight[] weights;

        /**
         * Create a row for a feature.
         *
         * @param feat		target feature
         * @param neighbor	useful neighbor
         */
        private Row(Feature feat, Feature neighbor) {
            this.feat = feat;
            this.neighbor = neighbor;
            // Clear the weights.
            this.weights = new Weight[FpkmReporter.this.estimatedSamples];
        }

        /**
         * Store a weight for the current column.
         *
         * @param exact		TRUE if the weight is for an exact hit
         * @param wValue	value of the weight
         */
        private void store(boolean exact, double wValue) {
            Weight w = new Weight(exact, wValue);
            int idx = FpkmReporter.this.jobNames.size() - 1;
            this.weights[idx] = w;
        }

        /**
         * @return the target feature
         */
        public Feature getFeat() {
            return this.feat;
        }

        /**
         * @return the neighbor feature
         */
        public Feature getNeighbor() {
            return this.neighbor;
        }

        /**
         * @return the weight in the specified column
         *
         * @param iCol	column of interest
         */
        public Weight getWeight(int iCol) {
            return this.weights[iCol];
        }

    }


    /**
     * Enumeration of report formats
     */
    public static enum Type {
        TEXT, EXCEL;

        public FpkmReporter create(OutputStream output, IParms processor) {
            FpkmReporter retVal = null;
            switch (this) {
            case TEXT :
                retVal = new TextFpkmReporter(output, processor);
                break;
            case EXCEL:
                retVal = new ExcelFpkmReporter(output, processor);
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

    }


    /**
     * Initialize the report.
     *
     * @param maxSamples	maximum number of samples
     */
    public void startReport(int maxSamples) {
        this.jobNames = new ArrayList<String>(maxSamples);
        this.estimatedSamples = maxSamples;
        // Create the row map.  We sort the features by location.
        this.rowMap = new TreeMap<Feature, Row>(new Feature.LocationComparator());
    }

    /**
     * Begin processing a single sample.
     *
     * @param jobName	sample name
     */
    public void startJob(String jobName) {
        // Save the sample name.
        this.jobNames.add(jobName);
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
        Row fRow = this.rowMap.computeIfAbsent(feat, x -> new Row(x, neighbor));
        // Add this weight.
        fRow.store(exactHit, weight);
    }

    /**
     * Terminate the report.  This actually produces the output.
     */
    public void endReport() {
        this.openReport(this.jobNames);
        for (Row row : this.rowMap.values())
            this.writeRow(row);
    }

    /**
     * Initialize the output.
     *
     * @param jobNames		list of sample names
     */
    protected abstract void openReport(List<String> jobNames);

    /**
     * Write the data for a single row.
     *
     * @param row	row of weights to write for a specific feature.
     */
    protected abstract void writeRow(Row row);

    /**
     * Finish the report and flush the output.
     */
    protected abstract void closeReport();

    @Override
    public void close() {
        this.closeReport();
    }

}
