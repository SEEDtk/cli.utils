/**
 *
 */
package org.theseed.binreports.reports;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.theseed.binreports.BinReport;
import org.theseed.binreports.BinReport.Sample;
import org.theseed.utils.ParseFailureException;

/**
 * This command produces two report files.  One lists every commonly-occurring represented set for each condition label.
 * The other lists the commonly-occurring sets unique to each condition label.
 *
 * @author Bruce Parrello
 *
 */
public class CommonsBinReportReporter extends BinReportReporter {

    // FIELDS
    /** map of representative group IDs to names */
    private Map<String, String> featureMap;
    /** total scores for current label */
    private double[] sums;
    /** number of samples for current label */
    private int sampleCount;
    /** label of current sample */
    private String currentLabel;
    /** array index of current label */
    private int labelIdx;
    /** minimum fraction required for commonality */
    private double commonFrac;
    /** array of labels, in presentation order */
    private String[] labels;
    /** detail report print writer */
    private PrintWriter detailWriter;
    /** counts report print writer */
    private PrintWriter countsWriter;
    /** map of groups to counts for each label */
    private Map<String, int[]> groupCountMap;
    /** number of samples for each label */
    private int[] sampleCounts;
    /** bin report containing all the sample data */
    private BinReport data;

    public CommonsBinReportReporter(IParms processor) throws IOException, ParseFailureException {
        super(processor);
        // Insure that the scoring method is binary (1/0).
        this.requireBinaryNormalization();
        // Get and validate the commonality fraction.
        this.commonFrac = processor.getCommonFrac();
        if (this.commonFrac <= 0 || this.commonFrac > 1.0)
            throw new ParseFailureException("Minimum fraction for commonality must be between 0 and 1.");
        // Save the feature name map.
        this.featureMap = processor.getFeatureMap();
    }

    @Override
    protected void startReport(BinReport binReport) throws IOException {
        // Save the bin report.
        this.data = binReport;
        // Create the output files.
        this.detailWriter = this.openOutFile("commonality.details.tbl");
        this.detailWriter.println("label\trepgen_id\trepgen_name\tcount");
        this.countsWriter = this.openOutFile("commonality.counts.tbl");
        this.labels = this.getLabels();
        this.countsWriter.println("repgen_id\trepgen_name\t" + StringUtils.join(labels, '\t') + "\tdominant");
        // Initialize the summary and sample counts arrays.
        this.sums = new double[binReport.width()];
        this.sampleCounts = new int[this.labels.length];
        // Initialize the group common-label map.
        this.groupCountMap = new TreeMap<String, int[]>();
        this.featureMap.keySet().stream().forEach(x -> this.groupCountMap.put(x, new int[binReport.getLabels().size()]));
        // Position on the first label.
        this.labelIdx = 0;
    }

    @Override
    protected void startLabel(String label) {
        // Save the current label.
        this.currentLabel = label;
        // Initialize the sums for this label.
        Arrays.fill(this.sums, 0.0);
        this.sampleCount = 0;
    }

    @Override
    protected void processSample(Sample sample, double[] scores) {
        sumInto(this.sums, scores);
        this.sampleCount++;
    }

    @Override
    protected void finishLabel() {
        // Compute the required count for a common label.
        double commonSum = this.sampleCount * this.commonFrac;
        // Output the features that are common, and update the label map.
        for (int i = 0; i < sums.length; i++) {
            String repgenId = this.data.getIdxFeature(i);
            int count = (int) this.sums[i];
            this.groupCountMap.get(repgenId)[this.labelIdx] = count;
            if (this.sums[i] >= commonSum)
                this.detailWriter.println(this.currentLabel + "\t" + repgenId + "\t" + this.featureMap.getOrDefault(repgenId, "<unknown>")
                        + "\t" + Integer.toString(count));
        }
        // Save the sample count and set up for the next label.
        this.sampleCounts[this.labelIdx] = this.sampleCount;
        this.labelIdx++;
    }

    @Override
    protected void finishReport() {
        // We will build the output lines in here.
        StringBuilder line = new StringBuilder(80);
        // Write out the counts report using the group-count map.
        for (var groupEntry : this.groupCountMap.entrySet()) {
            String groupId = groupEntry.getKey();
            int[] counts = groupEntry.getValue();
            // Only proceed if the counts are not all zero.
            if (Arrays.stream(counts).anyMatch(x -> x > 0)) {
                // Get the group name from the map.
                String groupName = this.featureMap.get(groupId);
                // Build the output line.
                line.setLength(0);
                line.append(groupId).append('\t').append(groupName);
                // We need to convert each count into a fraction and remember the dominant label.
                double maxRatio = 0.0;
                String maxLabel = "";
                for (int i = 0; i < counts.length; i++) {
                    double ratio = counts[i] / (double) this.sampleCounts[i];
                    line.append('\t').append(ratio);
                    // Do the dominance check.  Note that if two ratios are the same, we treat it as neither dominates.
                    if (ratio > maxRatio) {
                        maxRatio = ratio;
                        maxLabel = this.labels[i];
                    } else if (ratio == maxRatio)
                        maxLabel = "";
                }
                line.append("\t").append(maxLabel);
                this.countsWriter.println(line.toString());
            }
        }
    }

    /**
     * Add an array of scores into another array of scores.
     *
     * @param sums		target array of scores
     * @param scores	array of scores to add
     */
    private static void sumInto(double[] sums, double[] scores) {
        IntStream.range(0, sums.length).forEach(i -> sums[i] += scores[i]);
    }

}
