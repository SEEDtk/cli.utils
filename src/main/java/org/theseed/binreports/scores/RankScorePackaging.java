/**
 *
 */
package org.theseed.binreports.scores;

import java.util.List;
import org.theseed.binreports.BinReport;

/**
 * This is the most complex of all the score normalization methods.  We must assign a value based on the score's rank.
 * We don't use absolute ranks; rather, we divide the set of feature scores into N equal-sized divisions.  The top division
 * has a score of 1.0, the second a score of (N - 1) / N, the third a score of (N - 2) / N, and so forth.
 *
 * @author Bruce Parrello
 *
 */
public class RankScorePackaging extends ScorePackaging {

    // FIELDS
    /** number of divisions */
    private int nDivisions;


    public RankScorePackaging(BinReport binReport) {
        super(binReport);
        this.nDivisions = binReport.getDivisions();
    }

    @Override
    protected void computeScores(double[] scores) {
        // Copy the array to a sorted list of score holders.  The holders remember the original array index.
        List<ScoreHolder> holders = ScoreHolder.sortScores(scores);
        // Save the length and last index of the holder list.
        final int hSize = holders.size();
        final int lastHIdx = hSize - 1;
        // Now the first Nth of the list has the scores for value 0, the second has the scores for value 1 / N,
        // and so forth.  This part is tricky, because we are not going to get even-sized splits.  We track the
        // next index to process and compute the stop point for each division.
        int nextIdx = 0;
        for (int i = 0; i < this.nDivisions; i++) {
            // This is the score to store.
            double score = (double) i / (this.nDivisions - 1);
            // Compute the stopping point.  We make a safety check to insure we don't round ourselves out of range.
            int lastIdx = Math.round((float) ((i + 1) * holders.size()) / this.nDivisions);
            if (lastIdx > hSize) lastIdx = hSize;
            // Only proceed if there is anything here.
            if (lastIdx > 0) {
                // Extend the stopping point past the last identical values.  Identical values always rank the same.
                while (lastIdx < lastHIdx && holders.get(lastIdx).getScore() == holders.get(lastIdx-1).getScore())
                    lastIdx++;
                // Now we store the current score for every score from "nextIdx" up to but not including "lastIdx".
                while (nextIdx < lastIdx) {
                    ScoreHolder holder = holders.get(nextIdx);
                    scores[holder.getIdx()] = score;
                    nextIdx++;
                }
            }
        }
    }

    /**
     * Specify a new number of divisions to use.
     *
     * @param newVal	the number of divisions to use
     */
    public void setDivisions(int newVal) {
        this.nDivisions = newVal;
    }

}
