/**
 *
 */
package org.theseed.reports;

import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * This bin report lists the IDs of the good genomes.
 *
 * @author Bruce Parrello
 *
 */
public class GenomesBinReport extends BinReporter {

    // FIELDS
    /** sets of genomes in a two-element list:  0 = bad, 1 = good */
    private List<SortedSet<GenomeData>> typeGenomes;

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

    public GenomesBinReport(OutputStream output, boolean allFlag) {
        super(output, allFlag);
        this.typeGenomes = Arrays.asList(new TreeSet<GenomeData>(), new TreeSet<GenomeData>());
    }

    @Override
    public void openReport(String name) {
        this.println(GenomeData.header());
    }

    @Override
    public void binGenome(String sampleId, int type, String genomeId, double score, String name, String refId, int dnaSize) {
        this.typeGenomes.get(type).add(new GenomeData(score, sampleId, genomeId, name, refId, dnaSize));
    }

    @Override
    public void displaySample(String name, List<String> good, List<String> bad) {
    }

    @Override
    public void closeReport() {
        writeGenomes(this.typeGenomes.get(1));
        if (this.isAllFlag())
            writeGenomes(this.typeGenomes.get(0));
    }

    /**
     * Write all the genomes in the sorted set.
     *
     * @param myGenomes		sorted set to write
     */
    public void writeGenomes(SortedSet<GenomeData> myGenomes) {
        // Unspool the genomes in quality order.
        for (GenomeData data : myGenomes)
            this.println(data.toLine());
    }

}
