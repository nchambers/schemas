package nate.reading;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nate.util.HandleParameters;
import nate.util.SortableObject;
import nate.util.Util;


/**
 * This is a class that does not load data files, databases, or all the machinery used in classifications.
 * It simply reads the output logs from running LabelDocument, and pulls out the labels assigned to each
 * documents.
 * INPUT: output-clusters, output-topics
 * 
 * After reading the cluster and topic probabilities for each document, this class can run various searches
 * over the parameters (e.g. how many topics to guess per document) to find the best way to interpolate
 * the two models.
 * 
 * -muckey <templates-file>
 * File of MUC templates, the gold templates.
 */
public class Evaluate {
  MUCKeyReader _answerKey;
  List<String> _clusterTypes = new ArrayList<String>();
  List<Integer> _clusterIDs = new ArrayList<Integer>();
  List<String> _topicTypes = new ArrayList<String>();
  List<Integer> _topicIDs = new ArrayList<Integer>();
  List<ReadingCluster> _clusters = new ArrayList<ReadingCluster>();
  List<ReadingCluster> _topics = new ArrayList<ReadingCluster>();
  Map<Integer,Integer> _clusterIDToTopic = new HashMap<Integer, Integer>();

  Map<String, List<ReadingCluster>> _clusterDocProbs;
  Map<String, List<ReadingCluster>> _clusterSentProbs;
  Map<String, List<ReadingCluster>> _clusterSigSentences;
  Map<String, List<ReadingCluster>> _topicDocProbs;
  Map<String, List<ReadingCluster>> _topicSentProbs;
  Map<String, List<ReadingCluster>> _topicSigSentences;

  public Evaluate(String args[]) {
    HandleParameters params = new HandleParameters(args);
    _answerKey = new MUCKeyReader(params.get("-muckey"));

    _clusterDocProbs     = new HashMap<String, List<ReadingCluster>>();
    _clusterSentProbs    = new HashMap<String, List<ReadingCluster>>();
    _clusterSigSentences = new HashMap<String, List<ReadingCluster>>();
    _topicDocProbs       = new HashMap<String, List<ReadingCluster>>();
    _topicSentProbs      = new HashMap<String, List<ReadingCluster>>();
    _topicSigSentences   = new HashMap<String, List<ReadingCluster>>();
  }

  private List<ReadingCluster> sort(List<ReadingCluster> items) {
    // Sort the list of clusters.
    ReadingCluster[] arr = new ReadingCluster[items.size()];
    arr = items.toArray(arr);
    Arrays.sort(arr);

    // Rebuild a List object.
    List<ReadingCluster> sorted = new ArrayList<ReadingCluster>();
    for( ReadingCluster cluster : arr ) sorted.add(cluster);
    return sorted;
  }

  private List<ReadingCluster> sortAndTrimCutoff(List<ReadingCluster> items, double cutoff) {
    // Sort the list.
    List<ReadingCluster> sorted = sort(items);

    // Return the trimmed list.
    return trimCutoff(sorted, cutoff);
  }

  private List<ReadingCluster> trimCutoff(List<ReadingCluster> items, double cutoff) {
    if( items == null ) return null;

    List<ReadingCluster> newlist = new ArrayList<ReadingCluster>();
    for( ReadingCluster cluster : items ) {
      if( cluster.score() >= cutoff )
        newlist.add(cluster);
    }
    return newlist;
  }

  private List<ReadingCluster> sortAndTrimToFirstN(List<ReadingCluster> items, int n) {
    // Sort the list.
    List<ReadingCluster> sorted = sort(items);

    // Return the trimmed list.
    return trimToFirstN(sorted, n);
  }

  private List<ReadingCluster> trimToFirstN(List<ReadingCluster> items, int n) {
    if( items == null ) return null;

    List<ReadingCluster> newlist = new ArrayList<ReadingCluster>();
    for( int i = 0; i < n && i < items.size(); i++ ) {
      newlist.add(items.get(i));
    }
    return newlist;
  }

