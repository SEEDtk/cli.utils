/**
 *
 */
package org.theseed.reports;

import java.io.OutputStream;

import org.theseed.genome.Feature;


/**
 * This is the base class for peg-role relationship reporting.  The main class passes in features and they
 * are written to the output in whatever format the user desires.
 *
 * @author Bruce Parrello
 *
 */
public abstract class PegReporter extends BaseReporter {



    /**
     * This interface describes the functions that must be supported by any command processor using
     * one of these reporters.
     */
    public interface IParms {

    }

    /**
     * This enum describes the various output types.
     */
    public enum Type {

        /** write DNA sequences */
        DNA {
            @Override
            public PegReporter create(IParms processor, OutputStream outStream) {
                return new DnaPegReporter(processor, outStream);
            }
        },

        /** write protein sequences */
        PROT {
            @Override
            public PegReporter create(IParms processor, OutputStream outStream) {
                return new ProteinPegReporter(processor, outStream);
            }

        };

        /**
         * @return a peg reporter for the specified output stream
         *
         * @param processor		controlling command processor
         * @param outStream		output stream to receive the report
         */
        public abstract PegReporter create(IParms processor, OutputStream outStream);

    }

    /**
     * Construct a peg reporter.
     *
     * @param output	output stream for printing
     */
    public PegReporter(OutputStream output) {
        super(output);
    }

    /**
     * Write the headers to the output stream.
     */
    public abstract void writeHeaders();

    /**
     * Write a feature to the output stream.
     *
     * @param num		function number
     * @param funId		function ID
     * @param feat		feature to write
     */
    public abstract void writeFeature(int num, String funId, Feature feat);

    /**
     * Finish the report.
     */
    public abstract void finishReport();

}
