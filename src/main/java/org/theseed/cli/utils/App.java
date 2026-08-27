package org.theseed.cli.utils;

import java.util.Arrays;

import org.theseed.basic.BaseProcessor;
import org.theseed.bin.utils.BinStatsProcessor;
import org.theseed.bin.utils.BinTestProcessor;
import org.theseed.bin.utils.QzaReportProcessor;
import org.theseed.binreports.BinReportAnalysisProcessor;


/**
 * Commands for utilities relating to CLI processing.
 *
 * binStats		determine bin composition in a PATRIC workspace directory
 * gtoRoles		get proteins from genomes based on roles
 * qzaReport	determine bin composition in a trimmed Amplicon database
 * xTotals		output the total value of each column in an xmatrix
 * roleId		add role IDs to an input file
 * roleTable	output sequences from genomes with functions
 * qzaDump		convert the samples in a QZA file to FASTA files
 * binTest		test the binning code on a directory of samples
 * binReport	analyze bin reports to produce reports or classifier data
 * md5Repair    compute the MD5 checksums missing from the BV-BRC database
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
        case "binStats" -> processor = new BinStatsProcessor();
        case "qzaReport" -> processor = new QzaReportProcessor();
        case "gtoRoles" -> processor = new GtoRolesProcessor();
        case "roleId" -> processor = new RoleIdProcessor();
        case "xTotals" -> processor = new XTotalProcessor();
        case "roleTable" -> processor = new RoleTableProcessor();
        case "qzaDump" -> processor = new QzaDumpProcessor();
        case "binTest" -> processor = new BinTestProcessor();
        case "binReport" -> processor = new BinReportAnalysisProcessor();
        case "md5Repair" -> processor = new ContigRepairProcessor();
        default -> throw new RuntimeException("Invalid command " + command);
        }
        // Process it.
        processor.parseCommand(newArgs);
        processor.run();
    }
}
