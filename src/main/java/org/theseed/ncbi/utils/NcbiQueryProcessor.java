/**
 *
 */
package org.theseed.ncbi.utils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.io.MarkerFile;
import org.theseed.ncbi.NcbiConnection;
import org.theseed.ncbi.NcbiQuery;
import org.theseed.ncbi.NcbiTable;
import org.theseed.ncbi.XmlException;
import org.theseed.ncbi.reports.NcbiTableReporter;
import org.theseed.utils.BaseReportProcessor;
import org.theseed.utils.ParseFailureException;
import org.w3c.dom.Element;

/**
 * This command retrieves a set of records from the NCBI Entrez system and returns then as
 * a flat file.  These records come back as complex XML documents, and the parsing is arcane.
 * The basic strategy is to create a report object for each type of flat file we want to return,
 * and allow the user to specify the report object.  The table being queried is an attribte of
 * the report, and it must provide methods for printing the header and formatting the output
 * line for each record found.
 *
 * The positional parameters are the report format followed by the query criteria.  Each crtierion
 * is specified as a field name followed by the desired field value (that is, as two positional
 * parameters).  If full field names are being used, each space must be replaced
 * by a "+", since the field names are URLEncoded.  Field values are URLEncoded automatically.
 * If there is a space in a field value, the value must be enclosed in quotes.
 *
 * IF NO FILTERS ARE SPECIFIED, the search fields in the target table will be listed.
 *
 * The command-line options are as follows:
 *
 * -h	display command-line usage
 * -v	display more frequent log messages
 * -o	report output file, if not STDOUT
 * -d	date code for type of date in "--since" (default PDAT)
 *
 * --since		minimum date for results (default is to return all results)
 * --sinceFile	name of a file to be used to track dates.  If the file exists, it will contain
 * 				the date type and date for date-filtering.  If it does not, all results will
 * 				be returned and today's date and the specified date code will be stored for
 * 				subsequent runs.
 *
 * @author Bruce Parrello
 *
 */
public class NcbiQueryProcessor extends BaseReportProcessor implements NcbiTableReporter.IParms {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(NcbiQueryProcessor.class);
    /** NCBI connection */
    private NcbiConnection ncbi;
    /** reporting object */
    private NcbiTableReporter reporter;
    /** query to run */
    private NcbiQuery query;
    /** regex pattern for valid dates */
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d+(?:-\\d+(?:\\d+)?)?");

    // COMMAND-LINE OPTIONS

    /** minimum date for results */
    @Option(name = "--since", metaVar = "2018-10-20", usage = "date in YYYY-MM-DD format to limit query to recent records")
    private String minDateString;

    /** date file for incremental runs */
    @Option(name = "--sinceFile", metaVar = "trackedDate.date", usage = "date file for incremental runs")
    private File minDateFile;

    /** type of date for minimum-date option */
    @Option(name = "--dType", aliases = { "-d" }, metaVar = "mdat", usage = "type of date for recent-record limit")
    private String dateType;

    /** report type */
    @Argument(index = 0, metaVar = "reportType", usage = "type of report to write", required = true)
    private NcbiTableReporter.Type reportType;

    /** field name / value pairs */
    @Argument(index = 1, metaVar = "fieldName1 fieldValue1 fieldName2 fieldValue2 ...",
            usage = "name and value for each filtering field")
    private List<String> filters;

    @Override
    protected void setReporterDefaults() {
        this.minDateString = null;
        this.dateType = "pdat";
        this.filters = null;
    }

    @Override
    protected void validateReporterParms() throws IOException, ParseFailureException {
        try {
            // Create the reporting object.
            this.reporter = this.reportType.create(this);
            // Get the validation set for the query filters.
            this.ncbi = new NcbiConnection();
            NcbiTable table = this.reporter.getTable();
            // Create the query.
            this.query = new NcbiQuery(table);
            // If we have filters, we need to do a bunch of setup to initialize the query.
            if (filters != null) {
                if (filters.size() % 2 != 0)
                    throw new ParseFailureException("Filtering parameters must be evenly matched:  name value name value...");
                Set<String> validNames = this.ncbi.getFieldNames(table);
                // Set up the filters.
                for (int i = 0; i < filters.size(); i += 2) {
                    String name = StringUtils.replaceChars(filters.get(i), ' ', '+');
                    String value = filters.get(i+1);
                    if (! validNames.contains(name))
                        throw new ParseFailureException("Invalid field name \"" + name + "\" for table " +
                                table.name() + ".");
                    this.query.EQ(name, value);
                }
                // Add the date qualification.  The date string is used first if it exists.
                if (this.minDateString != null) {
                    NcbiQuery.validateDateType(dateType);
                    if (! DATE_PATTERN.matcher(this.minDateString).matches())
                        throw new ParseFailureException("Invalid since-date.");
                    // We have to do a funny sort of parsing here, because we allow specifying just the
                    // year or just the year and the month.
                    String[] parts = StringUtils.split(this.minDateString, '-');
                    int year = 0;
                    int month = 1;
                    int day = 1;
                    if (parts.length > 2)
                        day = Integer.valueOf(parts[2]);
                    if (parts.length > 1)
                        month = Integer.valueOf(parts[1]);
                    year = Integer.valueOf(parts[0]);
                    LocalDate minDate = LocalDate.of(year, month, day);
                    this.query.since(dateType, minDate);
                } else if (this.minDateFile != null && this.minDateFile.exists()) {
                       // Here we must read the date info from the file.
                    String dateSpec = MarkerFile.read(this.minDateFile);
                    String[] parts = StringUtils.split(dateSpec, '\t');
                    if (parts.length != 2)
                        throw new IOException("Since-date file has invalid data.");
                    this.dateType = parts[0];
                    NcbiQuery.validateDateType(parts[0]);
                    LocalDate minDate = LocalDate.parse(parts[1]);
                    log.info("Restricting query to results with {} >= {}.", this.dateType, minDate.toString());
                    this.query.since(dateType, minDate);
                }
            }
        } catch (XmlException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void runReporter(PrintWriter writer) throws Exception {
        NcbiTable table = this.query.getTable();
        String tableName = table.db();
        // Determine the mode.
        if (this.filters == null) {
            log.info("Listing search fields in {} table.", tableName);
            List<NcbiConnection.Field> fieldList = this.ncbi.getFieldList(table);
            writer.println("name\tlong_name\tdescription");
            for (NcbiConnection.Field field : fieldList)
                writer.println(field.toLine());
        } else {
            log.info("Submitting query for {} records.", tableName);
            List<Element> results = this.query.run(this.ncbi);
            log.info("{} records returned from {} query.", results.size(), tableName);
            // Start the report.
            this.reporter.openReport(writer);
            // Loop through the records.
            for (Element result : results)
                this.reporter.writeRecord(result);
            // Close off the report.
            this.reporter.closeReport();
            // Flush the report so that everything gets out before we update the since-date file.
            writer.flush();
            // Now that we have succeeded, update the since-date file (if any).
            if (this.minDateFile != null) {
                String dateSpec = this.dateType + "\t" + LocalDate.now().toString();
                MarkerFile.write(this.minDateFile, dateSpec);
                log.info("Since-date file {} updated for {} dates >= today.", this.minDateFile, this.dateType);
            }
        }
    }

}
