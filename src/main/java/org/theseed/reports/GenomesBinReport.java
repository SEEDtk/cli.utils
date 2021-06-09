/**
 *
 */
package org.theseed.reports;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.p3api.Connection;
import org.theseed.proteins.kmers.reps.P3RepGenomeDb;
import org.theseed.proteins.kmers.reps.RepGenomeDb;
import org.theseed.utils.ParseFailureException;

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
    /** sets of genomes in a two-element list:  0 = bad, 1 = good */
    private List<SortedSet<GenomeData>> typeGenomes;
    /** representative-genome database */
    private RepGenomeDb repgens;
    /** PATRIC connection */
    private Connection p3;

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
        private String repId;

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
            this.repId = null;
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
            return String.format("%s\t%s\t%s\t%d\t%8.4f\t%s\t%s", this.sampleId, this.genomeId, this.name,
                    this.dnaSize, this.score, this.refId, this.repId);
        }

        /**
         * @return the header line for the genome report
         */
        public static String header() {
            return "sample_id\tgenome_id\tname\tdna_size\tscore\tref_id\trepgen_id";
        }

        /**
         * @return the genome ID
         */
        public String getGenomeId() {
            return this.genomeId;
        }

        /**
         * Store the representative genome ID.
         *
         * @param repId		representative-genome ID to store
         */
        public void setRepId(String repId) {
            this.repId = repId;
        }
    }

    public GenomesBinReport(OutputStream output, IParms processor) throws ParseFailureException, IOException {
        super(output, processor);
        this.typeGenomes = Arrays.asList(new TreeSet<GenomeData>(), new TreeSet<GenomeData>());
        File repdbFile = processor.getRepGenDB();
        if (repdbFile == null)
            throw new ParseFailureException("Repgen database required for genome report.");
        this.repgens = RepGenomeDb.load(repdbFile);
        this.p3 = new Connection();
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
    private void writeGenomes(SortedSet<GenomeData> myGenomes) {
        this.computeRepIds(myGenomes);
        // Unspool the genomes in quality order.
        for (GenomeData data : myGenomes)
            this.println(data.toLine());
    }

    /**
     * Compute the representative-genome IDs for all of the genomes in the list
     *
     * @param myGenomes		set of genomes to process
     */
    private void computeRepIds(SortedSet<GenomeData> myGenomes) {
        // Get the representative for each genome.
        Collection<String> genomes = myGenomes.stream().map(x -> x.getGenomeId()).collect(Collectors.toList());
        Map<String, RepGenomeDb.Representation> repMap = P3RepGenomeDb.getReps(this.p3, genomes, this.repgens);
        // Loop through the genomes, storing rep IDs when we have them, and deleting the genome when we don't.
        Iterator<GenomeData> iter = myGenomes.iterator();
        while (iter.hasNext()) {
            GenomeData genome = iter.next();
            RepGenomeDb.Representation rep = repMap.get(genome.getGenomeId());
            if (rep == null) {
                iter.remove();
                log.info("No representation found for bin {}.", genome.getGenomeId());
            } else
                genome.setRepId(rep.getGenomeId());
        }
    }

}
