/**
 *
 */
package org.theseed.reports;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.rna.RnaData;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Here the report is written to an excel file.  This allows us to put links in and makes it possible for the consumer to do sorts
 * and other analytical transformations.
 *
 * @author Bruce Parrello
 *
 */
public class ExcelFpkmReporter extends FpkmReporter {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(ExcelFpkmReporter.class);
    /** current workbook */
    private Workbook workbook;
    /** current worksheet */
    private Sheet worksheet;
    /** saved output stream */
    private OutputStream outStream;
    /** input directory used to compute samstat links */
    private String inDir;
    /** next row number */
    private int rowNum;
    /** number of samples */
    private int nSamples;
    /** current row */
    private org.apache.poi.ss.usermodel.Row ssRow;
    /** header style */
    private CellStyle headStyle;
    /** alert style */
    private CellStyle alertStyle;
    /** normal style */
    private CellStyle numStyle;
    /** default column width */
    private static int DEFAULT_WIDTH = 10 * 256 + 128;
    /** function column width */
    private static int FUNCTION_WIDTH = 20 * 256;
    /** format for feature links */
    private static final String FEATURE_VIEW_LINK = "https://www.patricbrc.org/view/Feature/%s";

    /**
     * Construct the reporter for the specified output stream and controlling processor.
     *
     * @param output		target output stream
     * @param processor		controlling processor
     */
    public ExcelFpkmReporter(OutputStream output, IParms processor) {
        // Save the output stream.
        this.outStream = output;
        // Save the PATRIC workspace input directory name.
        this.inDir = processor.getInDir();
    }

    @Override
    protected void openReport(List<RnaData.JobData> samples) {
        log.info("Creating workbook.");
        // Save the sample count.
        this.nSamples = samples.size();
        // Create the workbook and the sheet.
        this.workbook = new XSSFWorkbook();
        this.worksheet = this.workbook.createSheet("FPKM");
        // Get a data formatter.
        DataFormat format = this.workbook.createDataFormat();
        short fmt = format.getFormat("###0.0000");
        // Create the header style.
        this.headStyle = this.workbook.createCellStyle();
        this.headStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        this.headStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        // Create the number style.
        this.numStyle = this.workbook.createCellStyle();
        this.numStyle.setDataFormat(fmt);
        // Create the alert style.
        this.alertStyle = this.workbook.createCellStyle();
        this.alertStyle.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        this.alertStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        // Now we add the column headers.
        this.rowNum = 0;
        this.addRow();
        this.setStyledCell(0, "peg_id", this.headStyle);
        this.setStyledCell(1, "function", this.headStyle);
        this.setStyledCell(2, "neighbor", this.headStyle);
        this.setStyledCell(3, "function", this.headStyle);
        int colNum = 3;
        // After the header columns, there is one column per sample.  Each is hyperlinked to its samstat page.
        for (RnaData.JobData sample : samples) {
            Cell cell = this.setStyledCell(++colNum, sample.getName(), headStyle);
            String url = this.getSamstatLink(sample.getName());
            this.setHref(cell, url);
        }
        // Now we have the two metadata rows.
        this.addRow();
        this.setStyledCell(0, "Thr g/l", this.headStyle);
        double[] prodValues = samples.stream().mapToDouble(x -> x.getProduction()).toArray();
        this.fillMetaRow(prodValues);
        this.addRow();
        this.setStyledCell(0, "OD", this.headStyle);
        double[] optValues = samples.stream().mapToDouble(x -> x.getOpticalDensity()).toArray();
        this.fillMetaRow(optValues);
    }

    /**
     * Fill the current row with meta-data numbers.
     *
     * @param values	array of meta-data values
     */
    private void fillMetaRow(double[] values) {
        int colNum = 3;
        for (double v : values) {
            if (! Double.isNaN(v))
                this.setNumCell(++colNum, v);
        }
    }

    /**
     * Add a new row to the spreadsheet.
     */
    protected void addRow() {
        this.ssRow = this.worksheet.createRow(this.rowNum);
        this.rowNum++;
    }

