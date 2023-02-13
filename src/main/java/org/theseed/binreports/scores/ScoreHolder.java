/**
 *
 */
package org.theseed.binreports.scores;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This is a utility object that allows us to sort scores and remember where they belong in a scoring array.
 *
 * @author Bruce Parrello
 *
 */
public class ScoreHolder implements Comparable<ScoreHolder> {

    /** index of score in the score array */
    private int idx;
    /** value of score */
    private double score;

    /**
     * Create a score holder for a specified position in the array.
     *
     * @param i			index into the scoring array
     * @param scores	scoring array
     */
    protected ScoreHolder(int i, double[] scores) {
        this.idx = i;
        this.score = scores[i];
    }

    @Override
    public int compareTo(ScoreHolder o) {
        int retVal = Double.compare(this.score, o.score);
        if (retVal == 0)
            retVal = this.idx - o.idx;
        return retVal;
    }

    /**
     * @return the index into the scoring array for this score
     */
    public int getIdx() {
        return this.idx;
    }

    /**
     * @return the raw value of this score
     */
    public double getScore() {
        return this.score;
    }

    /**
     * Convert an array of scores into a sorted list of score holders.
     *
     * @param scores	array of scores to sort
     *
     * @return a list of score holders, sorting the scores from lowest to highest
     */
    public static List<ScoreHolder> sortScores(double[] scores) {
        List<ScoreHolder> holders = IntStream.range(0, scores.length).mapToObj(i -> new ScoreHolder(i, scores))
                .sorted().collect(Collectors.toList());
        return holders;
    }


}
