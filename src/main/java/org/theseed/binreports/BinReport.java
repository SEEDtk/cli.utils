/**
 *
 */
package org.theseed.binreports;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.io.TabbedLineReader;
import org.theseed.utils.ParseFailureException;

/**
 * This object describes a bin report.  It takes as input an ordered collection of feature IDs, and then reads the
 * sample information from a file.  The structures allows the client to iterate through the samples and retrieve
 * the feature arrays.
 *
 * @author Bruce Parrello
 *
 */
public class BinReport implements Iterable<BinReport.Sample> {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(BinReport.class);
    /** map of sample IDs to samples */
    private Map<String, Sample> sampleMap;
    /** map of feature IDs to feature indices */
    private Map<String, Integer> featureMap;
    /** array of feature IDs, in order */
    private String[] featureList;
    /** minimum raw score for presence/absence scoring */
    private double minScore;
    /** number of ranking divisions to use for rank scoring */
    private int divisions;
    /** default minimum score */
    private static final double DEFAULT_MIN = 150.0;
    /** default number of ranking divisions */
    private static final int DEFAULT_DIVISIONS = 100;

    /**
     * This object represents a single sample.
     */
    public class Sample {

        /** ID of the sample */
        private String sampleId;
        /** label of the sample, indicating the condition of interest */
        private String label;
        /** array of feature scores */
        private double[] scores;

        /**
         * Create a new, empty sample.
         *
         * @param id			sample ID
         * @param condition		sample label
         */
        protected Sample(String id, String condition) {
            this.sampleId = id;
            this.label = condition;
            // Create an empty array of feature scores.
            this.scores = new double[BinReport.this.featureMap.size()];
            Arrays.fill(scores, 0.0);
        }

        /**
         * Add a feature score to the score array.
         *
         * @param featureId		ID of the feature (representative genome) being scored
         * @param score			score of the feature
         *
         * @throws IOException
         */
        protected void setScore(String featureId, double score) throws IOException {
            // Find the feature and store this score in its array position.
            int idx = BinReport.this.featureMap.getOrDefault(featureId, -1);
            if (idx < 0)
                throw new IOException("Feature ID " + featureId + " for sample " + this.sampleId + " is not valid.");
            this.scores[idx] += score;
        }

        /**
         * @return the sample ID
         */
        public String getSampleId() {
            return this.sampleId;
        }

        /**
         * @return the condition label
         */
        public String getLabel() {
            return this.label;
        }

        /**
         * @return the array of scores
         */
        public double[] getScores() {
            return this.scores;
        }

    }


    /**
     * Construct a new bin report for a feature ID set.
     *
     * @param featureIDs	collection of feature IDs to use, in column order
     *
     * @throws ParseFailureException
     */
    public BinReport(Collection<String> featureIDs) throws ParseFailureException {
        // We need to run through the feature IDs, building the index map.
        this.featureMap = new HashMap<String, Integer>(featureIDs.size() * 4 / 3 + 1);
        this.featureList = new String[featureIDs.size()];
        for (String featureId : featureIDs) {
            if (this.featureMap.containsKey(featureId))
                throw new ParseFailureException("Duplicate feature ID \"" + featureId + " in bin report feature ID list.");
            int idx = this.featureMap.size();
            this.featureMap.put(featureId, idx);
            this.featureList[idx] = featureId;
        }
        // Create the sample map.
        this.sampleMap = new HashMap<String, Sample>(250);
        // Set the score-normalization tuning parameters.
        this.minScore = DEFAULT_MIN;
        this.divisions = DEFAULT_DIVISIONS;
    }

    /**
     * @return the number of features for this bin report sample set
     */
    public int width() {
        return this.featureMap.size();
    }

    /**
     * @return the number of samples in this bin report
     */
    public int size() {
        return this.sampleMap.size();
    }

    /**
     * @return a list of the feature IDs, in order
     */
    public String[] getHeadings() {
        // Create a blank array.
        String[] retVal = new String[this.width()];
        // Fill in the feature IDs, each appearing in its computed position.
        for (var featEntry : this.featureMap.entrySet())
            retVal[featEntry.getValue()] = featEntry.getKey();
        return retVal;
    }

    /**
     * @return the array index of the feature with the specified ID
     */
    public int getFeatureIdx(String featureId) {
        int retVal = this.featureMap.getOrDefault(featureId, -1);
        if (retVal < 0)
            throw new IllegalArgumentException("Invalid feature ID \"" + featureId + "\" in bin report query.");
        return retVal;
    }

    @Override
    public Iterator<Sample> iterator() {
        return this.sampleMap.values().iterator();
    }

    /**
     * Import a bin report file into this report object along with the specified condition label.
     *
     * @param label		condition label to use for all samples
     * @param file		report file to process
     *
     * @throws IOException
     */
    public void processFile(String label, File file) throws IOException {
        // Open the file and do a quick verification.
        try (TabbedLineReader fileStream = new TabbedLineReader(file)) {
            log.info("Processing bin report file {} with label {}.", file, label);
            int sampleIdIdx = fileStream.findField("sample_id");
            int groupIdx = fileStream.findColumn("rep_id");
            if (groupIdx < 0)
                groupIdx = fileStream.findField("repgen_id");
            // We allow either "count" or "score" for the last column.
            int scoreIdx = fileStream.findColumn("count");
            if (scoreIdx < 0)
                scoreIdx = fileStream.findField("score");
            // Loop through the data lines.Denote we have no sample yet.
            int lineIn = 0;
            for (var line : fileStream) {
                String sampleId = line.get(sampleIdIdx);
                Sample sample = this.sampleMap.computeIfAbsent(sampleId, x -> new Sample(x, label));
                String featureId = line.get(groupIdx);
                double score = line.getDouble(scoreIdx);
                sample.setScore(featureId, score);
                lineIn++;
            }
            log.info("{} lines read from file {} for label {}.", lineIn, file, label);
        }
    }

    /**
     * @return the list of condition labels for these samples
     */
    public Set<String> getLabels() {
        Set<String> retVal = this.sampleMap.values().stream().map(x -> x.getLabel()).collect(Collectors.toSet());
        return retVal;
    }

    /**
     * @return the set of samples for a specified condition label
     *
     * @param label		condition label whose samples are desired
     */
    public Set<Sample> getSamplesForLabel(String label) {
        Set<Sample> retVal = this.sampleMap.values().stream().filter(x -> x.getLabel().contentEquals(label)).collect(Collectors.toSet());
        return retVal;
    }

    /**
     * @return the sample with the specified ID, or NULL if there is none
     *
     * @param sampleId	ID of the desired sample
     */
    public Sample getSample(String sampleId) {
        return this.sampleMap.get(sampleId);
    }

    /**
     * @return the presence/absence scoring threshold
     */
    public double getMinScore() {
        return this.minScore;
    }

    /**
     * Specify the presence/absence scoring threshold.
     *
     * @param minScore 	the new value to set
     */
    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    /**
     * @return the number of ranking divisions
     */
    public int getDivisions() {
        return this.divisions;
    }

    /**
     * Specify the number of ranking divisions to use.
     *
     * @param divisions 	the new value to set
     */
    public void setDivisions(int divisions) {
        this.divisions = divisions;
    }

    /**
     * @return the ID of the feature (column) with the specified column index
     *
     * @param idx		index of the desired column
     */
    public String getIdxFeature(int idx) {
        return this.featureList[idx];
    }

}
