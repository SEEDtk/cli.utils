/**
 *
 */
package org.theseed.cli.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.genome.Feature;
import org.theseed.genome.Genome;
import org.theseed.genome.iterator.GenomeSource;
import org.theseed.io.TabbedLineReader;
import org.theseed.proteins.Function;
import org.theseed.proteins.FunctionMap;
import org.theseed.reports.PegReporter;
import org.theseed.utils.BaseProcessor;
import org.theseed.utils.ParseFailureException;

/**
 * This command outputs tables of peg sequences, either DNA or protein, along with a function identifier for
 * all genomes in a GTO directory.  It will also output a table mapping function identifiers to functions.
 *
 * The positional parameters are the name of the input genome source and the name of the output directory.
 * Each output file will have a name of "pegs.XXXX.txt", where "XXXX" is the output format.  In addition,
 * a "functions.tbl" file will map the function identifiers to function abbreviations and names.  (Currently,
 * the function identifier is an integer.)  If "functions.tbl" already exists, it will be re-used and overwritten
 * at the end.
 *
 * The command-line options are as follows:
 *
 * -h	display command-line usage
 * -v	display more frequent log messages
 *
 * --format		output format (multiple, at least one required)
 * --source		type of genome source (default DIR)
 * --clear		erase output directory before processing
 *
 * @author Bruce Parrello
 *
 */
public class RoleTableProcessor extends BaseProcessor implements PegReporter.IParms {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(RoleTableProcessor.class);
    /** list of output reporters */
    private List<PegReporter> reporters;
    /** map of function IDs to names */
    private FunctionMap funMap;
    /** map of function IDs to numbers */
    private Map<String, Integer> funNumberMap;
    /** input genome source */
    private GenomeSource genomes;
    /** name of the function output file */
    private File functionFile;
    /** number ID of next function */
    private int nextFunNum;
    /** list of valid domains */
    private static final Set<String> PROKS = Set.of("Bacteria", "Archaea");

    // COMMAND-LINE OPTIONS

    /** output format list */
    @Option(name = "--format", usage = "desired output format (multiple)")
    private List<PegReporter.Type> outFormats;

    /** input source type */
    @Option(name = "--source", aliases = { "--type", "-t" }, usage = "genome source type")
    private GenomeSource.Type sourceType;

    /** if specified, the output directory will be cleared before processing */
    @Option(name = "--clear", usage = "if specified, the output directory will be cleared before processing")
    private boolean clearFlag;

    /** input directory */
    @Argument(index = 0, metaVar = "inDir", usage = "genome source file or directory", required = true)
    private File inDir;

    /** output directory */
    @Argument(index = 1, metaVar = "outDir", usage = "output directory name", required = true)
    private File outDir;

    // NESTED CLASSES

    private static class ByValue implements Comparator<Map.Entry<String, Integer>> {

        @Override
        public int compare(Entry<String, Integer> o1, Entry<String, Integer> o2) {
            int retVal = o1.getValue() - o2.getValue();
            if (retVal == 0)
                retVal = o1.getKey().compareTo(o2.getKey());
            return retVal;
        }

    }

    // METHODS

    @Override
    protected void setDefaults() {
        this.clearFlag = false;
        this.sourceType = GenomeSource.Type.DIR;
        this.outFormats = new ArrayList<PegReporter.Type>();
    }

    @Override
    protected boolean validateParms() throws IOException, ParseFailureException {
        // If no reports are specified, default to DNA.
        if (this.outFormats.size() == 0)
            this.outFormats.add(PegReporter.Type.DNA);
        // Validate the input.
        if (! this.inDir.exists())
            throw new FileNotFoundException("Input genome source " + this.inDir + " not found.");
        log.info("Connecting to genomes in {}.", this.inDir);
        this.genomes = this.sourceType.create(this.inDir);
        // Set up the output.
        if (! this.outDir.isDirectory()) {
            // Here we have to create the output directory.
            log.info("Creating output directory {}.", this.outDir);
            FileUtils.forceMkdir(this.outDir);
        } else if (this.clearFlag) {
            // Here the output directory exists, but we need to erase it.
            log.info("Erasing output directory {}.", this.outDir);
            FileUtils.cleanDirectory(this.outDir);
        } else {
            log.info("Output will be to directory {}.");
        }
        // Set up the function maps.
        log.info("Intializating function identification maps.");
        this.funMap = new FunctionMap();
        this.funNumberMap = new HashMap<String, Integer>(15000);
        // Check for a pre-existing function map.
        this.functionFile = new File(this.outDir, "functions.tbl");
        if (this.functionFile.exists()) {
            log.info("Reading pre-existing function identification from {}.", this.functionFile);
            this.readFunctionFile();
        } else {
            // No pre-existing map.  Set the starting function number to 1000;
            this.nextFunNum = 1000;
        }
        return true;
    }

