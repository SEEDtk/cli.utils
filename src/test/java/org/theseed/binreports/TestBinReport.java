/**
 *
 */
package org.theseed.binreports;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.util.ResizableDoubleArray;
import org.junit.jupiter.api.Test;
import org.theseed.binreports.scores.ScorePackaging;
import org.theseed.io.TabbedLineReader;
import org.theseed.utils.ParseFailureException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;

/**
 * @author Bruce Parrello
 *
 */
class TestBinReport {

    @Test
    void testBinReportBuilder() throws IOException, ParseFailureException {
        File binFile = new File("data", "binReport.bin.tbl");
        File bin2File = new File("data", "binReport.bin2.tbl");
        Set<String> feats = new TreeSet<String>(TabbedLineReader.readSet(binFile, "repgen_id"));
        feats.addAll(TabbedLineReader.readSet(bin2File, "repgen_id"));
        int width = feats.size();
        BinReport report = new BinReport(feats);
        assertThat(report.width(), equalTo(width));
        report.processFile("control", binFile);
        assertThat(report.size(), equalTo(2));
        report.processFile("parkinsons", bin2File);
        assertThat(report.size(), equalTo(3));
        for (BinReport.Sample sample : report) {
            int idx;
            String sampleId = sample.getSampleId();
            double[] scores = sample.getScores();
            assertThat(sampleId, scores.length, equalTo(width));
            switch (sampleId) {
            case "ERR1136887" :
                assertThat(sample.getLabel(), equalTo("control"));
                idx = report.getFeatureIdx("1776382.82");
                assertThat(scores[idx], equalTo(0.0));
                idx = report.getFeatureIdx("2838613.4");
                assertThat(scores[idx], closeTo(343802.795, 0.001));
                idx = report.getFeatureIdx("1969167.3");
                assertThat(scores[idx], closeTo(384961.807, 0.001));
                idx = report.getFeatureIdx("2831966.3");
                assertThat(scores[idx], closeTo(226754.426, 0.001));
                break;
            case "GPSample" :
                assertThat(sample.getLabel(), equalTo("control"));
                idx = report.getFeatureIdx("1776382.82");
                assertThat(scores[idx], closeTo(134604.244, 0.001));
                idx = report.getFeatureIdx("2838613.4");
                assertThat(scores[idx], closeTo(1461684.573, 0.001));
                idx = report.getFeatureIdx("1969167.3");
                assertThat(scores[idx], closeTo(2311314.032, 0.001));
                idx = report.getFeatureIdx("2831966.3");
                assertThat(scores[idx], equalTo(0.0));
                break;
            case "ROSample" :
                assertThat(sample.getLabel(), equalTo("parkinsons"));
                idx = report.getFeatureIdx("1776382.82");
                assertThat(scores[idx], closeTo(168751.238, 0.001));
                idx = report.getFeatureIdx("2838613.4");
                assertThat(scores[idx], closeTo(694106.538, 0.001));
                idx = report.getFeatureIdx("1969167.3");
                assertThat(scores[idx], equalTo(0.0));
                idx = report.getFeatureIdx("97138.3");
                assertThat(scores[idx], closeTo(35454.394, 0.001));
                break;
            default :
                assertThat(sampleId, false);
            }
            assertThat(report.getLabels(), containsInAnyOrder("control", "parkinsons"));
            var controls = report.getSamplesForLabel("control");
            assertThat(controls.size(), equalTo(2));
            var parks = report.getSamplesForLabel("parkinsons");
            sample = report.getSample("ROSample");
            assertThat(parks, containsInAnyOrder(sample));
        }
    }

    @Test
    void testScorePackaging() throws IOException, ParseFailureException {
        File binFile = new File("data", "binReport.bin.tbl");
        File bin2File = new File("data", "binReport.bin2.tbl");
        Set<String> feats = new TreeSet<String>(TabbedLineReader.readSet(binFile, "repgen_id"));
        feats.addAll(TabbedLineReader.readSet(bin2File, "repgen_id"));
        BinReport report = new BinReport(feats);
        report.processFile("control", binFile);
        report.processFile("parkinsons", bin2File);
        for (var sample : report)
            testSample(report, sample);
    }

