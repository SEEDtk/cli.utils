/**
 *
 */
package org.theseed.cli.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.theseed.cli.CopyTask;
import org.theseed.cli.DirEntry;
import org.theseed.cli.DirTask;
import org.theseed.io.LineReader;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Bruce Parrello
 *
 */
public class TestDirectory {

    /**
     * Test directory entries
     * @throws IOException
     */
    @Test
    public void testDirEntry() throws IOException {
        try (LineReader reader = new LineReader(new File("data", "dir.tbl"))) {
            DirEntry entry = DirEntry.create(reader.next());
            assertThat(entry.getType(), equalTo(DirEntry.Type.HTML));
            assertThat(entry.getName(), equalTo("G472-T23-30319_assembly_report.html"));
            entry = DirEntry.create(reader.next());
            assertThat(entry.getType(), equalTo(DirEntry.Type.CONTIGS));
            assertThat(entry.getName(), equalTo("G472-T23-30319_contigs.fasta"));
            entry = DirEntry.create(reader.next());
            assertThat(entry.getType(), equalTo(DirEntry.Type.FOLDER));
            assertThat(entry.getName(), equalTo("details"));
            entry = DirEntry.create(reader.next());
            assertThat(entry.getType(), equalTo(DirEntry.Type.TEXT));
            assertThat(entry.getName(), equalTo("p3x-assembly.stderr"));
            entry = DirEntry.create(reader.next());
            assertThat(entry.getType(), equalTo(DirEntry.Type.FOLDER));
            assertThat(entry.getName(), equalTo("Akkermansia muciniphila LeChatelierE_2013__MH0431__bin.34"));
            entry = DirEntry.create(reader.next());
            assertThat(entry.getType(), equalTo(DirEntry.Type.FOLDER));
            assertThat(entry.getName(), equalTo(".Algoriphagus marincola HL-49 clonal population trimmed"));
            entry = DirEntry.create(reader.next());
            assertThat(entry.getType(), equalTo(DirEntry.Type.JOB_RESULT));
            assertThat(entry.getName(), equalTo("Algoriphagus marincola HL-49 clonal population trimmed"));
            entry = DirEntry.create(reader.next());
            assertThat(entry.getType(), equalTo(DirEntry.Type.READS));
            assertThat(entry.getName(), equalTo("277_12hrs_S31_R1_001.fastq"));
            entry = DirEntry.create(reader.next());
            assertThat(entry.getType(), equalTo(DirEntry.Type.OTHER));
            assertThat(entry.getName(), equalTo("277_12hrs_S31_R1_001.fastq.md5"));
        }
    }

    @Test
    public void testDirTask() {
        DirTask dirTask = new DirTask(new File("data"), "rastuser25@patricbrc.org");
        List<DirEntry> dirList = dirTask.list("/rastuser25@patricbrc.org/Binning.Webinar");
        assertThat(dirList.size(), equalTo(10));
        for (DirEntry entry : dirList) {
            switch (entry.getName()) {
            case "Big" :
                assertThat(entry.getType(), equalTo(DirEntry.Type.FOLDER));
                break;
            case "Medium" :
                assertThat(entry.getType(), equalTo(DirEntry.Type.FOLDER));
                break;
            case "Metagenomic Binning BV-BRC.pptx" :
                assertThat(entry.getType(), equalTo(DirEntry.Type.OTHER));
                break;
            case "SRS014683.fa" :
                assertThat(entry.getType(), equalTo(DirEntry.Type.CONTIGS));
                break;
            case "SRS014683.scaffolds.fa" :
                assertThat(entry.getType(), equalTo(DirEntry.Type.CONTIGS));
                break;
            case "SRS014683_extract.1.fq" :
                assertThat(entry.getType(), equalTo(DirEntry.Type.READS));
                break;
            case "SRS014683_extract.2.fq" :
                assertThat(entry.getType(), equalTo(DirEntry.Type.READS));
                break;
            case "Small" :
                assertThat(entry.getType(), equalTo(DirEntry.Type.FOLDER));
                break;
            case "contigs.fasta" :
                assertThat(entry.getType(), equalTo(DirEntry.Type.CONTIGS));
                break;
            case "The BV-BRC Command-Line Interface.pptx" :
                assertThat(entry.getType(), equalTo(DirEntry.Type.OTHER));
                break;
            default :
                fail("Invalid directory entry " + entry.getName());
            }
        }
        dirList = dirTask.list("/rastuser25@patricbrc.org/Binning.Webinar/Medium");
        assertThat(dirList.size(), equalTo(11));
    }

    public void testCopyTask() throws IOException {
        CopyTask copyTask = new CopyTask(new File("data"), "rastuser25@patricbrc.org");
        File[] copied = copyTask.copyRemoteFolder("/rastuser25@patricbrc.org/Binning.Webinar/Big", false);
        assertThat(copied.length, equalTo(3));
        List<String> names = Arrays.stream(copied).map(f -> f.getName()).collect(Collectors.toList());
        assertThat(names, containsInAnyOrder("coupling10.test.tbl", "coupling200.log", "couplings200.random.weighted.tbl"));
        for (File copy : copied)
            assertThat(copy.getAbsolutePath(), copy.canRead(), equalTo(true));
    }

}
