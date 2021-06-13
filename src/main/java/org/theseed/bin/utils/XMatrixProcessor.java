/**
 *
 */
package org.theseed.bin.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.io.FileUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.dl4j.utils.XMatrixDir;
import org.theseed.io.TabbedLineReader;
import org.theseed.utils.BaseProcessor;
import org.theseed.utils.ParseFailureException;

/**
 * This command creates a classification matrix for a set of bin reports.  The reports should be in the
 * GENOMES format from the BinReportProcessor command.  The positional parameters are the name of the output
 * directory and then a list of input specifiers arranged in pairs.  The first item in each pair is a label
 * 0value and the second is the file name for a bin report.  So, for example
 *
 * 		cli.utils xmatrix parkinsons parkReport.tbl alzheimers alzReport.tbl control controlReport.tbl
 *
 * would assign the samples in "parkReport.tbl" the label "parkinsons", the samples in "alzReport.tbl" the
 * label "alzheimers", and the samples is "controlReport.tbl" the label "control".
 *
 * The command-line options are as follows.
 *
 * -h	display command-line usage
 * -v	display more frequent log messages
 *
 * --clear		erase the output directory before processing
 * --label		name to give to the label column (default "type")
 * --format		format of the output directory (default DL4J)
 *
 * @author Bruce Parrello
 *
 */
public class XMatrixProcessor extends BaseProcessor {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(XMatrixProcessor.class);
    /** map of sample IDs to repgen ID sets */
    private Map<String, Set<String>> sampleGenomeMap;
    /** map of sample IDs to types */
    private Map<String, String> sampleTypeMap;
    /** map of label values to input file names */
    private Map<String, File> inputFileMap;
    /** set of repgen IDs found */
    private Set<String> genomeIdSet;
    /** expected number of input samples */
    private static final int EXPECTED_SAMPLES = 500;

    // COMMAND-LINE OPTIONS

    /** label column name */
    @Option(name = "--label", usage = "name to give to the label column")
    private String labelCol;

    /** output directory format */
    @Option(name = "--format", usage = "format of output directory")
    private XMatrixDir.Type outFormat;

    /** TRUE if we should clear the directory before processing */
    @Option(name = "--clear", usage = "if specified, the output directory will be cleared before processing")
    private boolean clearFlag;

    /** output directory */
    @Argument(index = 0, metaVar = "outDir", usage = "output directory", required = true)
    private File outDir;

    /** specifications-- label followed by file, repeated */
    @Argument(index = 1, metaVar = "label1 inFile1 label2 inFile2 ...", usage = "list of label/input-file pairs")
    private List<String> specifications;

    @Override
    protected void setDefaults() {
        this.labelCol = "type";
        this.outFormat = XMatrixDir.Type.DL4J;
        this.clearFlag = false;
    }

    @Override
    protected boolean validateParms() throws IOException, ParseFailureException {
        // Verify the output directory.
        if (! this.outDir.isDirectory()) {
            log.info("Creating output directory {}.", this.outDir);
            FileUtils.forceMkdir(this.outDir);
        } else if (this.clearFlag) {
            log.info("Erasing output directory {}.", this.outDir);
            FileUtils.cleanDirectory(this.outDir);
        } else
            log.info("Output will be to directory {}.", this.outDir);
        // Verify that the input file specs are paired.
        if (specifications.size() % 2 != 0)
            throw new ParseFailureException("Input specifications must be in label/file pairs.");
        // Create the input file hash.
        this.inputFileMap = new LinkedHashMap<String, File>(this.specifications.size() / 2);
        for (int i = 0; i < this.specifications.size(); i += 2) {
            String label = this.specifications.get(i);
            File inFile = new File(this.specifications.get(i+1));
            if (! inFile.canRead())
                throw new FileNotFoundException("Input file " + inFile + " is not found or unreadable.");
            this.inputFileMap.put(label, inFile);
        }
        return true;
    }

    @Override
    protected void runCommand() throws Exception {
        // Initialize the output directory creator.
        XMatrixDir dirCreator = this.outFormat.create(this.outDir);
        // Create the tracking maps.
        this.genomeIdSet = new TreeSet<String>();
        this.sampleGenomeMap = new HashMap<String, Set<String>>(EXPECTED_SAMPLES);
        this.sampleTypeMap = new HashMap<String, String>(EXPECTED_SAMPLES);
        // This will contain the list of labels, in order.  We allow duplicates.
        List<String> labelSet = new ArrayList<String>(this.inputFileMap.size());
        // This will count the total number of bins found.
        int binCount = 0;
        // Now we loop through the input pairs, filling in the above maps.
        for (Map.Entry<String, File> specification : this.inputFileMap.entrySet()) {
            String label = specification.getKey();
            File inFile = specification.getValue();
            log.info("Processing samples of type {} in file {}.", label, inFile);
            // The number of labels is always small, so this will not be prohibitively slow.
            if (! labelSet.contains(label))
                labelSet.add(label);
            // Now we need to process all the bins in the file.  For each bin, we need the sample ID and
            // the repgen ID.
            try (TabbedLineReader inStream = new TabbedLineReader(inFile)) {
                int sampleCol = inStream.findField("sample_id");
                int repgenCol = inStream.findField("repgen_id");
                for (TabbedLineReader.Line line : inStream) {
                    String sample = line.get(sampleCol);
                    String repgenId = line.get(repgenCol);
                    // Record the sample.
                    this.sampleTypeMap.put(sample, label);
                    // Add the repgenId to the full repgenId set.
                    this.genomeIdSet.add(repgenId);
                    // Add it to the sample's repgenId set.
                    Set<String> sampleSet = this.sampleGenomeMap.computeIfAbsent(sample, x -> new TreeSet<String>());
                    sampleSet.add(repgenId);
                    // Count the bin.
                    binCount++;
                }
            }
            log.info("{} total samples processed.  {} total repgen IDs found so far in {} total bins.",
                    this.sampleTypeMap.size(), this.genomeIdSet.size(), binCount);
        }
        // Create an array of the genome IDs.
        String[] repgenIds = this.genomeIdSet.stream().toArray(String[]::new);
        // Now all our hashes have been built.  Send the labels to the directory creator.
        log.info("Writing to output directory {}.", this.outDir);
        dirCreator.setLabels(labelSet);
        // Next, send the column headings.
        dirCreator.setColumns("sample", repgenIds, this.labelCol);
        // Now we pass in the rows, one at a time.  For each row, there is a sample ID, an array of 1/0 values, and
        // then the sample type.  To start, we allocate the array of doubles for the input (1/0) columns.
        double[] inputValues = new double[this.genomeIdSet.size()];
        for (Map.Entry<String, Set<String>> sampleData : this.sampleGenomeMap.entrySet()) {
            String sample = sampleData.getKey();
            Set<String> sampleRepgens = sampleData.getValue();
            // Loop through the full genome ID set, filling in the input values.
            for (int i = 0; i < this.genomeIdSet.size(); i++)
                inputValues[i] = (sampleRepgens.contains(repgenIds[i]) ? 1.0 : 0.0);
            // Write this sample to the output.
            dirCreator.writeSample(sample, inputValues, this.sampleTypeMap.get(sample));
        }
        // Finish up the output directory.
        dirCreator.finish();
    }
}