  /**
   * Removes from the second list, any cluster that is in the first list.
   * @param main The unchanged list.
   * @param target The list to remove duplicates from.
   * @return A new list that is the target, but with the main's clusters removed from it.
   */
  private List<ReadingCluster> removeDuplicates(List<ReadingCluster> main, List<ReadingCluster> target) {
    Set<Integer> skipids = new HashSet<Integer>();
    for( ReadingCluster cluster : main )
      skipids.add(cluster.getID());

    List<ReadingCluster> fixed = new ArrayList<ReadingCluster>();
    for( ReadingCluster cluster : target ) {
      if( !skipids.contains(cluster.getID()) ) {
        fixed.add(cluster);
        skipids.add(cluster.getID());
      }
    }

    return fixed;
  }

  /**
   * Removes any duplicate clusters in a list, saving the one with the highest score.
   * It also maintains the order of the list, only removing later duplicates, and 
   * updating the first occurrence with the later one's score if the later one has
   * a higher score.
   */
  private List<ReadingCluster> removeDuplicates(List<ReadingCluster> thelist) {
    List<ReadingCluster> fixed = new ArrayList<ReadingCluster>();
    Map<Integer,ReadingCluster> added = new HashMap<Integer, ReadingCluster>();

    for( ReadingCluster cluster : thelist ) {
      int id = cluster.getID();
      // If this is the first cluster instance, keep it.
      if( !added.containsKey(id) ) {
        added.put(id, cluster);
        fixed.add(cluster);
      }
      // If we already added this cluster, but this instance is a higher score.
      else if( cluster.score() > added.get(id).score() ) {
        added.get(id).setClusterScore(cluster.score());
      }
    }

    return fixed;
  }

  private void removeDuplicates(Map<String,List<ReadingCluster>> thelists) {
    for( Map.Entry<String, List<ReadingCluster>> entry : thelists.entrySet() ) {
      //      System.out.println("story " + entry.getKey());
      //      for( ReadingCluster cluster : entry.getValue() ) System.out.println("  pree " + cluster);
      List<ReadingCluster> trimmed = removeDuplicates(entry.getValue());
      //      for( ReadingCluster cluster : trimmed ) System.out.println("  trim " + cluster);
      entry.setValue(trimmed);
    }
  }


  private void mapClustersToTopics() {
    _clusterIDToTopic = mapClustersToTopics(_clusters, _topics);

    //    // reverse it
    //    Map<Integer,Integer> topicToCluster = mapClustersToTopics(_topics, _clusters);
    //    // find the topics not yet aligned
    //    Set<Integer> alignedTopicIDs = new HashSet<Integer>();
    //    for( Integer topicID : _clusterIDToTopic.values() ) {
    //      alignedTopicIDs.add(topicID);
    //    }
    //    for( ReadingCluster topic : _topics ) {
    //      int topicID = topic.getID();
    //      // If this topic wasn't aligned originally.
    //      if( !alignedTopicIDs.contains(topic.getID()) ) {
    //        System.out.println("found unaligned: " + topic.getID());
    //        if( topicToCluster.get(topicID) != -1 ) {          
    //          _clusterIDToTopic.put(topicToCluster.get(topicID), topicID);
    //          System.out.println("reverse mapping found " + topicToCluster.get(topicID) + " -> " + topicID);
    //        }
    //      }
    //    }
  }

  /**
   * Finds the one topic that matches best with the cluster, given that the match score is
   * greater than the best current topicID match score.  These best scores are in the 
   * given tokenIDScores map.
   * @param cluster The cluster to map.
   * @param topics The list of possible topics to align with.
   * @param topicIDScores The best scores for each topic that is already aligned.
   * @return A score object with the best topicID and the aligned score.
   */
  private SortableObject mapClusterToTopic(ReadingCluster cluster, List<ReadingCluster> topics, Map<Integer,Double> topicIDScores) {
    double best = 0.0;
    int bestTopicID = -1;

    for( ReadingCluster topic : topics ) {
      double score = 0.0f;
      for( String token : cluster.getTokens() )
        score += cluster.getTokenScore(token) * topic.getTokenScore(token);

      Double currentTopicScore = topicIDScores.get(topic.getID());
      if( currentTopicScore == null ) currentTopicScore = 0.0;

      if( score > best && score > currentTopicScore ) {
        best = score;
        bestTopicID = topic.getID();
      }
    }

    return new SortableObject(best, bestTopicID);
  }

