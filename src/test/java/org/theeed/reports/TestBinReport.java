/**
 *
 */
package org.theeed.reports;

import org.junit.jupiter.api.Test;
import org.theseed.io.TabbedLineReader;
import org.theseed.reports.BinReport;
import org.theseed.utils.ParseFailureException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

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
        }
    }

}
