/**
 *
 */
package org.theseed.binreports.scores;

import org.theseed.binreports.BinReport;

/**
 * Raw score packaging returns the incoming scores unmodified.
 *
 * @author Bruce Parrello
 *
 */
public class RawScorePackaging extends ScorePackaging {

    public RawScorePackaging(BinReport binReport) {
        super(binReport);
    }

    @Override
    protected void computeScores(double[] scores) {
    }

}
