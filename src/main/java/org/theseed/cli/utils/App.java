package org.theseed.cli.utils;

import java.util.Arrays;

import org.theseed.bin.utils.BinReportProcessor;
import org.theseed.bin.utils.BinTestProcessor;
import org.theseed.bin.utils.QzaReportProcessor;
import org.theseed.bin.utils.XMatrixProcessor;
import org.theseed.utils.BaseProcessor;

/**
 * Commands for utilities relating to CLI processing.
 *
 * binReport	determine bin composition in a PATRIC workspace directory
 * xMatrix		convert bin reports into a classification matrix
 * gtoRoles		get proteins from genomes based on roles
 * qzaReport	determine bin composition in trimmed Amplicon database
 * xTotals		output the total value of each column in an xmatrix
 * roleId		add role IDs to an input file
 * roleTable	output sequences from genomes with functions
 * qzaDump		convert the samples in a QZA file to FASTA files
 * binTest		test the binning code on a directory of samples
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
        case "binReport" :
            processor = new BinReportProcessor();
            break;
        case "qzaReport" :
            processor = new QzaReportProcessor();
            break;
        case "xmatrix" :
            processor = new XMatrixProcessor();
            break;
        case "gtoRoles" :
            processor = new GtoRolesProcessor();
            break;
        case "roleId" :
            processor = new RoleIdProcessor();
            break;
        case "xTotals" :
            processor = new XTotalProcessor();
            break;
        case "roleTable" :
            processor = new RoleTableProcessor();
            break;
        case "qzaDump" :
            processor = new QzaDumpProcessor();
            break;
        case "binTest" :
            processor = new BinTestProcessor();
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
