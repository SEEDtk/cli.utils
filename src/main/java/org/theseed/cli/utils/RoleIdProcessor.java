/**
 *
 */
package org.theseed.cli.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.basic.ParseFailureException;
import org.theseed.genome.Feature;
import org.theseed.io.TabbedLineReader;
import org.theseed.proteins.Role;
import org.theseed.proteins.RoleMap;
import org.theseed.utils.BasePipeProcessor;

/**
 * This command will take as input a file with functional assignments in a particular column and will
 * convert them to role IDs using a role definition file.  Note that since a function may contain multiple
 * roles, multiple role IDs may be output for a single input line.  Roles that have no ID will be
 * eliminated, which may cause input lines to be lost.  The net effect us not only to convert the functions
 * to role IDs, but also to filter for roles of interest.
 *
 * The positional parameter is the name of the role definition file.  The command-line options are as follows.
 *
 * -h	display command-line usage
 * -v	display more frequent log messages
 * -i	input file (if not STDIN)
 * -o	output file (if not STDOUT)
 * -c	index (1-based) or name of column to convert
 *
 * @author Bruce Parrello
 *
 */
public class RoleIdProcessor extends BasePipeProcessor {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(RoleIdProcessor.class);
    /** role definition map */
    private RoleMap roleMap;
    /** input column index */
    private int colIdx;

    // COMMAND-LINE OPTIONS

    /** input column to convert */
    @Option(name = "--col", aliases = { "-c" }, metaVar = "role_name", usage = "index (1-based) or name of column containing function assignment")
    private String col;

    /** role definition file */
    @Argument(index = 0, metaVar = "roles.in.subsystems", usage = "role definition file for roles of interest")
    private File roleFile;

    @Override
    protected void setPipeDefaults() {
        this.col = "1";
    }

    @Override
    protected void validatePipeInput(TabbedLineReader inputStream) throws IOException {
        // Get the index of the input column.
        this.colIdx = inputStream.findField(this.col);
    }

    @Override
    protected void validatePipeParms() throws IOException, ParseFailureException {
        // Read in the role file.
        if (! this.roleFile.canRead())
            throw new FileNotFoundException("Role file " + this.roleFile + " not found or unreadable.");
        log.info("Reading roles from {}.", this.roleFile);
        this.roleMap = RoleMap.load(this.roleFile);
    }

    @Override
    protected void runPipeline(TabbedLineReader inputStream, PrintWriter writer) throws Exception {
        // Write the header line to the output.
        writer.println(inputStream.header());
        // Loop through the input file.
        int inCount = 0;
        int outCount = 0;
        for (TabbedLineReader.Line line : inputStream) {
            inCount++;
            // This will be the output line.
            String[] columns = new String[inputStream.size()];
            // Copy the non-input columns.
            for (int i = 0; i < columns.length; i++) {
                if (i != this.colIdx)
                    columns[i] = line.get(i);
            }
            // Now convert the input column.  For each role found, output a line.
            String function = line.get(this.colIdx);
            List<Role> roles = Feature.usefulRoles(this.roleMap, function);
            for (Role role : roles) {
                columns[this.colIdx] = role.getId();
                writer.println(StringUtils.join(columns, '\t'));
                outCount++;
            }
        }
        log.info("{} lines read, {} lines written.", inCount, outCount);
    }

}