    void testSample(BinReport report, BinReport.Sample sample) {
        String sampleId = sample.getSampleId();
        double[] raw = sample.getScores();
        ScorePackaging normalizer = ScorePackaging.Type.RAW.create(report);
        double[] normal = normalizer.getScores(sample);
        assertThat(sampleId, raw, equalTo(normal));
        ScorePackaging.Type type = ScorePackaging.Type.RATIO;
        testNormalizer(report, sample, raw, type);
        type = ScorePackaging.Type.VECTOR;
        testNormalizer(report, sample, raw, type);
        normalizer = ScorePackaging.Type.THRESHOLD.create(report);
        normal = normalizer.getScores(sample);
        assertThat(sampleId, raw.length, equalTo(normal.length));
        for (int i = 0; i < raw.length; i++) {
            String message = String.format("Checking %d for sample %s.", i, sampleId);
            if (raw[i] >= report.getMinScore())
                assertThat(message, normal[i], equalTo(1.0));
            else
                assertThat(message, normal[i], equalTo(0.0));
        }
        report.setDivisions(10);
        normalizer = ScorePackaging.Type.RANK.create(report);
        normal = normalizer.getScores(sample);
        assertThat(sampleId, raw.length, equalTo(normal.length));
        // We are going to see rank values from 0 to 1.0.  Create a list to hold each set of ranked values.
        ResizableDoubleArray[] valueLists = IntStream.range(0, 10).mapToObj(i -> new ResizableDoubleArray()).toArray(ResizableDoubleArray[]::new);
        // Loop through the two arrays, storing each value in the array for its rank.
        for (int i = 0; i < raw.length; i++) {
            int idx = (int) (normal[i] * 9);
            valueLists[idx].addElement(raw[i]);
        }
        // The lengths should be roughly the same, and each value in a list should be less than each value in the next list.
        double oldMax = Arrays.stream(valueLists[0].getElements()).max().orElse(0.0);
        for (int i = 1; i < 10; i++) {
            String mess = String.format("Comparing to rank %d for %s.", i, sampleId);
            OptionalDouble newMin = Arrays.stream(valueLists[i].getElements()).min();
            if (newMin.isPresent()) {
                assertThat(mess, newMin.getAsDouble(), greaterThan(oldMax));
                oldMax = Arrays.stream(valueLists[i].getElements()).max().getAsDouble();
            }
        }
        // Finally, test the MEDIAN packager.
        normalizer = ScorePackaging.Type.MEDIAN.create(report);
        normal = normalizer.getScores(sample);
        assertThat(sampleId, raw.length, equalTo(normal.length));
        DescriptiveStatistics stats = new DescriptiveStatistics(raw);
        double median = stats.getPercentile(50.0);
        for (int i = 0; i < raw.length; i++) {
            String message = String.format("Comparing to %d for type %s and sample %s.", i, type, sampleId);
            if (normal[i] == 1.0)
                assertThat(message, raw[i], greaterThan(median));
            else {
                assertThat(message, normal[i], equalTo(0.0));
                assertThat(message, raw[i], lessThanOrEqualTo(median));
            }
        }
    }

    void testNormalizer(BinReport report, BinReport.Sample sample, double[] raw, ScorePackaging.Type type) {
        String sampleId = sample.getSampleId();
        var normalizer = type.create(report);
        double[] normal = normalizer.getScores(sample);
        assertThat(sampleId, raw.length, equalTo(normal.length));
        for (int i = 1; i < raw.length; i++) {
            int cRaw = Double.compare(raw[i - 1], raw[i]);
            int cNorm = Double.compare(normal[i - 1], normal[i]);
            String message = String.format("Comparing to %d for type %s and sample %s.", i, type, sampleId);
            if (cRaw < 0)
                assertThat(message, cNorm, lessThan(0));
            else if (cRaw > 0)
                assertThat(message, cNorm, greaterThan(0));
            else
                assertThat(message, cNorm, equalTo(0));
        }
    }
}