    /**
     * Set the hyperlink for a cell.
     *
     * @param cell	cell to receive the hyperlink
     * @param url	URL to link from the cell
     */
    protected void setHref(Cell cell, String url) {
        final Hyperlink href = workbook.getCreationHelper().createHyperlink(HyperlinkType.URL);
        href.setAddress(url);
        cell.setHyperlink(href);
    }

    /**
     * Create a styled cell in the current row.
     *
     * @param i				column number of new cell
     * @param string		content of the cell
     * @param style			style to give the cell
     *
     * @return the created cell
     */
    private Cell setStyledCell(int i, String string, CellStyle style) {
        Cell retVal = setTextCell(i, string);
        retVal.setCellStyle(style);
        return retVal;
    }

    /**
     * Create a normal cell in the current row.
     *
     * @param i				column number of new cell
     * @param string		content of the cell
     *
     * @return the created cell
     */
    protected Cell setTextCell(int i, String string) {
        Cell retVal = this.ssRow.createCell(i);
        retVal.setCellValue(string);
        return retVal;
    }

    /**
     * @return the samstat link for a sample
     *
     * @param jobName	sample ID
     */
    private String getSamstatLink(String jobName) {
        return String.format("https://www.patricbrc.org/workspace%s/.%s_rna/Tuxedo_0_replicate1_%s_R1_001_ptrim.fq_%s_R2_001_ptrim.fq.bam.samstat.html",
                this.inDir, jobName, jobName, jobName);
    }

    /**
     * Link a cell to a PATRIC feature.
     *
     * @param cell	cell to link
     * @param feat	target feature for link (if NULL, no link will be generated)
     */
    public void setHref(Cell cell, RnaData.FeatureData feat) {
        if (feat != null) {
            try {
                String encodedFid = URLEncoder.encode(feat.getId(), StandardCharsets.UTF_8.toString());
                String url = String.format(FEATURE_VIEW_LINK, encodedFid);
                this.setHref(cell, url);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("UTF-8 encoding unsupported.");
            }
        }
    }


    @Override
    protected void writeRow(RnaData.Row row) {
        // Get the main feature and its function.
        RnaData.FeatureData feat = row.getFeat();
        String fid = feat.getId();
        // Create the row and put in the heading cell.
        this.addRow();
        Cell cell = this.setStyledCell(0, fid, this.headStyle);
        this.setHref(cell, feat);
        this.setTextCell(1, feat.getFunction());
        // Process the neighbor.
        RnaData.FeatureData neighbor = row.getNeighbor();
        String neighborId = "";
        String neighborFun = "";
        if (neighbor != null) {
            neighborId = neighbor.getId();
            neighborFun = neighbor.getFunction();
        }
        cell = this.setTextCell(2, neighborId);
        this.setHref(cell, neighbor);
        this.setTextCell(3, neighborFun);
        // Now we run through the weights.
        int colNum = 3;
        for (int i = 0; i < this.nSamples; i++) {
            RnaData.Weight weight = row.getWeight(i);
            colNum++;
            if (weight == null)
                this.setTextCell(colNum, "");
            else {
                cell = this.setNumCell(colNum, weight.getWeight());
                // An inexact hit is colored pink.
                if (! weight.isExactHit())
                    cell.setCellStyle(this.alertStyle);
            }
        }
    }

    /**
     * Create a cell with a numeric value.
     *
     * @param colNum	column number of the cell
     * @param num		number to put in the cell
     *
     * @return the cell created
     */
    private Cell setNumCell(int colNum, double num) {
        Cell retVal = this.ssRow.createCell(colNum);
        retVal.setCellValue(num);
        return retVal;
    }

    @Override
    protected void closeReport() {
        // All done, write out the workbook.
        try {
            if (this.workbook != null) {
                // Freeze the headers.
                this.worksheet.createFreezePane(1, 1);
                // Fix the column widths.
                this.worksheet.autoSizeColumn(0);
                this.worksheet.setColumnWidth(1, FUNCTION_WIDTH);
                this.worksheet.autoSizeColumn(2);
                this.worksheet.setColumnWidth(3, FUNCTION_WIDTH);
                for (int i = 4; i < this.nSamples + 4; i++)
                    this.worksheet.setColumnWidth(i, DEFAULT_WIDTH);
                log.info("Writing workbook.");
                this.workbook.write(this.outStream);
            }
            this.outStream.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
