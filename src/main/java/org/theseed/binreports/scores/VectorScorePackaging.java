/**
 *
 */
package org.theseed.binreports.scores;

import java.util.Arrays;

import org.theseed.binreports.BinReport;

/**
 * This treats the scores as a vector and normalizes them to a unit-length vector.
 *
 * @author Bruce Parrello
 *
 */
public class VectorScorePackaging extends ScorePackaging {

    public VectorScorePackaging(BinReport binReport) {
        super(binReport);
    }

    @Override
    protected void computeScores(double[] scores) {
        // Compute the vector length.
        double denom = Math.sqrt(Arrays.stream(scores).map(x -> x*x).sum());
        // Scale the vector if it is not zero length.
        if (denom > 0) {
            for (int i = 0; i < scores.length; i++)
                scores[i] /= denom;
        }
    }

}
