/**
 *
 */
package org.theseed.reports;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * This bin report lists the number of good and bad bins in each sample, plus the IDs of the good-bin genomes.
 *
 * @author Bruce Parrello
 *
 */
public class BinsBinReporter extends BinReporter {

    public BinsBinReporter(OutputStream output, IParms processor) {
        super(output, processor);
    }

    @Override
    public void openReport(String name) {
        this.println("sample\tgood\tbad\tgenome_ids");
    }

    @Override
    public void binGenome(String sampleId, int type, String genomeId, double score, String name, String refId, int dnaSize) {
    }

    @Override
    public void displaySample(String name, List<String> good, List<String> bad) {
        List<String> master = new ArrayList<String>(good.size() + bad.size());
        master.addAll(good);
        if (this.isAllFlag())
            master.addAll(bad);
        this.print("%s\t%d\t%d\t%s", name, good.size(), bad.size(), StringUtils.join(master, ", "));
    }

    @Override
    public void closeReport() {
    }

}
