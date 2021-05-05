/**
 *
 */
package org.theseed.reports;

import java.io.OutputStream;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * This bin report lists the number of good and bad bins in each sample, plus the IDs of the good-bin genomes.
 *
 * @author Bruce Parrello
 *
 */
public class BinsBinReporter extends BinReporter {

    public BinsBinReporter(OutputStream output) {
        super(output);
    }

    @Override
    public void openReport(String name) {
        this.println("sample\tgood\tbad\tgood_ids");
    }

    @Override
    public void goodGenome(String sampleId, String genomeId, double score, String name, String refId, int dnaSize) {
    }

    @Override
    public void displaySample(String name, int bad, List<String> good) {
        this.print("%s\t%d\t%d\t%s", name, good.size(), bad, StringUtils.join(good, ", "));
    }

    @Override
    public void closeReport() {
    }

}
