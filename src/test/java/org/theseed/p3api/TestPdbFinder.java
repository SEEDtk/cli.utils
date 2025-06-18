/**
 *
 */
package org.theseed.p3api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;

import org.apache.http.client.ClientProtocolException;
import org.junit.jupiter.api.Test;

/**
 * @author Bruce Parrello
 *
 */
public class TestPdbFinder {

    @Test
    public void testPdbFinding() throws ClientProtocolException, InterruptedException, IOException {
        PdbFinder finder = new PdbFinder();
        String seq = "MENLDALVSQALEAVRHTEDVNALEQIRVHYLGKKGELTQVMKTLGDLPAEERPKVGALINVAKEKVQDVLNARKTELEGAALAARLAAERIDVTLPGRGQLSGGLHPVTRTLERIEQCFSRIGYEVAEGPEVEDDYHNFEALNIPGHHPARAMHDTFYFNANMLLRTHTSPVQVRTMESQQPPIRIVCPGRVYRCDSDLTHSPMFHQVEGLLVDEGVSFADLKGTIEEFLRAFFEKQLEVRFRPSFFPFTEPSAEVDIQCVICSGNGCRVCKQTGWLEVMGCGMVHPNVLRMSNIDPEKFQGFAFGMGAERLAMLRYGVNDLRLFFDNDLRFLGQFR";
        String pdbId = finder.findPDB(seq);
        assertThat(pdbId, equalTo("4P71"));
        seq = "MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMAAAAAAAAAAAAAAAANNNNNNNNNNNNNN";
        pdbId = finder.findPDB(seq);
        assertThat(pdbId, nullValue());
    }

}
