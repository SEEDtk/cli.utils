/**
 *
 */
package org.theseed.binreports.scores;

import java.util.Arrays;

import org.theseed.binreports.BinReport;

/**
 * This scoring normalization converts a score to 0 if it is below the mean and 1 if is is at or above the mean.
 *
 * @author Bruce Parrello
 *
 */
public class HighLowScorePackaging extends ScorePackaging {

    public HighLowScorePackaging(BinReport binReport) {
        super(binReport);
    }

    @Override
    protected void computeScores(double[] scores) {
         double mean = Arrays.stream(scores).sum() / scores.length;
         for (int i = 0; i < scores.length; i++) {
             if (scores[i] >= mean)
                 scores[i] = 1.0;
             else
                 scores[i] = 0.0;
         }
    }

}
