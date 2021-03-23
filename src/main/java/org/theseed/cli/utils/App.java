package org.theseed.cli.utils;

import java.util.Arrays;

import org.theseed.rna.utils.FpkmAllProcessor;
import org.theseed.rna.utils.FpkmSummaryProcessor;
import org.theseed.rna.utils.RnaCopyProcessor;
import org.theseed.rna.utils.RnaSeqProcessor;
import org.theseed.rna.utils.SampleMetaFixProcessor;
import org.theseed.rna.utils.SampleMetaProcessor;
import org.theseed.utils.BaseProcessor;

/**
 * Commands for utilities relating to CLI processing.
 *
 * fpkm			run jobs to convert FASTQ files to FPKM results
 * fpkmSummary	produce a summary file from FPKM results
 * fpkmAll		generate all of the standard RNA SEQ files
 * rnaCopy		copy RNA read files into PATRIC for processing by the FPKM commands
 * rnaSetup		update the sampleMeta.tbl file from the progress.txt and rna.production.tbl files
 * rnaProdFix	add production and density data to a sampleMeta.tbl file
 */
public class App
{
    public static void main( String[] args )
    {
        // Get the control parameter.
        String command = args[0];
        String[] newArgs = Arrays.copyOfRange(args, 1, args.length);
        BaseProcessor processor;
        // Determine the command to process.
        switch (command) {
        case "fpkm" :
            processor = new RnaSeqProcessor();
            break;
        case "fpkmsummary" :
            processor = new FpkmSummaryProcessor();
            break;
        case "fpkmall" :
            processor = new FpkmAllProcessor();
            break;
        case "rnaCopy" :
            processor = new RnaCopyProcessor();
            break;
        case "rnaSetup" :
            processor = new SampleMetaProcessor();
            break;
        case "rnaProdFix" :
        	processor = new SampleMetaFixProcessor();
        	break;
        default:
            throw new RuntimeException("Invalid command " + command);
        }
        // Process it.
        boolean ok = processor.parseCommand(newArgs);
        if (ok) {
            processor.run();
        }
    }
}
