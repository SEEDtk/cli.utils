/**
 *
 */
package org.theseed.p3api;

import java.io.IOException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.cliftonlabs.json_simple.JsonKey;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.github.cliftonlabs.json_simple.JsonArray;

/**
 * This class is used to request PDB IDs for protein sequences.  It is entirely possible for none to be found.  The basic
 * strategy, however, is to do a PDB ID search for all the sequences and return the best match for each.
 *
 * Each sequence is processed by a single query.  A throttling parameter indicates a number of of milliseconds to pause between
 * queries.  This is an acknowledgement of the fact there is simply no way to associate a query sequence with a result if they sequences
 * are passed as a group.
 *
 * @author Bruce Parrello
 *
 */
public class PdbFinder {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(PdbFinder.class);
    /** e-value cutoff */
    private double maxEValue;
    /** minimum fraction identity */
    private double minIdentity;
    /** throttle pause, in milliseconds (0 for none) */
    private int throttlePause;
    /** URL for PDB search API */
    private static final String PDB_SEARCH_URL = "https://search.rcsb.org/rcsbsearch/v1/query";
    /** maximum retries */
    private static final int MAX_TRIES = 3;

    /**
     * This enum defines the keys used and their default values for the response object.
     */
    public static enum ResultKeys implements JsonKey {
        // GTO fields
        RESULT_SET(new JsonArray()),
        IDENTIFIER(null),
        SCORE(0.0);

        private final Object m_value;

        ResultKeys(final Object value) {
            this.m_value = value;
        }

        /** This is the string used as a key in the incoming JsonObject map.
         */
        @Override
        public String getKey() {
            return this.name().toLowerCase();
        }

        /** This is the default value used when the key is not found.
         */
        @Override
        public Object getValue() {
            return this.m_value;
        }

    }

    /**
     * Create the PDB finder.
     */
    public PdbFinder() {
        this.maxEValue = 1e-20;
        this.minIdentity = 0.40;
        this.throttlePause = 0;
    }

    /**
     * Find the PDB ID for a sequence.
     *
     * @param seq		protein sequence to find
     *
     * @return the best PDB ID found, or NULL if no match was found
     *
     * @throws InterruptedException
     * @throws IOException
     * @throws ClientProtocolException
     */
    public String findPDB(String sequence) throws InterruptedException, ClientProtocolException, IOException {
        String retVal = null;
        // Format the query.
        JsonObject qParms = new JsonObject().putChain("evalue_cutoff", this.maxEValue).putChain("target", "pdb_protein_sequence")
                .putChain("identity_cutoff", this.minIdentity).putChain("value", sequence);
        JsonObject query = new JsonObject().putChain("type", "terminal").putChain("service", "sequence").putChain("parameters", qParms);
        JsonObject request = new JsonObject().putChain("return_type", "polymer_entity").putChain("query", query);
        // Perform the throttling.
        if (this.throttlePause > 0)
            Thread.sleep(this.throttlePause);
        // Form a request to send to the PDB website.
        String jsonRequest = request.toJson();
        Request pdbRequest = Request.Post(PDB_SEARCH_URL).addHeader("Accept", "application/json")
                .bodyString(jsonRequest, ContentType.APPLICATION_JSON);
        // Now we can submit the request.
        int tries = 0;
        try {
            tries++;
            Response resp = pdbRequest.execute();
            // Process the response.
            HttpResponse rawResponse = resp.returnResponse();
            int statusCode = rawResponse.getStatusLine().getStatusCode();
            if (statusCode < 400) {
                HttpEntity respEntity = rawResponse.getEntity();
                // If there was nothing even close, the PDB server returns NULL instead of an empty list,
                // so we must check here.
                if (respEntity != null) {
                    // Here we have a response to parse.  We search for the one with the highest score.
                    String jsonString = EntityUtils.toString(rawResponse.getEntity());
                    JsonObject responseObject = (JsonObject) Jsoner.deserialize(jsonString);
                    retVal = this.processResponse(responseObject);
                }
            } else if (tries < MAX_TRIES) {
                log.debug("Retrying PDB request after error code {}.", statusCode);
            } else {
                throw new RuntimeException("PDB request failed with error " + statusCode + " " +
                        rawResponse.getStatusLine().getReasonPhrase());
            }
        } catch (IOException e) {
            // This is usually a timeout error.
            if (tries >= MAX_TRIES)
                throw new RuntimeException("HTTP error in PDB request: " + e.getMessage());
            else {
                tries++;
                log.debug("Retrying PDB request after " + e.getMessage());
            }
        } catch (Exception e) {
            throw new RuntimeException("Error parsing JSON for PDB query: " + e.getMessage());
        }
        return retVal;
    }

    /**
     * Process a query response to extract the best PDB ID.
     *
     * @param responseObject	response object returned from the PDB server
     *
     * @return the PDB ID, or NULL if none was found
     */
    private String processResponse(JsonObject responseObject) {
        JsonArray resultSet = responseObject.getCollectionOrDefault(ResultKeys.RESULT_SET);
        // Find the best result.
        String retVal = null;
        double bestScore = 0.0;
        for (Object result : resultSet) {
            JsonObject jResult = (JsonObject) result;
            double score = jResult.getDoubleOrDefault(ResultKeys.SCORE);
            if (score > bestScore) {
                retVal = jResult.getStringOrDefault(ResultKeys.IDENTIFIER);
                bestScore = score;
            }
        }
        // Trim off the entity indicator.
        if (retVal != null)
            retVal = StringUtils.substringBefore(retVal, "_");
        return retVal;
    }

    /**
     * Specify the maximum e-value.
     * @param maxEValue the maxEValue to set
     */
    public void setMaxEValue(double maxEValue) {
        this.maxEValue = maxEValue;
    }

    /**
     * Specify the minimum acceptable identity.
     *
     * @param minIdentity the minIdentity to set
     */
    public void setMinIdentity(double minIdentity) {
        this.minIdentity = minIdentity;
    }

    /**
     * Specify the throttle pause in milliseconds.
     *
     * @param throttlePause the throttlePause to set
     */
    public void setThrottlePause(int throttlePause) {
        this.throttlePause = throttlePause;
    }


}
