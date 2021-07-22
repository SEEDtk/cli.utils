/**
 *
 */
package org.theseed.p3api;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.theseed.genome.Feature;
import org.theseed.sequence.FastaOutputStream;
import org.theseed.sequence.Sequence;

/**
 * This class represents the data we are keeping on a role in the GtoRolesProcessor.
 *
 * @author Bruce Parrello
 *
 */
public class GtoRoleData implements AutoCloseable {

    // FIELDS
    /** current FASTA output file */
    private FastaOutputStream outStream;
    /** PDB ID, or NULL if no PDB ID has been found yet */
    private String pdbId;
    /** PDB finder to use */
    private PdbFinder pdbFinder;

    /**
     * Create a new, blank role-data object.
     *
     * @param outDir	name of the output directory
     * @param roleId	ID of the role
     * @param finder	PDB finder to use
     *
     * @throws FileNotFoundException
     */
    public GtoRoleData(File outDir, String roleId, PdbFinder finder) throws FileNotFoundException {
        File outFile = new File(outDir, roleId + ".faa");
        this.outStream = new FastaOutputStream(outFile);
        this.pdbId = null;
        this.pdbFinder = finder;
    }

    /**
     * Process a protein feature.
     *
     * @param feat		genome feature containing a protein of this type
     *
     * @throws IOException
     * @throws InterruptedException
     */
    public void process(Feature feat) throws IOException, InterruptedException {
        Sequence seq = new Sequence(feat.getId(), feat.getPegFunction(), feat.getProteinTranslation());
        this.outStream.write(seq);
        if (this.pdbId == null) {
            // We don't have a PDB ID yet, so try to find one.
            this.pdbId = this.pdbFinder.findPDB(seq.getSequence());
        }
    }

    @Override
    public void close() {
        // Insure the output stream is closed.
        this.outStream.close();
    }

    /**
     * @return the PDB ID found (if any)
     */
    public String getPdbId() {
        String retVal = "";
        if (this.pdbId != null)
            retVal = this.pdbId;
        return retVal;
    }


}
