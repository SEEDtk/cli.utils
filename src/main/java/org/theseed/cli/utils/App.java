package org.theseed.cli.utils;

import java.util.Arrays;

import org.theseed.rna.utils.RnaSeqProcessor;
import org.theseed.utils.BaseProcessor;

/**
 * Commands for utilities relating to CLI processing.
 *
 * fpkm		run jobs to convert FASTQ files to FPKM results
 * rnaPage	produce a web page from RNA sequence alignment results
 *
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
