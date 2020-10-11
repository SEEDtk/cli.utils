/**
 *
 */
package org.theseed.reports;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.stream.Collectors;

import org.theseed.genome.Feature;

/**
 * This is a simple output class that produces the data in a simple tab-delimited file.
 *
 * @author Bruce Parrello
 *
 */
public class TextFpkmReporter extends FpkmReporter {

    // FIELDS
    /** buffer for building output lines */
    private StringBuffer buffer;
    /** number of actual samples */
    private int nSamples;
    /** print writer for output */
    private PrintWriter writer;

    /**
     * Construct this report.
     *
     * @param output		output stream
     * @param processor		controlling processor object
     */
    public TextFpkmReporter(OutputStream output, IParms processor) {
        this.writer = new PrintWriter(output);
    }

    @Override
    protected void openReport(List<String> jobNames) {
        // Here we write the headings.  Note the empty column after each heading to leave room for the exact-hit flag.
        this.writer.println("fid\tfunction\tneighbor\tneighbor_fun\t" + jobNames.stream().map(x -> x + "\t").collect(Collectors.joining("\t")));
        // Create the string buffer.
        this.nSamples = jobNames.size();
        this.buffer = new StringBuffer(100 + 15 * this.nSamples);
    }

    @Override
    protected void writeRow(Row row) {
        // Here we write the row.  We need to get some data items out of the feature.
        Feature feat = row.getFeat();
        String function = feat.getPegFunction();
        Feature neighbor = row.getNeighbor();
        String neighborId = "";
        String neighborFun = "";
        if (neighbor != null) {
            neighborId = neighbor.getId();
            neighborFun = neighbor.getPegFunction();
        }
        this.buffer.setLength(0);
        this.buffer.append(feat.getId() + "\t" + function + "\t" + neighborId + "\t" + neighborFun);
        for (int i = 0; i < this.nSamples; i++) {
            Weight weight = row.getWeight(i);
            if (weight == null)
                this.buffer.append("\t\t");
            else {
                char flag = (weight.isExactHit() ? ' ' : '*');
                this.buffer.append(String.format("\t%8.4f\t%c", weight.getWeight(), flag));
            }
        }
        this.writer.println(this.buffer.toString());
    }

    @Override
    protected void closeReport() {
        this.writer.close();
    }


}
