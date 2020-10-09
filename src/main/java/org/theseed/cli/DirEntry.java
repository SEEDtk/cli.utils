/**
 *
 */
package org.theseed.cli;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * This class represents an entry in a PATRIC directory.
 *
 * @author Bruce Parrello
 *
 */
public class DirEntry {

    // FIELDS
    /** name of the file */
    private String name;
    /** file type */
    private Type type;
    /** pattern for parsing directory listing lines */
    private static final Pattern LINE_PATTERN = Pattern.compile("\\S+\\s+\\S+\\s+\\d+\\s+\\w+\\s+\\d+\\s+[0-9:]+\\s+(\\S+)\\s+(.+)");

    /**
     * Enum for type of file
     */
    public static enum Type {
        FOLDER, JOB_RESULT, CONTIGS, READS, HTML, TEXT, NEWICK, OTHER;
    }

    /**
     * Construct a directory entry for the specified file and type.
     *
     * @param nameString	name of the file or folder
     * @param typeVal		type of the file or folder
     */
    protected DirEntry(String nameString, Type typeVal) {
        this.name = nameString;
        this.type = typeVal;
    }

    /**
     * Create a directory entry from a directory listing line.
     *
     * @param line		file description from a p3-ls -alt command
     *
     * @return the directory entry, or NULL if this is not a directory line
     */
    public static DirEntry create(String line) {
        DirEntry retVal = null;
        Matcher m = LINE_PATTERN.matcher(StringUtils.strip(line));
        if (m.matches()) {
            String typeString = m.group(1);
            String nameString = m.group(2);
            Type type;
            switch (typeString) {
            case "folder" :
                type = Type.FOLDER;
                break;
            case "job_result" :
                type = Type.JOB_RESULT;
                break;
            case "contigs" :
                type = Type.CONTIGS;
                break;
            case "reads" :
                type = Type.READS;
                break;
            case "html" :
                type = Type.HTML;
                break;
            case "nwk" :
                type = Type.NEWICK;
                break;
            case "txt" :
                type = Type.TEXT;
                break;
            default:
                type = Type.OTHER;
            }
            retVal = new DirEntry(nameString, type);
        }
        return retVal;
    }

    /**
     * @return the name of this directory entry
     */
    public String getName() {
        return this.name;
    }

    /**
     * @return the type of this directory entry
     */
    public Type getType() {
        return this.type;
    }

}
