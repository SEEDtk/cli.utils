/**
 *
 */
package org.theseed.reports;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.theseed.utils.ParseFailureException;

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
     * This interface is used to get the parameters from the command processor.
     */
    public interface IParms {

        /**
         * @return TRUE if all bins are desired instead of just the good bins
         */
        public boolean isAllFlag();

        /**
         * @return the file containing the representative-genome database, or NULL if there is none
         */
        public File getRepGenDB();
    }

    /**
     * This enumeration defines the output types.
     */
    public static enum Type {
        /** report on the individual bins */
        BINS {
            @Override
            public BinReporter create(OutputStream output, IParms processor) {
                return new BinsBinReporter(output, processor);
            }
        },
        /** list the good-genome IDs */
        GENOMES {
            @Override
            public BinReporter create(OutputStream output, IParms processor) throws ParseFailureException, IOException {
                return new GenomesBinReport(output, processor);
            }
        },
        /** list the bins by repgen */
        REPGENS {
            @Override
            public BinReporter create(OutputStream output, IParms processor) throws ParseFailureException, IOException {
                return new RepGensBinReport(output, processor);
            }

        };

        public abstract BinReporter create(OutputStream output, IParms processor) throws ParseFailureException, IOException;

    }

    /**
     * Construct a bin reporter for the specified output stream.
     *
     * @param output		output stream for the report
     * @param processor		controlling command processor
     */
    public BinReporter(OutputStream output, IParms processor) {
        super(output);
        this.allFlag = processor.isAllFlag();
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