  /**
   * Map from each cluster ID to a single topic ID based on token similarity.
   * This is a one to one mapping.
   * If two clusters have a best topic that is the same, we take the one with the highest
   * score and assign the 2nd best topic to the other (or 3rd, 4th, however long it takes).
   * Not all clusters find a matching topic (they are mapped to -1).
   * Not all topics are mapped to either.
   */
  private Map<Integer,Integer> mapClustersToTopics(List<ReadingCluster> clusters, List<ReadingCluster> topics) {
    Set<Integer> alignedTopicIDs = new HashSet<Integer>();
    Map<Integer,Integer> clusterIDToTopic = new HashMap<Integer, Integer>();
    Map<Integer,Integer> topicIDToCluster = new HashMap<Integer, Integer>();
    Map<Integer,Double> bestTopicIDScores = new HashMap<Integer, Double>();

    for( ReadingCluster cluster : clusters ) {
      int clusterID = cluster.getID();
      SortableObject scored = mapClusterToTopic(cluster, topics, bestTopicIDScores);
      int topicID = (Integer)scored.key();
      double score = scored.score();
      //      System.out.println("mapscore\t" + clusterID + " -> " + topicID + " at " + score);


      // Fix old mappings if this overrules a previous mapping.
      while( topicID != -1 && bestTopicIDScores.containsKey(topicID) ) {
        bestTopicIDScores.put(topicID, score);
        int formerClusterID = topicIDToCluster.get(topicID);

        // Set the new mapping.
        clusterIDToTopic.put(clusterID, topicID);
        topicIDToCluster.put(topicID, clusterID);
        alignedTopicIDs.add(topicID);
        //        System.out.println("mapped2\t\t" + clusterID + " -> " + topicID);

        // Fix the old mapping.
        for( ReadingCluster clusty : clusters ) {
          if( clusty.getID() == formerClusterID ) {
            SortableObject scored2 = mapClusterToTopic(clusty, topics, bestTopicIDScores);
            topicID = (Integer)scored2.key();
            score = scored2.score();
            clusterID = formerClusterID;
            //            System.out.println("mappedit\t" + clusterID + " -> " + topicID + " at " + score);
            break;
          }
        }
      }

      bestTopicIDScores.put(topicID, score);
      clusterIDToTopic.put(clusterID, topicID);
      topicIDToCluster.put(topicID, clusterID);
      alignedTopicIDs.add(topicID);

      //      System.out.println("mapped\t\t" + clusterID + " -> " + topicID);
    }

    //      System.out.println("  **" + cluster);
    //      System.out.println("  **" + bestTopic);

    // DEBUG: list the topics not pointed to
    for( ReadingCluster topic : topics ) {
      if( !alignedTopicIDs.contains(topic.getID()) )
        System.out.println("topic not aligned: " + topic.getID());
    }
    return clusterIDToTopic;
  }

  /**
   * Interpolates the scores of clusters with those of topics for a single document:
   *   cluster * lambda + topic * (1-lambda)
   * @return New cluster objects that are the original clusters (not topics), but with interpolated scores.
   */
  private List<ReadingCluster> interpolate(List<ReadingCluster> clusterProbs, List<ReadingCluster> topicProbs, double lambda) {
    List<ReadingCluster> merged = new ArrayList<ReadingCluster>();
    Set<Integer> addedTopicIDs = new HashSet<Integer>();

    // Quick lookup sheet.
    Map<Integer, ReadingCluster> topicLookup = new HashMap<Integer, ReadingCluster>();
    for( ReadingCluster topic : topicProbs ) {
      topicLookup.put(topic.getID(), topic);
    }

    // Go through the first list.
    for( ReadingCluster cluster : clusterProbs ) {
      boolean foundmatch = false;

      // Lookup the mapping from the clusters to topics.
      Integer topicID = _clusterIDToTopic.get(cluster.getID());
      // This should never be null...
      if( topicID != null ) {
        ReadingCluster topic = topicLookup.get(topicID);
        if( topic != null ) {
          //          System.out.println("Interpolating: " + cluster + "\n   with " + topic);

          // Calculate the score.
          double score = lambda * cluster.score();
          if( topic != null )    
            score += (1.0-lambda) * topic.score();
          merged.add(new ReadingCluster(score, cluster));
          addedTopicIDs.add(topicID);
          foundmatch = true;
        }
      }

      // If the matching topic wasn't scored on this document..
      if( !foundmatch ) {
        double score = lambda * cluster.score();
        merged.add(new ReadingCluster(score, cluster));
        //        System.out.println("Interpolating cluster: " + cluster);
      }
    }

    // Go through the remaining second list items that weren't in the first.
    for( ReadingCluster topic : topicProbs ) {
      if( !addedTopicIDs.contains(topic.getID()) ) {
        double score = (1.0-lambda) * topic.score();
        merged.add(new ReadingCluster(score, topic));
        //        System.out.println("Interpolating topic: " + topic);
      }
    }
    //
    //    System.out.println("Done.");
    //    for( ReadingCluster cluster : merged ) {
    //      System.out.println("*merged: " + cluster);
    //    }

    return merged;
  }

