/**
 *
 */
package org.theseed.binreports.reports;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.theseed.binreports.BinReport;
import org.theseed.binreports.BinReport.Sample;


/**
 * This report is used to compare scores between output labels.  For each output label, it computes the trimean, mean,
 * standard deviation, and IQR of the scores for all the samples with that label.
 *
 * The data structure to support this is a hash mapping each output label to an array of DescriptiveStatistics objects.
 * The array is parallel to the scores, so for an input column, the array contains the scores in the same index position
 * as in the score array.
 *
 * Note that most normalized scores are between 0 and 1, so we are generally looking at values over a 0/1 range.
 *
 * @author Bruce Parrello
 *
 */
public class CompareBinReportReporter extends BinReportReporter {

    // FIELDS
    /** map of labels to statistics arrays */
    private Map<String, DescriptiveStatistics[]> statsMap;
    /** output file name */
    private String outFileName;
    /** output file stream */
    private PrintWriter reportWriter;
    /** list of input column labels, in order */
    private String[] repIds;


    /**
     * Insure we have all the parameters we need for this report.
     *
     * @param processor		controlling command processor
     *
     * @throws IOException
     */
    public CompareBinReportReporter(IParms processor) throws IOException {
        super(processor);
        // Insure we have a name for the output file.
        this.outFileName = processor.getOutFileName();
        if (this.outFileName == null) {
            this.outFileName = "compareLabels.tbl";
            log.info("Using default output file name of {} for this report.", this.outFileName);
        }
    }

    @Override
    protected void startReport(BinReport binReport) throws IOException {
        // Get the list of labels and the size of the scoring array and create the stats map.
        var labels = this.getLabels();
        var width = binReport.width();
        this.statsMap = new TreeMap<String, DescriptiveStatistics[]>();
        for (String label : labels) {
            var statsArray = new DescriptiveStatistics[width];
            IntStream.range(0, width).forEach(i -> statsArray[i] = new DescriptiveStatistics());
            this.statsMap.put(label, statsArray);
        }
        // Create the output file for the report.
        this.reportWriter = this.openOutFile(this.outFileName);
        this.reportWriter.println("label\trep_id\tmean\tstd_dev\ttrimean\tIQR");
        // Get the list of input column labels, in scoring array order.
        this.repIds = binReport.getHeadings();
    }

    @Override
    protected void startLabel(String label) {
    }

    @Override
    protected void processSample(Sample sample, double[] scores) {
        // Here we add the sample's data to all the statistics objects.
        DescriptiveStatistics[] statsArray = this.statsMap.get(sample.getLabel());
        for (int i = 0; i < scores.length; i++)
            statsArray[i].addValue(scores[i]);
    }

    @Override
    protected void finishLabel() {
    }

    @Override
    protected void finishReport() {
        // Now we produce the output.  We start with the individual labels.  For each pair of labels, we associate with every
        // score the difference between medians and process that.
        String[] labels = this.getLabels();
        for (String label : labels) {
            var statsArray = this.statsMap.get(label);
            // Process each input column.
            for (int i = 0; i < statsArray.length; i++) {
                var stats = statsArray[i];
                double mean = stats.getMean();
                double std_dev = stats.getStandardDeviation();
                double q1 = stats.getPercentile(25.0);
                double median = stats.getPercentile(50.0);
                double q3 = stats.getPercentile(75.0);
                double trimean = (2 * median + q1 + q3) / 4;
                double iqr = (q3 - q1);
                this.reportWriter.println(label + "\t" + this.repIds[i] + "\t" + Double.toString(mean) +
                        "\t" + Double.toString(std_dev) + "\t" + Double.toString(trimean) + "\t" + Double.toString(iqr));
            }
        }
    }

}
