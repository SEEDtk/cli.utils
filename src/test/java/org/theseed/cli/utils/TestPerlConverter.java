/**
 *
 */
package org.theseed.cli.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.theseed.cli.PerlConverter;
import org.theseed.io.LineReader;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import junit.framework.TestCase;

/**
 * @author Bruce Parrello
 *
 */
public class TestPerlConverter extends TestCase {


    public void testConverter() throws IOException {
        List<String> strings = new ArrayList<String>(50);
        try (LineReader stringStream = new LineReader(new File("data", "q.tbl"))) {
            for (String string : stringStream) strings.add(string);
        }
        JsonObject parsed = PerlConverter.parse(strings);
        assertThat(parsed.size(), equalTo(11));
        assertThat(parsed.get("app"), equalTo("RNASeq"));
        assertThat(parsed.get("start_time"), equalTo("2020-10-06 22:21:44"));
        assertThat(parsed.get("elapsed_time"), equalTo(""));
        assertThat(parsed.get("parent_id"), nullValue());
        assertThat(parsed.get("workspace"), nullValue());
        JsonObject parms = (JsonObject) parsed.get("parameters");
        JsonArray libs = (JsonArray) parms.get("single_end_libs");
        assertThat(libs.size(), equalTo(0));
        libs = (JsonArray) parms.get("paired_end_libs");
        assertThat(libs.size(), equalTo(3));
        assertThat(libs.get(0), equalTo("thing1"));
        JsonObject reads = (JsonObject) libs.get(1);
        assertThat(reads.get("read1"), equalTo("/rastuser25@patricbrc.org/\\FonSyntBioThr/Fastq/\'.277_6-4-3_5_5hrs_S55_fq/277_6-4-3_IPTG_5_5hrs_rep2_S55_R1_001_ptrim.fq.gz"));
        assertThat(reads.size(), equalTo(2));
        assertThat(parms.get("reference_genome_id"), equalTo("511145.183"));
        assertThat(parsed.get("id"), equalTo("705026"));
        assertThat(parsed.get("submit_time"), equalTo("C"));
    }
}