  /**
   * Creates a new list that is the intersection of the two lists, based on their IDs.
   */
  private List<ReadingCluster> intersectLists(List<ReadingCluster> first, List<ReadingCluster> second) {
    Set<Integer> ids = new HashSet<Integer>();
    for( ReadingCluster cluster : first )
      ids.add(cluster.getID());

    List<ReadingCluster> merged = new ArrayList<ReadingCluster>();
    for( ReadingCluster cluster : first )
      merged.add(cluster);

    for( ReadingCluster cluster : second ) {
      if( !ids.contains(cluster.getID()) )
        merged.add(cluster);
    }
    return merged;
  }

  private boolean clustersContain(int id, List<ReadingCluster> clusters) {
    for( ReadingCluster cluster : clusters ) {
      if( cluster.getID() == id )
        return true;
    }
    return false;      
  }

  private boolean templatesContain(String type, List<Template> templates) {
    List<Template> found = EvaluateTemplates.templatesContain(type, templates);
    if( found != null )
      return true;
    else
      return false;
  }

  public void gauntlet() {
    gauntlet(_clusterTypes, _clusterIDs, _clusterDocProbs, _clusterSentProbs, _clusterSigSentences);
//    gauntlet(_topicTypes, _topicIDs, _topicDocProbs, _topicSentProbs, _topicSigSentences);
  }

  /**
   * Pass in the top document clusters, and the top sentence-specific clusters.
   * This function tries many combos of including varying amounts of each in the guess list,
   * and outputs the precision/recall of each.
   * @param docProbs Ordered list of the top document clusters.
   * @param sentProbs Ordered list of the top sentence clusters.
   */
  public void gauntlet(List<String> types, List<Integer> typeids, 
      Map<String, List<ReadingCluster>> docProbs, Map<String, List<ReadingCluster>> sentProbs, Map<String, List<ReadingCluster>> sigProbs) {
    // Get the story names in order.
    Set<String> stories = docProbs.keySet();
    String[] sortedStories = new String[stories.size()];
    sortedStories = stories.toArray(sortedStories);
    Arrays.sort(sortedStories);

    // Run the gauntlet.
    for( int numTopDocs = 0; numTopDocs <= 10; numTopDocs += 2 ) {
      //for( double docCutoffScore = 0.01f; docCutoffScore <= 0.1f; docCutoffScore += 0.01f ) {
      //for( double docCutoffScore = 0.004f; docCutoffScore <= 0.05f; docCutoffScore += 0.002f ) {
      for( int numTopSents = 0; numTopSents <= 10; numTopSents += 2 ) {
        //      for( double sentCutoffScore = 0.2f; sentCutoffScore <= 2.0f; sentCutoffScore += 0.2f ) {
        for( int numTopSigs = 0; numTopSigs <= 4; numTopSigs += 2 ) {

          // Evaluation counts.
          Map<Integer,Map<String,Integer>> truePositives = new HashMap<Integer, Map<String, Integer>>();
          Map<Integer,Map<String,Integer>> falsePositives = new HashMap<Integer, Map<String, Integer>>();
          Map<String,Integer> totals = new HashMap<String, Integer>();

          for( String story : sortedStories ) {
//            System.out.println("Story " + story);
            List<Template> storyTemplates = _answerKey.getTemplates(story);
//            if( storyTemplates != null )
//              for( MUCTemplate muc : storyTemplates ) System.out.println("  gold " + muc.get(MUCTemplate.INCIDENT_TYPE));

            // Our guesses.  
            List<ReadingCluster> topClusterDocs = sortAndTrimToFirstN(docProbs.get(story), numTopDocs);
            //List<ReadingCluster> topClusterDocs = sortAndTrimCutoff(docProbs.get(story), docCutoffScore);
            List<ReadingCluster> topClusterSents = removeDuplicates(topClusterDocs, sentProbs.get(story));
            topClusterSents = sortAndTrimToFirstN(topClusterSents, numTopSents);
            //   topClusterSents = sortAndTrimCutoff(topClusterSents, sentCutoffScore);
            topClusterDocs = intersectLists(topClusterDocs, topClusterSents);

            List<ReadingCluster> topClusterSigs = removeDuplicates(topClusterDocs, sigProbs.get(story));
            topClusterSigs = sortAndTrimToFirstN(topClusterSigs, numTopSigs);
            topClusterDocs = intersectLists(topClusterDocs, topClusterSigs);

            //          incrementEvaluationCounts(types, typeids, storyTemplates, topClusterDocs, truePositives, falsePositives, totals);
            incrementEvaluationCounts(storyTemplates, topClusterDocs, truePositives, falsePositives, totals);
          }

          System.out.println("**EVAL** docs=" + numTopDocs + " sents=" + numTopSents + " sigs=" + numTopSigs);
          //        LabelDocument.printEvaluationResults(truePositives, falsePositives, totals);
          printEvaluationResults(truePositives, falsePositives, totals);    

        }
      }
    }
  }

