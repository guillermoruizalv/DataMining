/*
 * Copyright (C) 2016 Enrique Cabrerizo Fernández, Guillermo Ruiz Álvarez
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package es.uam.eps.bmi.search.searching;

import es.uam.eps.bmi.search.ScoredTextDocument;
import es.uam.eps.bmi.search.TextDocument;
import es.uam.eps.bmi.search.indexing.BasicIndex;
import es.uam.eps.bmi.search.indexing.Index;
import es.uam.eps.bmi.search.indexing.Posting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

/**
 * Class implementing literal searcher.
 *
 * @author Enrique Cabrerizo Fernández
 * @author Guillermo Ruiz Álvarez
 */
public class LiteralMatchingSearcher implements Searcher {

    //Index used to search
    private Index index;

    /**
     * Creates a searcher using the given index.
     *
     * @param index Index used to create the searcher, must be loaded.
     */
    @Override
    public void build(Index index) {
        // Store the index object.
        this.index = index;
    }

    /**
     * Returns a ranking of documents sorted by the score value.
     *
     * @param query String query used to search.
     * @return a ranking of documents sorted by the decrementing score value.
     */
    @Override
    public List<ScoredTextDocument> search(String query) {
        // Separate the query string by spaces
        String[] terms = query.split(" ");

        // If no terms, return an empty string.
        if (terms.length == 0) {
            return new ArrayList<>();
        }

        // Final list of terms
        List<Posting> finalPostingList = index.getTermPostings(terms[0]);

        if (terms.length > 0) {
            terms = Arrays.copyOfRange(terms, 1, terms.length);
        }

        // Iterate the terms
        for (String term : terms) {
            finalPostingList = concatPostings(finalPostingList, index.getTermPostings(term));
        }

        // Build the list of results
        List<ScoredTextDocument> resultList = new ArrayList<>();
        int sentenceDocsCount = finalPostingList.size();
        int docsCount = index.getDocIds().size();

        // Compute the score of each document
        for (Posting posting : finalPostingList) {
            // Get document attributes
            int docID = posting.getDocID();
            double docMod = index.getDocModule(docID);

            // Get term attributes
            int sentenceFrequency = posting.getTermFrequency();

            // Compute the tf-idf
            double idf = Math.log((double) docsCount / (double) sentenceDocsCount);
            double tf = 1 + Math.log(sentenceFrequency);
            double tf_idf = tf * idf;

            // Compute the ranking
            double score = (1 / docMod) * tf_idf;
            resultList.add(new ScoredTextDocument(docID, score));
        }

        // Sort the list and return
        Collections.sort(resultList, Collections.reverseOrder());
        return resultList;
    }

    /**
     * Takes <code>previousPostingList</code> and find postings in
     * <code>currentPostingsList</code> having the same document ID and
     * consecutive positions
     *
     * The postings must be sorted by document ID, and the list of positions
     * must be sorted too.
     *
     * @param previousPostingList First list of postings.
     * @param currentPostingsList Second list of postings in which to find the
     * postings with same document ID and consecutive positions.
     * @return A list filled with the found postings.
     */
    private List<Posting> concatPostings(List<Posting> previousPostingList, List<Posting> currentPostingsList) {
        // Result list.
        List<Posting> resultPostings = new ArrayList<>();

        // Iterate the second list of postings.
        for (Posting currPosting : currentPostingsList) {
            // Search for an element with the same document ID.
            int prevPostingPos = Collections.binarySearch(previousPostingList, currPosting, (Posting t1, Posting t2) -> {
                return Integer.compare(t1.getDocID(), t2.getDocID());
            });

            // Check if found
            if (prevPostingPos >= 0) {
                Posting resultPosting = new Posting(currPosting.getTerm(), currPosting.getDocID(), new ArrayList<>());
                Posting prevPosting = previousPostingList.get(prevPostingPos);

                // Build the new posting.
                for (int currPosition : prevPosting.getTermPositions()) {
                    for (int prevPosition : prevPosting.getTermPositions()) {
                        if (currPosition == prevPosition + 1) {
                            resultPosting.addPosition(currPosition);
                        }
                        if (currPosition <= prevPosition) {
                            break;
                        }
                    }
                }

                // Add the result posting in case that the positions are consecutive.
                if (resultPosting.getTermFrequency() > 0) {
                    resultPostings.add(resultPosting);
                }
            }

        }
        return resultPostings;
    }
    
    
    /**
     * Main class for TF-IDF searcher.
     *
     * Builds a searcher and asks the user for queries, showing the TOP 5
     * results.
     *
     * @param args The following arguments are used: index_path: Path to a
     * directory containing an index.
     */
    public static void main(String[] args) {
        int TOP = 5;

        // Input control
        if (args.length != 1) {
            System.err.printf("Usage: %s index_path\n"
                    + "\tindex_path: Path to a directory containing a Lucene index.\n",
                    TFIDFSearcher.class.getSimpleName());
            return;
        }

        // Create a LuceneIndex instance.
        Index index = new BasicIndex();
        index.load(args[0]);

        // Check if the index has been correctly loaded.
        if (index.isLoaded()) {
            LiteralMatchingSearcher literalMatchingSearcher = new LiteralMatchingSearcher();
            literalMatchingSearcher.build(index);

            // Ask for queries.
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter a query (press enter to finish): ");
            String query = scanner.nextLine();
            while (!query.equals("")) {
                // Show results.
                List<ScoredTextDocument> resultList = literalMatchingSearcher.search(query);
                // If there were no errors, show the results.
                if (resultList != null) {
                    if (resultList.isEmpty()) {
                        System.out.println("No results.");
                    } else {
                        System.out.println("Showing top " + TOP + " documents:");
                        // Get sublist.
                        if (resultList.size() >= TOP) {
                            resultList = resultList.subList(0, TOP);
                        }
                        resultList.forEach((ScoredTextDocument t) -> {
                            TextDocument document = index.getDocument(t.getDocID());
                            if (document != null) {
                                System.out.println(document.getName());
                            }
                        });
                    }
                    System.out.print("Enter a query (press enter to finish): ");
                    query = scanner.nextLine();
                } else {
                    return;
                }
            }
        }
    }
}