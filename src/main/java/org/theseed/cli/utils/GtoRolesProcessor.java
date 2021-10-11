/**
 *
 */
package org.theseed.cli.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.Feature;
import org.theseed.genome.Genome;
import org.theseed.genome.iterator.GenomeSource;
import org.theseed.io.TabbedLineReader;
import org.theseed.p3api.GtoRoleData;
import org.theseed.p3api.PdbFinder;
import org.theseed.proteins.Role;
import org.theseed.proteins.RoleMap;
import org.theseed.utils.BaseProcessor;
import org.theseed.utils.ParseFailureException;

/**
 * This command will produce directories of the proteins for the roles specified in the input file.  For each role, it will
 * build a FASTA file containing each instance of the role in the source genomes.
 *
 * The positional parameter is the name of the role definition file, the name of the directory containing the source genomes, and
 * the name of the output directory.
 *
 * The standard input should contain the desired role IDs in the first column.
 *
 * The command-line options are as follows:
 *
 * -h	display command-line usage
 * -v	display more frequent log messages
 * -i	input file name (if not the standard input)
 *
 * --source 	type of genome source
 * --clear		erase the output directory before processing
 * --maxE		maximum permissible E-value for PDB match
 * --minIdent	minimum permissible identity fraction for PDB match
 * --batch		number of roles to process in each batch
 *
 * @author Bruce Parrello
 *
 */
public class GtoRolesProcessor extends BaseProcessor {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(GtoRolesProcessor.class);
    /** role definitions */
    private RoleMap roleDefinitions;
    /** input genome source */
    private GenomeSource genomes;
    /** list of roles to process */
    private Set<String> targetRoles;
    /** global PDB ID finder */
    private PdbFinder finder;
    /** current batch of roles to process */
    private Map<String, GtoRoleData> roleBatch;
    /** writer for PDB output file */
    private PrintWriter pdbWriter;

    // COMMAND-LINE OPTIONS

    /** input file name (if not STDIN) */
    @Option(name = "--input", aliases = { "-i" }, metaVar = "roles.tbl", usage = "input file containing role IDs (if not STDIN)")
    private File inFile;

    /** type of genome source */
    @Option(name = "--source", usage = "genome source type")
    private GenomeSource.Type sourceType;

    /** TRUE to clear the output directory before processing */
    @Option(name = "--clear", usage = "if specified, the output directory will be cleared before processing")
    private boolean clearFlag;

    /** maximum E-value for a PDB hit */
    @Option(name = "--maxE", metaVar = "1e-5", usage = "maximum permissible E-value for a PDB hit")
    private double maxEValue;

    /** minimum permissible identity fraction for a PDB hit */
    @Option(name = "--minIdent", metaVar = "0.6", usage = "minimum identity fraction for a PDB hit")
    private double minIdent;

    /** number of roles to process in each batch */
    @Option(name = "--batch", metaVar = "10", usage = "number of roles to process in each batch")
    private int batchSize;

    /** name of the role definition file */
    @Argument(index = 0, metaVar = "roles.in.subsytems", usage = "name of the role definition file")
    private File roleFile;

    /** name of the input genome source (file or directory) */
    @Argument(index = 1, metaVar = "genomeDir", usage = "input genome source (file or directory)")
    private File genomeDir;

    /** name of the output directory */
    @Argument(index = 2, metaVar = "outDir", usage = "output directory for FASTA files")
    private File outDir;

    @Override
    protected void setDefaults() {
        this.inFile = null;
        this.sourceType = GenomeSource.Type.DIR;
        this.clearFlag = false;
        this.maxEValue = 1e-20;
        this.minIdent = 0.40;
        this.batchSize = 20;
    }