    /**
     * Read in a pre-existing function file.  This allows us to maintain function definitions between runs.
     *
     * @throws IOException
     */
    private void readFunctionFile() throws IOException {
        // Open the file.
        try (TabbedLineReader funStream = new TabbedLineReader(this.functionFile)) {
            // Default to a function number of 0.
            this.nextFunNum = 0;
            // Loop through the data lines.
            for (TabbedLineReader.Line line : funStream) {
                String funId = line.get(0);
                int funNum = line.getInt(1);
                String funName = line.get(2);
                Function fun = new Function(funId, funName);
                this.funMap.put(fun);
                this.funNumberMap.put(funId, funNum);
                if (funNum >= this.nextFunNum) this.nextFunNum = funNum + 1;
            }
        }
    }

    @Override
    protected void runCommand() throws Exception {
        // Create the reporter list.
        this.reporters = new ArrayList<PegReporter>(this.outFormats.size());
        try {
            // Initialize the reporters.
            for (PegReporter.Type outFormat : this.outFormats) {
                final String formatName = outFormat.toString();
                String fileName = "pegs." + formatName.toLowerCase() + ".txt";
                OutputStream outStream = new FileOutputStream(new File(this.outDir, fileName));
                this.reporters.add(outFormat.create(this, outStream));
                log.info("{} output will be written to {}.", outFormat.toString(), fileName);
            }
            // Write the headers.
            this.reporters.stream().forEach(x -> x.writeHeaders());
            // Loop through the genomes.
            int gCount = 0;
            int gUsed = 0;
            int gTotal = this.genomes.size();
            int pegsOut = 0;
            for (Genome genome : this.genomes) {
                gCount++;
                // Insure this is a prok.
                if (PROKS.contains(genome.getDomain())) {
                    log.info("Processing {} of {}: {}.", gCount, gTotal, genome);
                    gUsed++;
                    // Loop through the pegs.
                    for (Feature feat : genome.getPegs()) {
                        // Verify we have a real function.
                        String funDesc = feat.getPegFunction();
                        if (! StringUtils.isBlank(funDesc) && ! Feature.isHypothetical(funDesc)) {
                            // Compute the function ID and number.
                            Function fun = this.funMap.findOrInsert(funDesc);
                            String funId = fun.getId();
                            int funNum;
                            if (! this.funNumberMap.containsKey(funId)) {
                                funNum = this.nextFunNum;
                                this.nextFunNum++;
                                this.funNumberMap.put(funId, funNum);
                            } else
                                funNum = this.funNumberMap.get(funId);
                            // Write the protein.
                            this.reporters.stream().forEach(x -> x.writeFeature(funNum, funId, feat));
                            pegsOut++;
                        }
                    }
                    log.info("{} total functions, {} total pegs written.", this.funNumberMap.size(), pegsOut);
                }
            }
            // Finish the reports.
            log.info("Finishing reports.  {} of {} genomes processed.", gUsed, gTotal);
            this.reporters.stream().forEach(x -> x.finishReport());
            log.info("Writing function dictionary to {}.", this.functionFile);
            // Sort the functions by ID number.
            String[] sortedFuns = this.funNumberMap.entrySet().stream().sorted(new ByValue())
                    .map(x -> x.getKey()).toArray(String[]::new);
            try (PrintWriter funStream = new PrintWriter(this.functionFile)) {
                funStream.println("num\tabbreviation\tname");
                for (String funId : sortedFuns)
                    funStream.format("%d\t%s\t%s%n", this.funNumberMap.get(funId), funId, this.funMap.getName(funId));
            }
        } finally {
            // Close all the reporters.
            log.info("Closing reports.");
            this.reporters.stream().forEach(x -> x.close());
        }
    }

}
