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

    // FIELDS
    /** TRUE if all bin IDs should be output, not just good ones */
    private boolean allFlag;

    /**
     * This enumeration defines the output types.
     */
    public static enum Type {
        /** report on the individual bins */
        BINS {
            @Override
            public BinReporter create(OutputStream output, boolean allFlag) {
                return new BinsBinReporter(output, allFlag);
            }
        },
        /** list the good-genome IDs */
        GENOMES {
            @Override
            public BinReporter create(OutputStream output, boolean allFlag) {
                return new GenomesBinReport(output, allFlag);
            }
        };

        public abstract BinReporter create(OutputStream output, boolean allFlag);

    }

    /**
     * Construct a bin reporter for the specified output stream.
     *
     * @param output	output stream for the report
     * @param allFlag	if TRUE, both good and bad bin details will be output
     */
    public BinReporter(OutputStream output, boolean allFlag) {
        super(output);
        this.allFlag = true;
    }

    /**
     * Start the report.
     *
     * @param name		name of the directory being analyzed
     */
    public abstract void openReport(String name);

    /**
     * Record a genome.
     *
     * @param sampleId	ID of the controlling bin sample
     * @param type		1 if good, 0 if bad
     * @param genomeId 	ID of the genome
     * @param score		quality score of the genome
     * @param name		name of the genome
     * @param refId		ID of the reference genome
     * @param dnaSize 	number of base pairs in the genome
     */
    public abstract void binGenome(String sampleId, int type, String genomeId, double score, String name, String refId, int dnaSize);

    /**
     * Display a single bin.
     *
     * @param name		bin sample name
     * @param good		list of good genome IDs
     * @param bad		list of bad genome IDs
     */
    public abstract void displaySample(String name, List<String> good, List<String> bad);

    /**
     * Finish the report.
     */
    public abstract void closeReport();

	/**
	 * @return the allFlag
	 */
	public boolean isAllFlag() {
		return allFlag;
	}

}