  public void interpolationGauntlet() {
    interpolationGauntlet(_clusterTypes, _clusterIDs, _topicIDs, _clusterDocProbs, _clusterSentProbs, _topicDocProbs, _topicSentProbs);
  }

  /**
   * This function interpolates the scores of topics with those of clusters.
   * It is able to do this because clusters have been mapped one-to-one to topics a priori. We just
   * lookup the cluster and corresponding topic scores, then sum them together with a lambda discount.
   * 
   * @param types The global list of template types.
   * @param clustertypeids The ids of clusters corresponding to the "types".
   * @param topictypeids The ids of clusters corresponding to the "types".
   * @param clusterDocProbs The map of stories to top doc-scored clusters.
   * @param clusterSentProbs The map of stories to top sentence-scored clusters.
   * @param topicDocProbs The map of topics to top doc-scored clusters.
   * @param topicSentProbs The map of topics to top sentence-scored clusters.
   */
  public void interpolationGauntlet(List<String> types, List<Integer> clustertypeids, List<Integer> topictypeids, 
      Map<String, List<ReadingCluster>> clusterDocProbs, Map<String, List<ReadingCluster>> clusterSentProbs,
      Map<String, List<ReadingCluster>> topicDocProbs, Map<String, List<ReadingCluster>> topicSentProbs) {
    // Get the story names in order.
    Set<String> stories = _clusterDocProbs.keySet();
    String[] sortedStories = new String[stories.size()];
    sortedStories = stories.toArray(sortedStories);
    Arrays.sort(sortedStories);

    // Run the gauntlet.
    double interp = 0.3f;
    int numTopSents = 0;
    for( interp = 0.0f; interp <= 1.04f; interp += 0.05f ) {
      for( int numTopDocs = 4; numTopDocs < 11; numTopDocs += 2 ) {
        for( numTopSents = 0; numTopSents <= 10; numTopSents += 2 ) {

          // Evaluation counts.
          //        Map<String,Integer> truePositives = new HashMap<String, Integer>();
          //        Map<String,Integer> falsePositives = new HashMap<String, Integer>();
          //        Map<String,Integer> totals = new HashMap<String, Integer>();
          // Evaluation counts.
          Map<Integer,Map<String,Integer>> truePositives = new HashMap<Integer, Map<String, Integer>>();
          Map<Integer,Map<String,Integer>> falsePositives = new HashMap<Integer, Map<String, Integer>>();
          Map<String,Integer> totals = new HashMap<String, Integer>();

          for( String story : sortedStories ) {
            //         System.out.println("Story " + story);
            List<Template> storyTemplates = _answerKey.getTemplates(story);

            // Our guesses.
            List<ReadingCluster> interped = interpolate(clusterDocProbs.get(story), topicDocProbs.get(story), interp);
            List<ReadingCluster> topDocs = sortAndTrimToFirstN(interped, numTopDocs);

            // Cluster Sentences.
            if( numTopSents > 0 ) {
              List<ReadingCluster> topClusterSents = removeDuplicates(topDocs, clusterSentProbs.get(story));
              topClusterSents = sortAndTrimToFirstN(topClusterSents, numTopSents/2);
              topDocs = intersectLists(topDocs, topClusterSents);

              // Topic Sentences.
              List<ReadingCluster> topTopicSents = removeDuplicates(topDocs, topicSentProbs.get(story));
              topTopicSents = sortAndTrimToFirstN(topTopicSents, numTopSents/2);
              topDocs = intersectLists(topDocs, topTopicSents);
            }

            //         incrementEvaluationCounts(types, clustertypeids, storyTemplates, topDocs, truePositives, falsePositives, totals);
            incrementEvaluationCounts(storyTemplates, topDocs, truePositives, falsePositives, totals);
          }

          System.out.printf("**EVAL** interp=%.2f docs=%d sents=%d\n", interp, numTopDocs, numTopSents);
          printEvaluationResults(truePositives, falsePositives, totals);    
          //       LabelDocument.printEvaluationResults(truePositives, falsePositives, totals);
          //break;
        }
      }
    }
  }

