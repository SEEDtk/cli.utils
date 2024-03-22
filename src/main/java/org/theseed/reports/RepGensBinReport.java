/**
 *
 */
package org.theseed.reports;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;

import org.theseed.basic.ParseFailureException;
import org.theseed.proteins.kmers.reps.RepGenomeDb;
import org.theseed.stats.WeightMap;

/**
 * This is a version of the genome bin report designed for the creation of xmatrix files.  It produces a standard-format
 * bin report with fewer details.  It differs from its base class only in how it processes the data at the very end.
 *
 * @author Bruce Parrello
 *
 */
public class RepGensBinReport extends GenomesBinReport {

    // FIELDS
    /** default size for maps */
    private static final int DEFAULT_MAP_SIZE = 200;
    /** scale factor for converting DNA size to score */
    private static final double DNA_SIZE_SCALE_FACTOR = 100000.0;

    public RepGensBinReport(OutputStream output, IParms processor) throws ParseFailureException, IOException {
        super(output, processor);
    }

    @Override
    public void openReport(String name) {
        this.println("sample_id\trepgen_id\trep_name\tscore");
    }

    @Override
    public void closeReport() {
        // Get all the genome bins we care about.
        Collection<GenomeData> genomes = this.getGoodGenomes();
        if (this.isAllFlag())
            genomes.addAll(this.getBadGenomes());
        // Assign representatives to all these genomes.
        this.computeRepIds(genomes);
        // Now we loop through the genomes, summarizing them by representative.  To do this, we map each sample
        // ID to a weight map for the rep IDs in the sample.  The genomes are weighted by dna size / 100K.
        var summaryMap = new HashMap<String, WeightMap>(DEFAULT_MAP_SIZE);
        for (GenomeData genome : genomes) {
            String sampleId = genome.getSampleId();
            WeightMap sampleMap = summaryMap.computeIfAbsent(sampleId, x -> new WeightMap(DEFAULT_MAP_SIZE));
            sampleMap.count(genome.getRepId(), genome.getDnaSize() / DNA_SIZE_SCALE_FACTOR);
        }
        // We have everything we need to output the data for the samples.  Get the repgen database so we can
        // convert representative genome IDs to names.
        RepGenomeDb repDb = this.getRepgenDb();
        for (var summaryEntry : summaryMap.entrySet()) {
            String sampleId = summaryEntry.getKey();
            for (var repEntry : summaryEntry.getValue().counts()) {
                String repId = repEntry.getKey();
                String repName = repDb.get(repId).getName();
                this.println(sampleId + "\t" + repId + "\t" + repName + "\t" + Double.toString(repEntry.getCount()));
            }
        }

    }

}
