/**
 *
 */
package org.theseed.bin.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.theseed.test.Matchers.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.regex.Matcher;

import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.select.Evaluator;
import org.junit.Test;

/**
 * @author Bruce Parrello
 *
 */
public class TestBinParse {

    @Test
    public void test() throws IOException {
        File targetFile = new File("data", "BinningReport.html");
        String htmlString = FileUtils.readFileToString(targetFile, Charset.defaultCharset());
        Matcher m = BinReportProcessor.TABLE_PATTERN.matcher(htmlString);
        assertThat(m.find(), isTrue());
        String table = m.group(1);
        assertThat(table, startsWith("<table"));
        assertThat(table, endsWith("</table>"));
        assertThat(table.substring(5), not(containsString("<table")));
        Document doc = Jsoup.parse(table);
        Elements rows = doc.select(new Evaluator.Tag("tr"));
        assertThat(rows.size(), equalTo(2));
        String genomeId = "";
        for (Element row : rows) {
            Elements cells = row.select(new Evaluator.Tag("td"));
            if (cells.size() >= 2) {
                Element cell = cells.get(1);
                Element link = cell.selectFirst(new Evaluator.Tag("a"));
                genomeId = link.text();
            }
        }
        assertThat(genomeId, equalTo("378753.24"));
    }

}
