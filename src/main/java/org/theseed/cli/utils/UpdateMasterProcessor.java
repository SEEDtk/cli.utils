/**
 *
 */
package org.theseed.cli.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.Genome;
import org.theseed.genome.GenomeMultiDirectory;
import org.theseed.p3api.Connection;
import org.theseed.p3api.P3Genome;
import org.theseed.utils.BaseProcessor;
import org.theseed.utils.ParseFailureException;

import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * This command gets the complete list of PATRIC prokaryotes and compares it to the contents of a master directory.
 * All genomes not in PATRIC will be deleted from the master directory, and all genomes in PATRIC but not the
 * master directory will be downloaded.  The positional parameter is the name of the PATRIC master directory to
 * update.
 *
 * The command-line options are as follows:
 *
 * -h	display command-line usage
 * -v	display more frequent log messages
 * -l	level of detail to download (default STRUCTURE_ONLY)
 *
 * @author Bruce Parrello
 *
 */
public class UpdateMasterProcessor extends BaseProcessor {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(UpdateMasterProcessor.class);
    /** map of PATRIC genome IDs to names */
    private Map<String, String> pGenomes;
    /** PATRIC master directory */
    private GenomeMultiDirectory pMaster;
    /** scale faster for list size */
    private static final double LOAD_FACTOR = 1.3;
    /** PATRIC connection */
    private Connection p3;

    // COMMAND-LINE OPTIONS

    /** detail level for downloads */
    @Option(name = "--level", aliases = { "-l" }, metaVar = "detail level for new-genome download")
    private P3Genome.Details level;

    /** name of the PATRIC master directory */
    @Argument(index = 0, metaVar = "p3MasterDir", usage = "name of the PATRIC master genome directory")
    private File p3MasterDir;

    @Override
    protected void setDefaults() {
        this.level = P3Genome.Details.STRUCTURE_ONLY;
    }

    @Override
    protected boolean validateParms() throws IOException, ParseFailureException {
        // Verify the master directory.
        if (! this.p3MasterDir.isDirectory())
            throw new FileNotFoundException("Input directory " + this.p3MasterDir + " is not found or invalid.");
        log.info("Loading master directory {}.", this.p3MasterDir);
        this.pMaster = new GenomeMultiDirectory(this.p3MasterDir);
        // Get the PATRIC genome list.  We convert it to an ID -> name map, keeping the first if there is a conflict.
        this.p3 = new Connection();
        int predicted = (int) (LOAD_FACTOR * this.pMaster.size());
        List<JsonObject> objects = new ArrayList<JsonObject>(predicted);
        p3.addAllProkaryotes(objects);
        this.pGenomes = objects.stream().collect(Collectors.toMap(x -> Connection.getString(x, "genome_id"),
                x -> Connection.getString(x, "genome_name"), (x1,x2) -> x1));
        log.info("{} genomes found in PATRIC.", this.pGenomes.size());
        return true;
    }

    @Override
    protected void runCommand() throws Exception {
        // First, we delete the obsolete genomes.
        int deleted = this.deleteObsolete();
        // Now the hard part, downloading the new stuff.  Compute the number of genomes to download.
        int total = this.pGenomes.size() - this.pMaster.size();
        int downloaded = 0;
        int missing = 0;
        int processed = 0;
        long start = System.currentTimeMillis();
        // Loop through everything in PATRIC.
        for (Map.Entry<String, String> genomeEntry : this.pGenomes.entrySet()) {
            String genomeId = genomeEntry.getKey();
            if (! this.pMaster.contains(genomeId)) {
                String name = genomeEntry.getValue();
                // Here we must download the genome.
                Genome newGenome = P3Genome.load(p3, genomeId, this.level);
                if (newGenome == null) {
                    log.warn("Genome {} ({}) not found in PATRIC.", newGenome, name);
                    missing++;
                } else {
                    log.info("Genome {} downloaded.", newGenome);
                    this.pMaster.add(newGenome);
                    downloaded++;
                }
                processed++;
                if (log.isInfoEnabled()) {
                    int remaining = total - processed;
                    double rate = (System.currentTimeMillis() - start) / (1000.0 * processed);
                    double timeLeft = rate * remaining / 3600.0;
                    log.info("{} genomes remaining, {} seconds/genome, {} hours to completion.",
                            remaining, rate, timeLeft);
                }
            }
        }
        log.info("All done.  {} deleted, {} added, {} missing.", deleted, downloaded, missing);
    }

    /**
     * Delete the obsolete genomes.
     *
     * @return the number of genomes deleted
     *
     * @throws IOException
     */
    private int deleteObsolete() throws IOException {
        // Get the list of genomes to delete.
        Set<String> badGenomes = this.pMaster.getIDs().stream().filter(x -> ! this.pGenomes.containsKey(x)).collect(Collectors.toSet());
        log.info("{} genomes will be deleted.", badGenomes.size());
        for (String badGenome : badGenomes) {
            log.debug("Removing genome {}.", badGenome);
            this.pMaster.remove(badGenome);
        }
        return badGenomes.size();
    }

}
