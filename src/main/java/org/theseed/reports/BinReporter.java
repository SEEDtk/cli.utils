/**
 *
 */
package org.theseed.reports;

import java.io.OutputStream;
import java.util.List;

/**
 * This is the base class for metagenome binning reports.
 *
 * @author Bruce Parrello
 *
 */
public abstract class BinReporter extends BaseReporter {

    /**
     * This enumeration defines the output types.
     */
    public static enum Type {
        /** report on the individual bins */
        BINS {
            @Override
            public BinReporter create(OutputStream output) {
                return new BinsBinReporter(output);
            }
        },
        /** list the good-genome IDs */
        GENOMES {
            @Override
            public BinReporter create(OutputStream output) {
                return new GenomesBinReport(output);
            }
        };

        public abstract BinReporter create(OutputStream output);

    }

    /**
     * Construct a bin reporter for the specified output stream.
     *
     * @param output	output stream for the report
     */
    public BinReporter(OutputStream output) {
        super(output);
    }

    /**
     * Start the report.
     *
     * @param name		name of the directory being analyzed
     */
    public abstract void openReport(String name);

    /**
     * Record a good genome.
     *
     * @param sampleId	ID of the controlling bin sample
     * @param goodId 	ID of the good genome
     * @param score		quality score of the genome
     * @param name		name of the genome
     * @param refId		ID of the reference genome
     * @param dnaSize 	number of base pairs in the genome
     */
    public abstract void goodGenome(String sampleId, String goodId, double score, String name, String refId, int dnaSize);

    /**
     * Display a single bin.
     *
     * @param name		bin sample name
     * @param bad		number of bad genomes
     * @param good		list of good genome IDs
     */
    public abstract void displaySample(String name, int bad, List<String> good);

    /**
     * Finish the report.
     */
    public abstract void closeReport();

}
