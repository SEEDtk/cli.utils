/**
 *
 */
package org.theseed.reports;

import java.io.OutputStream;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This bin report lists the IDs of the good genomes.
 *
 * @author Bruce Parrello
 *
 */
public class GenomesBinReport extends BinReporter {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(GenomesBinReport.class);
    /** set of good genomes */
    private SortedSet<GenomeData> goodGenomes;

    /**
     * This dinky class tracks the data for a single genome.  It is sorted by
     * score.
     */
    private static class GenomeData implements Comparable<GenomeData> {

        private double score;
        private String genomeId;
        private String name;

        /**
         * Create a descriptor.
         *
         * @param score1		quality score
         * @param genomeId1		genome ID
         * @param name1			genome name
         */
        public GenomeData(double score1, String genomeId1, String name1) {
            this.score = score1;
            this.genomeId = genomeId1;
            this.name = name1;
        }

        @Override
        public int compareTo(GenomeData o) {
            // High score sorts first.
            int retVal = Double.compare(o.score, this.score);
            // Genome ID within score.
            if (retVal == 0)
                retVal = this.genomeId.compareTo(o.genomeId);
            return retVal;
        }

        /**
         * @return the output string for this genome
         */
        public String toLine() {
            return String.format("%s\t%s\t%8.4f", this.genomeId, this.name, this.score);
        }

        /**
         * @return the header line for the genome report
         */
        public static String header() {
            return "genome_id\tname\tscore";
        }
    }

    public GenomesBinReport(OutputStream output) {
        super(output);
        this.goodGenomes = new TreeSet<GenomeData>();
    }

    @Override
    public void openReport(String name) {
        this.println(GenomeData.header());
    }

    @Override
    public void goodGenome(String genomeId, double score, String name) {
        this.goodGenomes.add(new GenomeData(score, genomeId, name));
    }

    @Override
    public void displaySample(String name, int bad, List<String> good) {
    }

    @Override
    public void closeReport() {
        log.info("{} good genome found.", this.goodGenomes.size());
        // Unspool the genomes in quality order.
        for (GenomeData data : this.goodGenomes) {
            this.println(data.toLine());
        }
    }

}
