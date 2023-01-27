/**
 *
 */
package org.theseed.binreports.scores;

import java.util.Arrays;

import org.theseed.binreports.BinReport;

/**
 * This is the base class for objects that translate bin report scores to classification input values.  Each
 * subclass uses a different method to normalize the results.
 *
 * @author Bruce Parrello
 *
 */
public abstract class ScorePackaging {

    // FIELDS
    /** relevant bin report object */
    private BinReport data;

    /**
     * This enumeration specifies the different types of score normalization.
     */
    public static enum Type {
        /** return the scores unmodified */
        RAW {
            @Override
            public ScorePackaging create(BinReport binReport) {
                return new RawScorePackaging(binReport);
            }

            @Override
            public boolean isBinary() {
                return false;
            }
        },
        /** convert the scores to fractions of 1 */
        RATIO {
            @Override
            public ScorePackaging create(BinReport binReport) {
                return new RatioScorePackaging(binReport);
            }

            @Override
            public boolean isBinary() {
                return false;
            }
        },
        /** convert the scores to a unit vector */
        VECTOR {
            @Override
            public ScorePackaging create(BinReport binReport) {
                return new VectorScorePackaging(binReport);
            }

            @Override
            public boolean isBinary() {
                return false;
            }
        },
        /** convert the scores to binary presence/absence based on a threshold */
        THRESHOLD {
            @Override
            public ScorePackaging create(BinReport binReport) {
                return new ThresholdScorePackaging(binReport);
            }

            @Override
            public boolean isBinary() {
                return true;
            }
        },
        /** convert the scores to a normalized ranking */
        RANK {
            @Override
            public ScorePackaging create(BinReport binReport) {
                return new RankScorePackaging(binReport);
            }

            @Override
            public boolean isBinary() {
                return false;
            }
        },
        /** convert the scores to binary presence/absence based on relationship to the mean */
        HIGH_LOW {
            @Override
            public ScorePackaging create(BinReport binReport) {
                return new HighLowScorePackaging(binReport);
            }

            @Override
            public boolean isBinary() {
                return true;
            }
        };

        /**
         * Create a score normalization object of the specified type.
         *
         * @param binReport		bin report whose scores are to be normalized
         */
        public abstract ScorePackaging create(BinReport binReport);

        /**
         * @return TRUE if the normalization method is binary (normalizes to 1 or 0), FALSE if it is analog
         */
        public abstract boolean isBinary();

    }

    /**
     * Construct a score package object for a specified bin report.
     *
     * @param binReport		binReport object containing the samples and scores
     */
    public ScorePackaging(BinReport binReport) {
        this.data = binReport;
    }

    /**
     * @return a normalized scoring array for the specified sample
     *
     * @param sample	sample containing the scores to normalize
     */
    public double[] getScores(BinReport.Sample sample) {
        double[] retVal = Arrays.copyOf(sample.getScores(), this.data.width());
        this.computeScores(retVal);
        return retVal;
    }

    /**
     * Normalize the scores from a sample.
     *
     * @param scores		modifiable array containing raw scores to normalize in place
     */
    protected abstract void computeScores(double[] scores);

    /**
     * @return the bin report for this score packager
     */
    protected BinReport getBinReport() {
        return this.data;
    }

}