  /**
   * Counts the true/false positives over all topics for all template types.
   */
  public void incrementEvaluationCounts(List<Template> storyTemplates, List<ReadingCluster> topDocs,
      Map<Integer,Map<String,Integer>> truePositives, Map<Integer,Map<String,Integer>> falsePositives, Map<String,Integer> totals) {

    //    System.out.println("top increment ");

    // Loop over each template for this document.
    if( storyTemplates != null ) {
      Set<String> seenTypes = new HashSet<String>();
      for( Template goldTemplate : storyTemplates ) {
        String goldType = goldTemplate.get(MUCTemplate.INCIDENT_TYPE);
        if( !seenTypes.contains(goldType) ) {
          seenTypes.add(goldType);

          // Increment the true positives.
          for( ReadingCluster guess : topDocs ) {
            //            System.out.println("  guess " + guess);
            Map<String,Integer> trues = truePositives.get(guess.getID());
            if( trues == null ) {
              trues = new HashMap<String, Integer>();
              truePositives.put(guess.getID(), trues);
            }
//            System.out.println("incrementing true " + goldType + " with cluster id " + guess.getID());
            Util.incrementCount(trues, goldType, 1);
          }
          // Increment total MUC type counts.
          //          System.out.println("incrementing total " + goldType);
          Util.incrementCount(totals, goldType, 1);
        }
      }
    }

    // Increment the false positives.
    for( String type : MUCTemplate.MUC_TYPES ) {
      if( !templatesContain(type, storyTemplates) ) {
        for( ReadingCluster guess : topDocs ) {
          Map<String,Integer> nottrues = falsePositives.get(guess.getID());
          if( nottrues == null ) {
            nottrues = new HashMap<String, Integer>();
            falsePositives.put(guess.getID(), nottrues);
          }
          //          System.out.println("incrementing false " + type + " with cluster id " + guess.getID());
          Util.incrementCount(nottrues, type, 1);
        }
      }
    }
  }

  public void incrementEvaluationCounts(List<String> types, List<Integer> typeids, 
      List<Template> storyTemplates, List<ReadingCluster> topDocs,
      Map<String,Integer> truePositives, Map<String,Integer> falsePositives, Map<String,Integer> totals) {

    int i = 0;
    for( String type : types ) {
      int typeid = typeids.get(i++);
      boolean matchedCluster  = clustersContain(typeid, topDocs);
      boolean matchedTemplate = templatesContain(type, storyTemplates);

      // Our frame type matches the gold template frame type (e.g. kidnap).
      if( matchedCluster && matchedTemplate ) {
        Util.incrementCount(truePositives, type, 1);
        //        System.out.println("EVAL: " + type + " match!");
      }
      // Our frame is a different type than the gold template.
      else if( matchedCluster && !matchedTemplate ) {
        Util.incrementCount(falsePositives, type, 1);
        //        System.out.println("EVAL: " + type + " false match!");
      }
      if( matchedTemplate ) {
        Util.incrementCount(totals, type, 1);
        //        if( !matchedCluster ) System.out.println("EVAL: " + type + " missed!");
      }
    }
  }

