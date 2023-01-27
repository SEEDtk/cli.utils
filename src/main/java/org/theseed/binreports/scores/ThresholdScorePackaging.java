/**
 *
 */
package org.theseed.binreports.scores;

import org.theseed.binreports.BinReport;

/**
 * This sets each score to 1.0 if it is over the threshold defined by the binreport, and 0.0 otherwise.
 *
 * @author Bruce Parrello
 *
 */
public class ThresholdScorePackaging extends ScorePackaging {

    // FIELDS
    /** threshold score to use */
    private double minScore;

    public ThresholdScorePackaging(BinReport binReport) {
        super(binReport);
        this.minScore = binReport.getMinScore();
    }

    @Override
    protected void computeScores(double[] scores) {
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] >= this.minScore)
                scores[i] = 1.0;
            else
                scores[i] = 0.0;
        }
    }

}
