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
        private String sampleId;
        private String refId;
        private int dnaSize;

        /**
         * Create a descriptor.
         *
         * @param score1		quality score
         * @param sampleId1		ID of the containing sample
         * @param genomeId1		genome ID
         * @param name1			genome name
         * @param refId1		reference genome ID
         * @param dnaSize1		genome size in base pairs
         */
        public GenomeData(double score1, String sampleId1, String genomeId1, String name1, String refId1, int dnaSize1) {
            this.score = score1;
            this.genomeId = genomeId1;
            this.name = name1;
            this.sampleId = sampleId1;
            this.refId = refId1;
            this.dnaSize = dnaSize1;
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
            return String.format("%s\t%s\t%s\t%d\t%8.4f\t%s", this.sampleId, this.genomeId, this.name,
                    this.dnaSize, this.score, this.refId);
        }

        /**
         * @return the header line for the genome report
         */
        public static String header() {
            return "sample_id\tgenome_id\tname\tdna_size\tscore\tref_id";
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
    public void goodGenome(String sampleId, String genomeId, double score, String name, String refId, int dnaSize) {
        this.goodGenomes.add(new GenomeData(score, sampleId, genomeId, name, refId, dnaSize));
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