  public static void printEvaluationResults(Map<Integer,Map<String,Integer>> truePositives, Map<Integer,Map<String,Integer>> falsePositives, Map<String,Integer> totals) {
    for( String type : MUCTemplate.MUC_TYPES ) {
      Integer total = totals.get(type);
      if( total == null ) {
        System.out.println("ERROR in printEvaluationResults: total for " + type + " is null");
        return;
      }

      int bestid = -1;
      float bestf1 = -1.0f;
      int bestCorrect = 0;
      int bestFalsePos = 0;
      float bestPrecision = 0.0f;
      float bestRecall = 0.0f;

      for( Integer id : truePositives.keySet() ) {
        Map<String,Integer> trues = truePositives.get(id); 
        Integer correct = null;
        if( trues != null ) correct = trues.get(type);
        if( correct == null ) correct = 0;

        Integer falsePos = null;
        Map<String,Integer> nottrues = falsePositives.get(id);
        if( nottrues != null ) falsePos = nottrues.get(type);
        if( falsePos == null ) falsePos = 0;

        int guessed = correct + falsePos;
        float precision = 0.0f;
        if( guessed > 0 ) precision = (float)correct/(float)guessed;
        float recall = (float)correct/(float)total;
        float f1 = 2.0f * precision * recall / (precision + recall);

        if( f1 > bestf1 ) {
          bestf1 = f1;
          bestid = id;
          bestCorrect = correct;
          bestFalsePos = falsePos;
          bestPrecision = precision;
          bestRecall = recall;
        }
      }

      if( type.startsWith("KIDNAP") ) type = "KIDNAP";
      if( type.startsWith("FORCED") ) type = "WORK";
      System.out.printf("EVAL %s:\t%d\tP: %d/%d = %.3f\tR: %d/%d = %.3f\t F1: %.3f\n", type, bestid,
          bestCorrect, (bestCorrect+bestFalsePos), bestPrecision,
          bestCorrect, total, bestRecall, bestf1);
    }
  }


  public void readClustersFromFile(String filename) {
    fromFile(filename, _clusterDocProbs, _clusterSentProbs, _clusterSigSentences, _clusterTypes, _clusterIDs);
    removeDuplicates(_clusterSentProbs);
  }

  public void readTopicsFromFile(String filename) {
    fromFile(filename, _topicDocProbs, _topicSentProbs, _topicSigSentences, _topicTypes, _topicIDs);
    removeDuplicates(_topicSentProbs);
  }

