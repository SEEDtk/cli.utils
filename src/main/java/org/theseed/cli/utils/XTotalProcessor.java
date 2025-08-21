/**
 *
 */
package org.theseed.cli.utils;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.kohsuke.args4j.Argument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.basic.ParseFailureException;
import org.theseed.io.TabbedLineReader;
import org.theseed.utils.BasePipeProcessor;

/**
 * This is a simpe utility that takes as input an xmatrix file and outputs the total
 * value in each input column.  The positional parameters are the names of the metadata
 * and output columns.  The xmatrix is read from the standard input, and the report is
 * produced on the standard output.
 *
 * The command-line options are as follows:
 *
 * -h	display command-line usage
 * -v	display more frequent log messages
 * -i	input xmatrix file (if not STDIN)
 * -o	output report file (if not STDOUT)
 *
 * @author Bruce Parrello
 *
 */
public class XTotalProcessor extends BasePipeProcessor {

    // FIELDS
    /** logging facility */
    private static final Logger log = LoggerFactory.getLogger(XTotalProcessor.class);
    /** set of columns to skip */
    private Set<Integer> skipCols;

    // COMMAND-LINE OPTIONS

    /** names of columns to skip */
    @Argument(index = 0, metaVar = "col1 col2 ...", usage = "names of columns to omit from the output")
    private List<String> metaCols;

    @Override
    protected void setPipeDefaults() {
        this.metaCols = new ArrayList<String>();
    }

    @Override
    protected void validatePipeInput(TabbedLineReader inputStream) throws IOException {
        // Insure all of the metadata columns exist.  We get the index of each and store
        // it in the skip set.
        this.skipCols = new TreeSet<Integer>();
        for (String metaCol : metaCols) {
            int colIdx = inputStream.findColumn(metaCol);
            this.skipCols.add(colIdx);
        }
        log.info("{} metadata columns identified in input.", this.skipCols.size());
    }

    @Override
    protected void validatePipeParms() throws IOException, ParseFailureException {
    }

    @Override
    protected void runPipeline(TabbedLineReader inputStream, PrintWriter writer) throws Exception {
        // Build the count map.
        var counts = new TreeMap<String, Double>();
        // Loop through the records, accumulating the counts.
        String[] labels = inputStream.getLabels();
        Arrays.stream(labels).forEach(x -> counts.put(x, 0.0));
        for (TabbedLineReader.Line line : inputStream) {
            for (int i = 0; i < labels.length; i++) {
                if (! this.skipCols.contains(i)) {
                    double val = line.getDouble(i);
                    counts.put(labels[i], counts.get(labels[i]) + val);
                }
            }
        }
        // Output the counts.
        writer.println("col_name\ttotal");
        for (Map.Entry<String, Double> count : counts.entrySet())
            writer.format("%s\t%6.2f%n", count.getKey(), count.getValue());
    }

}
