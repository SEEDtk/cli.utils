/**
 *
 */
package org.theseed.bin.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.FileNotFoundException;
import org.junit.jupiter.api.Test;

/**
 * @author Bruce Parrello
 *
 */
class TestPerlRun {

    @Test
    void testPerlProgram() throws FileNotFoundException {
        BinPipeline.setBinPath(new File("data"));
        File dataFile = new File("data", "test.fasta");
        var pipe = new BinPipeline();
        boolean ok = pipe.runPerl("perlTest", dataFile.getAbsolutePath());
        assertThat(ok, equalTo(true));
    }

}
