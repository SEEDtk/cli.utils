/**
 *
 */
package org.theseed.dl4j.utils;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the base class for creating a classification directory.
 *
 * @author Bruce Parrello
 *
 */
public abstract class XMatrixDir {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(XMatrixDir.class);
    /** output directory name */
    private File outDir;
    /** recommended output format */
    private String doubleFormat = "%6.1f";

    /**
     * Enumeration for X-matrix directory types
     */
    public static enum Type {
        DL4J {
            @Override
            public XMatrixDir create(File outDir) throws IOException {
                return new Dl4jXmatrixDir(outDir);
            }
        }, XFILES {
            @Override
            public XMatrixDir create(File outDir) throws IOException {
                return new XfilesXMatrixDir(outDir);
            }
        };

        /**
         * @return a directory-creation object for this directory type
         *
         * @param outDir	output directory name
         *
         * @throws IOException
         */
        public abstract XMatrixDir create(File outDir) throws IOException;
    }

    /**
     * Construct the Xmatrix directory creator.
     *
     * @param outDir	output directory name
     */
    public XMatrixDir(File outDir) {
        this.outDir = outDir;
    }

    /**
     * Specify the list of output labels.
     *
     * @param labelSet	list of output labels, with the default label first
     *
     * @throws IOException
     */
    public abstract void setLabels(Collection<String> labelSet) throws IOException;

    /**
     * @return a file in the output directory
     *
     * @param name		base name for the file
     */
    public File getFile(String name) {
        return new File(this.outDir, name);
    }

    /**
     * Specify the column headings.
     *
     * @param idName		ID column name
     * @param inputIds		array of input column names
     * @param labelName		label name
     *
     * @throws IOException
     */
    public abstract void setColumns(String idName, String[] inputIds, String labelName) throws IOException;

    /**
     * Write the line for a single data row.
     *
     * @param rowId			row identifier
     * @param inputValues	array of input values
     * @param labelVal		label value (output)
     *
     * @throws IOException
     */
    public abstract void writeSample(String rowId, double[] inputValues, String labelVal) throws IOException;

    /**
     * Finish creating the directory.
     *
     * @throws IOException
     */
    public abstract void finish() throws IOException;

    /**
     * @return a formatted input value
     */
    public String getFormatted(double value) {
        return String.format(doubleFormat, value);
    }

    /**
     * Specify the format to use for input values.
     *
     * @param doubleFormat the format to use for values
     */
    public void setDoubleFormat(String doubleFormat) {
        this.doubleFormat = doubleFormat;
    }

}