    @Override
    protected boolean validateParms() throws IOException, ParseFailureException {
        // Validate the numeric parameters.
        if (this.batchSize < 1)
            throw new ParseFailureException("Invalid batch size.  Minimum is 1.");
        if (this.maxEValue < 0.0)
            throw new ParseFailureException("Maximum E-value must be non-negative.");
        if (this.minIdent > 1.0)
            throw new ParseFailureException("Minimum identity fraction cannot be more than 1.");
        // Get the list of roles from the input.
        if (this.inFile == null) {
            this.targetRoles = TabbedLineReader.readSet(System.in, "1");
            log.info("{} target roles read from standard input.", this.targetRoles.size());
        } else if (! this.inFile.canRead())
            throw new FileNotFoundException("Target role input file " + this.inFile + " not found or unreadable.");
        else {
            this.targetRoles = TabbedLineReader.readSet(this.inFile, "1");
            log.info("{} target roles read from {}.", this.targetRoles.size(), this.inFile);
        }
        // Get the role definitions.
        if (! this.roleFile.canRead())
            throw new FileNotFoundException("Role definition file " + this.roleFile + " is not found or unreadable.");
        else {
            this.roleDefinitions = RoleMap.load(this.roleFile);
            log.info("{} role definitions found in {}.", this.roleDefinitions.size(), this.roleFile);
            // Verify the target roles against the role definitions.
            for (String roleId : this.targetRoles) {
                if (! this.roleDefinitions.containsKey(roleId))
                    log.warn("WARNING: Target role {} not found in role definitions.", roleId);
            }
        }
        // Get the genome source.
        if (! this.genomeDir.exists())
            throw new FileNotFoundException("Input genome source " + this.genomeDir + " not found.");
        else {
            this.genomes = this.sourceType.create(this.genomeDir);
            log.info("{} genomes in {} source {}.", this.genomes.size(), this.sourceType, this.genomeDir);
        }
        // Finally, set up the output directory.
        if (! this.outDir.isDirectory()) {
            log.info("Creating output directory {}.", this.outDir);
            FileUtils.forceMkdir(this.outDir);
        } else if (this.clearFlag) {
            log.info("Erasing output directory {}.", this.outDir);
            FileUtils.cleanDirectory(this.outDir);
        } else
            log.info("Output will be to directory {}.", this.outDir);
        // Initialize the PDB finder.
        log.info("Connection to PDB search API.");
        this.finder = new PdbFinder();
        // Initialize the role batch holder.
        this.roleBatch = new HashMap<String, GtoRoleData>((this.batchSize * 4 + 2) / 3);
        // Create the PDB output file.
        this.pdbWriter = new PrintWriter(new File(this.outDir, "pdbList.txt"));
        return true;
    }

    @Override
    protected void runCommand() throws Exception {
        try {
            // Start the PDB output file.
            this.pdbWriter.println("roleId\tname\tPDB");
            // We process the roles a batch at a time to avoid flooding memory.  Loop through the roles.
            int batchCount = 0;
            for (String roleId : this.targetRoles) {
                if (this.roleBatch.size() >= this.batchSize) {
                    // Here we need to make room for the next role.  Process the batch and empty it.
                    batchCount++;
                    log.info("Processing batch {}.", batchCount);
                    this.processBatch();
                }
                // Add this new role.
                this.roleBatch.put(roleId, new GtoRoleData(this.outDir, roleId, this.finder));
            }
            // Process the residual batch.
            log.info("Processing residual batch with {} roles.", this.roleBatch.size());
            this.processBatch();
        } finally {
            // Insure all the role files are closed.
            this.closeAll();
            // Close the PDB output file.
            this.pdbWriter.close();
        }
    }

    /**
     * Close all the FASTA files and clear the batch.
     */
    private void closeAll() {
        // Close all the files.
        for (GtoRoleData roleData : this.roleBatch.values())
            roleData.close();
        // Clear the batch.
        this.roleBatch.clear();
    }

    /**
     * Process the current role batch.  We run through all the genomes, looking for PEGs with roles in the batch.
     *
     * @throws InterruptedException
     * @throws IOException
     */
    private void processBatch() throws IOException, InterruptedException {
        // Loop through the genomes.
        log.info("Scanning genomes.");
        for (Genome genome : this.genomes) {
            // We run through the pegs, looking for matching roles.
            int pegCount = 0;
            int roleCount = 0;
            int roleFound = 0;
            for (Feature feat : genome.getPegs()) {
                pegCount++;
                for (Role role : feat.getUsefulRoles(this.roleDefinitions)) {
                    roleCount++;
                    GtoRoleData roleData = this.roleBatch.get(role.getId());
                    if (roleData != null) {
                        // Here we have found a role we are looking for on this pass.
                        roleFound++;
                        roleData.process(feat);
                    }
                }
            }
            log.info("Scanned genome {}:  {} pegs, {} roles, {} processed.", genome, pegCount, roleCount, roleFound);
        }
        // Write out the PDBs.
        for (Map.Entry<String, GtoRoleData> roleEntry : this.roleBatch.entrySet()) {
            // Get the name and PDB ID of the role.
            String roleId = roleEntry.getKey();
            String roleDesc = this.roleDefinitions.getName(roleId);
            String pdbId = roleEntry.getValue().getPdbId();
            this.pdbWriter.format("%s\t%s\t%s%n", roleId, roleDesc, pdbId);
        }
        // Close all the role files.
        this.closeAll();
    }

}
