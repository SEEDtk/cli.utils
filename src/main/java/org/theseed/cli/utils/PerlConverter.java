/**
 *
 */
package org.theseed.cli.utils;

import java.util.Iterator;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;

/**
 * This is a utility class that converts a PERL data dump of a hash into a JSON object.  It is highly recursive.
 * We only use this for the special case of the verbose mode in the PATRIC CLI, in which all data types are strings,
 * lists, or hashes.  The only primitive that is NOT a string is "undef", which we record as NULL.
 *
 * @author Bruce Parrello
 *
 */
public class PerlConverter {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(PerlConverter.class);
    /** text of the dump being parsed */
    private String buffer;
    /** current position in the buffer */
    private int pos;
    /** start-of-dump marker */
    private static final String START_OF_DUMP = "$VAR1 = {";
    /** end-of-dump marker */
    private static final String END_OF_DUMP = "};";
    /** hash arrow */
    private static final String HASH_ARROW = "=> ";

    protected PerlConverter(List<String> lines) {
        // Allocate a buffer.
        StringBuffer temp = new StringBuffer(lines.size() * 40);
        // Find the start of the dump.
        Iterator<String> iter = lines.iterator();
        while (iter.hasNext() && ! START_OF_DUMP.contentEquals(StringUtils.stripToEmpty(iter.next())));
        // Assemble the dump into the buffer.
        String line = "{ ";
        while (! END_OF_DUMP.contentEquals(line)) {
            temp.append(line).append(' ');
            if (iter.hasNext())
                line = StringUtils.stripToEmpty(iter.next());
            else
                line = "};";
        }
        // Close off the dump hash.
        temp.append(" };");
        // Store the dump in this object.
        this.buffer = temp.toString();
        this.pos = 0;
    }

    /**
     * Consume a hash at the current position.
     *
     * @return a JSON object for the hash
     */
    protected JsonObject parseHash() {
        JsonObject retVal = new JsonObject();
        // Consume the open brace.
        this.pos++;
        // Skip whitespace.
        this.eatWhite();
        // We are now positioned on the start of a mapping.  If it is the hash terminator, we stop.
        while (this.current() != '}') {
            // There is still more hash to go.  Parse the label.
            String label = this.parseString();
            // Consume the arrow.
            this.pos += HASH_ARROW.length();
            // Determine what we have here and attach it.
            Object target = this.parseObject();
            retVal.put(label, target);
        }
        // Push past the closing brace.
        this.pos++;
        this.eatWhite();
        return retVal;
    }

    /**
     * @return the object at the current position.
     */
    private Object parseObject() {
        Object retVal;
        switch (this.current()) {
        case '[' :
            retVal = this.parseList();
            break;
        case '{' :
            retVal = this.parseHash();
            break;
        default :
            retVal = this.parseString();
        }
        return retVal;
    }

    /**
     * @return the list at the current position.
     */
    protected JsonArray parseList() {
        JsonArray retVal = new JsonArray();
        // Consume the open bracket.
        this.pos++;
        // Skip whitespace.
        this.eatWhite();
        // Loop until we find the close bracket.
        while (this.current() != ']') {
            Object target = this.parseObject();
            retVal.add(target);
            // Skip the whitespace.
            this.eatWhite();
        }
        // Eat the closing bracket.
        this.pos++;
        // Eat the following whitespace.
        this.eatWhite();
        return retVal;
    }

    /**
     * @return the string at the current position.
     */
    protected String parseString() {
        String retVal = null;
        if (this.current() != '\'') {
            // Here we have an undef.  Skip to the comma.
            while(this.current() != ',') this.pos++;
        } else {
            // Here we have a real string.  We scan for a closing quote.  The only tricky part
            // is that there may be an escaped quote in the middle.
            StringBuffer valBuf = new StringBuffer(100);
            this.pos++;
            while (this.current() != '\'') {
                if (this.current() == '\\') this.pos++;
                valBuf.append(this.current());
                this.pos++;
            }
            retVal = valBuf.toString();
            // Eat the closing quote.
            this.pos++;
        }
        // Eat the following whitespace.
        this.eatWhite();
        return retVal;
    }

    /**
     * @return the current character in the buffer
     */
    protected char current() {
        char retVal = 0;
        int pos0 = this.pos;
        try {
            retVal = this.buffer.charAt(pos0);
        } catch (StringIndexOutOfBoundsException e) {
            log.error("Could not parse-- out-of-bounds error at {} in string.\n{}", pos0, this.buffer);
            throw e;
        }
        return retVal;
    }

    /**
     * Consume all spaces at the current position.
     */
    protected void eatWhite() {
        // Skip a leading comma.
        if (this.current() == ',') this.pos++;
        // Skip all the spaces.
        while(this.current() <= ' ')
            this.pos++;
    }

    /**
     * Parse a PERL data dump out of a list of strings.
     *
     * @param list	list of strings to parse
     *
     * @return the JsonObject represented by the dump
     */
    public static JsonObject parse(List<String> list) {
        PerlConverter converter = new PerlConverter(list);
        JsonObject retVal = converter.parseHash();
        return retVal;
    }

}
