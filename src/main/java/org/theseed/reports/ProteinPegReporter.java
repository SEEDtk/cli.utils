/**
 *
 */
package org.theseed.reports;

import java.io.OutputStream;

import org.theseed.genome.Feature;

/**
 * This peg reporter writes a protein sequence.  It includes the function number.
 *
 * @author Bruce Parrello
 *
 */
public class ProteinPegReporter extends PegReporter {

    /**
     * Construct a protein peg reporter.
     *
     * @param processor		controlling command processor
     * @param outStream		target output stream
     */
    public ProteinPegReporter(IParms processor, OutputStream outStream) {
        super(outStream);
    }

    @Override
    public void writeHeaders() {
        this.println("fun_id\tprot");
    }

    @Override
    public void writeFeature(int num, String funId, Feature feat) {
        this.print("%d\t%s", num, feat.getProteinTranslation());
    }

    @Override
    public void finishReport() {
        // TODO code for finishReport

    }

}
