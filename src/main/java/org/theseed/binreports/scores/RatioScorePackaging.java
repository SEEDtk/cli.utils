/**
 *
 */
package org.theseed.binreports.scores;

import java.util.Arrays;

import org.theseed.binreports.BinReport;

/**
 * This method normalizes the scores to always sum to 1 while keeping the ratios between values intact.
 *
 * The method should only be used if all the scores are non-negative.
 *
 * @author Bruce Parrello
 *
 */
public class RatioScorePackaging extends ScorePackaging {

    public RatioScorePackaging(BinReport binReport) {
        super(binReport);
    }

    @Override
    protected void computeScores(double[] scores) {
        double denom = Arrays.stream(scores).sum();
        // We normally divide by the sum to normalize, but only if the sum is positive.
        if (denom > 0) {
            for (int i = 0; i < scores.length; i++)
                scores[i] /= denom;
        }
    }

}
