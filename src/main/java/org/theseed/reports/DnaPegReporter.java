/**
 *
 */
package org.theseed.reports;

import java.io.OutputStream;

import org.theseed.genome.Feature;
import org.theseed.genome.Genome;

/**
 * This report produces DNA sequence output for each peg and includes the function number.
 */
public class DnaPegReporter extends PegReporter {

    /**
     * Construct a DNA Peg reporter.
     *
     * @param processor		controlling command processor
     * @param outStream		target output stream
     */
    public DnaPegReporter(IParms processor, OutputStream outStream) {
        super(outStream);
    }

    @Override
    public void writeHeaders() {
        this.println("fun_id\tdna");

    }

    @Override
    public void writeFeature(int num, String funId, Feature feat) {
        // Get the DNA.
        Genome genome = feat.getParent();
        String dna = genome.getDna(feat.getLocation());
        this.print("%d\t%s", num, dna);
    }

    @Override
    public void finishReport() {
    }

}