  /**
   * Reads the debug output of a run and looks for lines that mention the
   * cluster/topic guesses: "EVAL: KIDNAP match"
   * Collects all of these guesses, and returns a list of booleans that indicate
   * whether or not we guessed KIDNAP (or whatever evalType is passed in). 
   * @param filename Debug output from a run.
   * @param allDocProbs The map this function fills, story IDs to the list of top cluster selections for the document.
   * @param allSentProbs The map this function fills, story IDs to the list of top cluster selections for sentences.
   */
  public void fromFile(String filename, Map<String, List<ReadingCluster>> allDocProbs, Map<String, List<ReadingCluster>> allSentProbs, 
      Map<String, List<ReadingCluster>> allSigSentences, List<String> mucTypes, List<Integer> idmap) {
    System.out.println("FromFile " + filename);

    // Open the file.
    BufferedReader in = null;
    try {
      in = new BufferedReader(new FileReader(filename));
    } catch( Exception ex ) { 
      System.out.println("Error opening file: " + filename);
      System.exit(-1);
    }

    // Read the file.
    String line = null;
    List<ReadingCluster> topDocProbs = null;
    List<ReadingCluster> topSentProbs = null;
    List<ReadingCluster> sigSentences = null;
    try {
      while( (line = in.readLine()) != null ) {
        //System.out.println("line: " + line);
        // e.g. "43: (43/??) DEV-MUC3-0043"
        if( line.matches("^\\d+: .* DEV-.*") ) {
          int start = line.indexOf("DEV-");
          String currentStory = line.substring(start, start+13);
          topDocProbs = new ArrayList<ReadingCluster>();
          topSentProbs = new ArrayList<ReadingCluster>();
          sigSentences = new ArrayList<ReadingCluster>();
          allDocProbs.put(currentStory, topDocProbs);
          allSentProbs.put(currentStory, topSentProbs);
          allSigSentences.put(currentStory, sigSentences);
        }

        // Top 0   112       0.0251
        else if( line.matches("^Top \\d+\\s+\\d+\\s+.*") ) {
          String parts[] = line.split("\\s+");
          ReadingCluster item = new ReadingCluster();
          item.setClusterScore(Double.parseDouble(parts[3]));
          item.setID(Integer.parseInt(parts[2]));
          topDocProbs.add(item);
        }

        // SigSent 0.232 24
        // SigSent2 0.623 7
        // These are unordered in the output file, we need to sort.
        else if( line.matches("^SigSent2? .*") ) {
          String parts[] = line.split("\\s+");
          ReadingCluster item = new ReadingCluster();
          item.setClusterScore(Double.parseDouble(parts[1]));
          item.setID(Integer.parseInt(parts[2]));
          topSentProbs.add(item);
        }

        // Cluster KIDNAP = 24
        else if( line.matches("^Cluster [A-Za-z]+ = \\d+.*") ) {
          String[] parts = line.split("\\s+");
          mucTypes.add(parts[1]);
          idmap.add(Integer.valueOf(parts[3]));
          System.out.println("Cluster Type Set " + parts[1] + " to " + Integer.valueOf(parts[3]));
        }

        // Topic KIDNAP = 165
        else if( line.matches("^Topic [A-Za-z]+ = \\d+.*") ) {
          String[] parts = line.split("\\s+");
          mucTypes.add(parts[1]);
          idmap.add(Integer.valueOf(parts[3]));
          System.out.println("Topic Type Set " + parts[1] + " to " + Integer.valueOf(parts[3]));
        }

        // Frame map KIDNAP: 165
        else if( line.matches("^Frame map [A-Za-z]+: \\d+.*") ) {
          String[] parts = line.split("\\s+");
          mucTypes.add(parts[2]);
          idmap.add(Integer.valueOf(parts[3]));
          System.out.println("Frame Type Set " + parts[2] + " to " + Integer.valueOf(parts[3]));
        }

        else if( line.matches("^\\s*Cluster id=.+") ) {
          int idpos = line.indexOf("id=");
          ReadingCluster cluster = ReadingCluster.fromString(line.substring(idpos));
          _clusters.add(cluster);
          System.out.println("Read cluster " + cluster);
        }

        else if( line.matches("^\\s*Topic id=.+") ) {
          int idpos = line.indexOf("id=");
          ReadingCluster cluster = ReadingCluster.fromString(line.substring(idpos));
          _topics.add(cluster);
          System.out.println("Read topic " + cluster);
        }

        // Old format
        else if( line.matches("^Adding Consistent2? id.+") ) {
          int starter = line.indexOf("id="); 
          ReadingCluster item = ReadingCluster.fromString(line.substring(starter));
          sigSentences.add(item);
          //System.out.println("read consistent: " + item);
        }
        // New Format
        else if( line.matches("^Adding Consistent2? \\d.+") ) {
          String[] parts = line.split("\\s+");
          ReadingCluster item = new ReadingCluster();
          item.setID(Integer.parseInt(parts[3]));
          item.setClusterScore(Float.parseFloat(parts[2]));
          sigSentences.add(item);
//          System.out.println("read consistent: " + item);
        }
      }
    } catch( Exception ex ) { 
      System.out.println("Exception reading line: " + line);
      ex.printStackTrace(); 
      System.exit(1);
    }

    // Sanity check: one of these should have been set.
    if( _clusterIDs.size() == 0 && _topicIDs.size() == 0 ) {
      System.out.println("Evaluate gauntlet() got empty template IDs");
      System.exit(-1);
    }
  }


  public static void main(String[] args) {
    Evaluate eval = new Evaluate(args);
    System.out.println(Arrays.toString(args));
    eval.readClustersFromFile(args[args.length-2]);
//    eval.readTopicsFromFile(args[args.length-1]);
//    eval.mapClustersToTopics();
    eval.gauntlet();
//    eval.interpolationGauntlet();
  }
}
