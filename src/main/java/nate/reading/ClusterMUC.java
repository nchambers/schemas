package nate.reading;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import nate.CountTokenPairs;
import nate.IDFMap;
import nate.NERSpan;
import nate.cluster.ClusterUtil;
import nate.cluster.HierarchicalClustering;
import nate.cluster.SingleLinkSimilarity;
import nate.narrative.ScoreCache;
import nate.util.SortableObject;
import nate.util.SortableScore;
import nate.util.Triple;
import nate.util.Util;
import nate.util.WordNet;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TypedDependency;


public class ClusterMUC {
  // http://www.edufind.com/english/grammar/rep7.cfm
  public final static String[] _reportingWords = { "report", "say", "reply", "announce", "ask", "add", "explain", "state", "tell", "quote", "speak", "suggest" };

  public ClusterMUC() {
  }


  public static void scoresToProbabilities(ScoredFrame[] scores) {
    double sum = 0.0;
    for( ScoredFrame cluster : scores )
      sum += cluster.score();
    for( ScoredFrame cluster : scores )
      cluster.setScore(cluster.score() / sum);
  }
  
  /**
   * Given a document, return the clusters that are most relevant to this document.
   * Relevancy: highest similarity score?
   * @return Array of objects that contain scores and the index of the cluster. The
   *         index is the index in the given clusters array.
   */
  public static List<ScoredFrame> mostRelevantClusters(ProcessedDocument doc, ScoredFrame[] docScores, List<ScoredFrame[]> sentenceScores, int numDocClusters, int numSentClusters) {
    List<ScoredFrame> top = new ArrayList<ScoredFrame>();
    Set<Integer> added = new HashSet<Integer>();

    // Sum each cluster's scores over the sentences.
    // This helps KIDNAP, but hurts BOMB
//    System.out.println("Summing sentence scores for document scores...");
//    docScores = sumSentenceScores(sentenceScores);
    
    System.out.println("Using direct full document scores...numdocs " + docScores.length);
    if( docScores.length > 0 && docScores[0].score() >= 0.0 )
      scoresToProbabilities(docScores);
    
    for( int i = 0; i < Math.min(numDocClusters, docScores.length); i++ ) {
      ScoredFrame scored = docScores[i];
      top.add(scored);
      System.out.printf("Top %d\t%d\t%.5f\n", i, scored.frame().getID(), scored.score());
      added.add(scored.frame().getID());
    }

    // Individual sentence winners.
    if( numSentClusters > 0 ) {
//      List<ScoredFrame> localWinners = significantSentenceWinsByProbability(sentenceScores);
//      List<ScoredFrame> topWinners = consistentSentenceWins(sentenceScores);
      ScoredFrame[] topWinners = topBlendedSentenceWins(doc, sentenceScores, 1);
      for( ScoredFrame win : topWinners ) {
        System.out.println("Win\t" + win.frame().getID());
        if( !added.contains(win.frame().getID()) ) {
          added.add(win.frame().getID());
          top.add(win);
        }
      }
    }

    return top;
  }

  public static List<ScoredFrame> mostRelevantDocProbClusters(ScoredFrame[] docScores, double probCutoff) {
    List<ScoredFrame> top = new ArrayList<ScoredFrame>();
    
    System.out.println("Using all doc probs.");
    if( docScores != null && docScores.length > 0 ) {
      for( ScoredFrame scored : docScores )
        if( scored.score() > probCutoff )
          top.add(scored);
    }
    
    return top;
  }
  
  public static List<ScoredFrame> mostRelevantPreciseClusters(ScoredFrame[] docScores) {
    List<ScoredFrame> top = new ArrayList<ScoredFrame>();
    
    System.out.println("Using all precise guesses based on central tokens.");
    if( docScores != null && docScores.length > 0 ) {
      for( ScoredFrame scored : docScores )
        if( scored.score() > 0.0 )
          top.add(scored);
    }
    
    return top;
  }
  
  /**
   * Saves the frames that appear in both...
   */
  public static List<ScoredFrame> mostRelevantPreciseAndProbs(ScoredFrame[] preciseScores, ScoredFrame[] probScores, double probCutoff) {
    List<ScoredFrame> topPrecise = mostRelevantPreciseClusters(preciseScores);
    List<ScoredFrame> topProbs = mostRelevantDocProbClusters(probScores, probCutoff);
    List<ScoredFrame> top = new ArrayList<ScoredFrame>();
    
    for( ScoredFrame sc : topPrecise )
      System.out.println("\tprecise\t" + sc.frame().getID());
    for( ScoredFrame sc : topProbs )
      System.out.println("\tprobs\t" + sc.frame().getID());
    
    System.out.println("Voting precise and probs");
    for( ScoredFrame precise : topPrecise ) {
      for( ScoredFrame prob : topProbs ) {
        if( precise.frame().getID() == prob.frame().getID() ) {
          top.add(precise);
        }
      }
    }
    
    return top;
  }
  
  /**
   * DESTRUCTIVE!
   * Adds the items in the second list to the first one, if they aren't already in there.
   * If the addlist contains items with higher cluster scores, update the original list's cluster score.
   * @param mainlist
   * @param addlist
   */
  public static void mergeClusterLists(List<ScoredFrame> mainlist, List<ScoredFrame> addlist) {
    for( ScoredFrame add : addlist ) {
      boolean found = false;
      for( ScoredFrame main : mainlist ) {
        if( add.frame().getID() == main.frame().getID() ) {
          if( add.score() > main.score() ) {
            main.setScore(add.score());
            found = true;
          }
          break;
        }
      }
      if( !found ) {
        mainlist.add(add);
      }
    }  
  }
  
  public static boolean sentenceContainsKeyClusterTokens(Collection<String> sentenceTokens, Frame frame) {
    Set<String> clusterTokens = frame.tokens();
    for( String token : sentenceTokens ) {
      if( clusterTokens.contains(token) )
        return true;
    }
    return false;
  }
  
  public static ScoredFrame[] labelSentenceWithClustersProbabilities(Collection<String> sentenceTokens, Frame[] frames) {
    ScoredFrame[] scoredFrames = new ScoredFrame[frames.length];

    // Judge each frame's distance to the sentence.
    int i = 0;
    System.out.println("sentprobs tokens " + sentenceTokens);
    for( Frame frame : frames ) {
//      System.out.println("sentprobs frame " + frame.getTokens());
      double maxprob = 0.0;
      boolean matched = false;
      double score = 0.0;
      for( String token : sentenceTokens ) {
        double prob = frame.getTokenProb(token);
//        if( frame.contains("v-kidnap") ) System.out.printf("kidnap: %s %.4f\n", token, prob);
        if( prob > 0.0 ) {
          score += Math.log(frame.getTokenProb(token));
          matched = true;
        }
        else score += -5.0;
        if( prob > maxprob ) maxprob = prob;
      }
      // if all tokens had zero probability.
      if( !matched ) score = -999.99;
//      System.out.println();
//      System.out.println("  total prob = " + score);
      ScoredFrame newframe = new ScoredFrame(score, frame, maxprob);
      newframe.setNumTokensScored(sentenceTokens.size());
      scoredFrames[i++] = newframe;
    }
        
    Arrays.sort(scoredFrames);
    
    // DEBUG ONLY
    int xx = 0;
    for( ScoredFrame item : scoredFrames ) {
      System.out.printf("  scored %.4f\t%s\n", item.score(), item.frame().tokens());
      if( xx++ == 6 ) break;
    }

    return scoredFrames;
  }
  
  /**
   * Counts how many "trigger words" appear in the sentence per frame.  A frame is scored by
   * the trigger word count.  Plain and simple.
   * @return A sorted list of frames by trigger count.
   */
  public static ScoredFrame[] labelSentenceWithPreciseMatches(Collection<String> sentenceTokens, Frame[] frames) {
    System.out.println("labelsentence precise! (" + frames.length + " frames)");
    
    // Judge each frame's distance to the document.
    ScoredFrame[] scoredFrames = new ScoredFrame[frames.length];
    int i = 0;
    System.out.println("sentprec tokens " + sentenceTokens);
    for( Frame frame : frames ) {
      int numMatches = 0;
      if( frame.getCentralTokens() != null ) {
        for( String token : frame.getCentralTokens() ) {
          if( sentenceTokens.contains(token) ) {
            numMatches++;
            if( frame.contains("v-kidnap") || frame.contains("v-explode") ) System.out.println("* contains " + token);
            System.out.println("matched " + token + " frame " + frame.getID());
          }
        }
      }
      scoredFrames[i++] = new ScoredFrame(numMatches, frame);
    }
    
    Arrays.sort(scoredFrames);
    
    // DEBUG ONLY
    int xx = 0;
    for( ScoredFrame item : scoredFrames ) {
      if( item.score() > 0.0 )
        System.out.printf("  scored %.4f\t%s\n", item.score(), item.frame().tokens());
      if( xx++ == 6 ) break;
    }
    
    return scoredFrames;
  }
  
  /**
   * The given sentence is represented by a list of tokens.  These could be a subset of the
   * actual sentence...just give it the important tokens you want included in the
   * scoring function.
   * Creates cloned clusters, and updates the clones with that score, returns them sorted.
   * @param frames All clusters we want to score.
   * @param cache The pairwise scores between tokens that were used during clustering.
   * @return An array of scored cluster objects, sorted.
   */
  public static ScoredFrame[] labelSentenceWithClusters(Collection<String> sentenceTokens, Frame[] frames, ScoreCache cache, boolean requireMembership) {
    ScoredFrame[] scoredFrames = new ScoredFrame[frames.length];

    int i = 0;
    for( Frame frame : frames ) {
      Collection<String> clusterTokens = frame.tokens();
      float score = 0.0f;
      int numtokens = sentenceTokens.size();
      if( numtokens > 0 ) {
        boolean debug = false;
        if( LabelDocument.CLUSTER_SENT_SIM.equals("max") )
          score = SingleLinkSimilarity.computeClusterSimilarity(clusterTokens, sentenceTokens, cache);
        else if( LabelDocument.CLUSTER_SENT_SIM.equals("newlink-tight") )
          score = SingleLinkSimilarity.computeNewLinksClusterSimilarity(clusterTokens, sentenceTokens, cache, true, debug);
        else if( LabelDocument.CLUSTER_SENT_SIM.equals("newlink") )
          score = SingleLinkSimilarity.computeNewLinksClusterSimilarity(clusterTokens, sentenceTokens, cache, false, debug);
        else {
          System.out.println("Unknown cluster similarity: " + LabelDocument.CLUSTER_SENT_SIM);
          System.exit(-1);
        }
//        score = score / (float)numtokens;
        // Interpolate with key-token score?
//        if( sentenceContainsKeyClusterTokens(sentenceTokens, cluster) )  score = score + 2.0f;
        if( requireMembership && !sentenceContainsKeyClusterTokens(sentenceTokens, frame) ) score = 0.3f * score;
      }
      scoredFrames[i++] = new ScoredFrame(score, frame);
    }
    
    Arrays.sort(scoredFrames);
    
    // DEBUG ONLY
    int xx = 0;
    for( ScoredFrame item : scoredFrames ) {
      System.out.printf("  scored %.4f\t%s\n", item.score(), item.frame().tokens());
      if( xx++ == 6 ) break;
    }

    return scoredFrames;
  }
  
  /**
   * @param tokenType e.g. VERBS, VERBS_AND_NOMINALS, etc.
   * @return A list of arrays, one array for each sentence.  The array contains the cluster
   *         scores for that particular sentence.  The list is as long as the number of sentences.
   *         Each SortableObject has a ReadingCluster object with the cluster it scored.
   */
  public static List<ScoredFrame[]> labelSentencesWithClusters(ProcessedDocument doc,
      Frame[] frames,
      ScoreCache cache,
      WordNet wordnet,
      IDFMap generalIDF,
      IDFMap domainIDF,
      int tokenType,
      boolean includeDependents,
      boolean requireMembership) {
    System.out.println("labelsentences!");
    List<ScoredFrame[]> labelScoresBySentence = new ArrayList<ScoredFrame[]>();
    
    int sid = 0;
    for( Tree tree : doc.trees() ) {
      List<TypedDependency> sentdeps = doc.deps.get(sid);
//      List<String> tokens = getKeyTokensInSentence(sid, tree, sentdeps, wordnet, generalIDF, tokenType, includeDependents, true);
      List<String> tokens = getKeyTokensInSentenceRemoveDomainRare(sid, tree, sentdeps, doc.ners, wordnet, generalIDF, domainIDF, CountTokenPairs.BASE, includeDependents, true);
      System.out.println("sentence key tokens: " + tokens);
//      labelScoresBySentence.add(labelSentenceWithClusters(tokens, frames, cache, requireMembership));
//      labelScoresBySentence.add(labelSentenceWithClustersProbabilities(tokens, frames));
      
      labelScoresBySentence.add(labelSentenceWithPreciseMatches(tokens, frames));
      sid++;
    }
    return labelScoresBySentence;
  }
  
  /**
   * @return A sorted list of frames by top overall probability with the given document.
   */
  public static ScoredFrame[] labelDocumentWithClusterProbabilities(ProcessedDocument doc,
      Frame[] frames,
      WordNet wordnet,
      IDFMap generalIDF,
      IDFMap domainIDF,
      int tokenType,
      boolean includeDependents,
      boolean requireMembership) {
    System.out.println("labeldocument probs!");
    List<String> doctokens = new ArrayList<String>();

    // Get the key tokens.
    boolean includeCollocationObjects = true;
    List<List<String>> sentTokens = getKeyTokensInDocument(doc, wordnet, generalIDF, domainIDF, tokenType, includeDependents, includeCollocationObjects);
    for( List<String> tokens : sentTokens ) 
      doctokens.addAll(tokens);
    
    // Judge each frame's distance to the document.
    ScoredFrame[] scoredFrames = new ScoredFrame[frames.length];
    int i = 0;
    System.out.println("docprobs tokens " + doctokens);
    for( Frame frame : frames ) {
      double score = 0.0;
      for( String token : doctokens ) {
        double prob = frame.getTokenProb(token);
//        System.out.println("  - " + token + " frame " + frame.getID() + " prob " + frame.getTokenProb(token));
        if( prob > 0.0 ) score += Math.log(frame.getTokenProb(token));
        else score += -5.0;        
      }
      // Geometric Average
      score = score / (double)doctokens.size();
//      System.out.println("  -- scored frame " + frame.getID() + " score=" + score);
      scoredFrames[i++] = new ScoredFrame(score, frame);
    }
    
    Arrays.sort(scoredFrames);

    return scoredFrames;
  }
  
  /**
   * Counts how many "trigger words" appear in the document per frame.  A frame is scored by
   * the trigger word count.  Plain and simple.
   * @return A sorted list of frames by trigger count.
   */
  public static ScoredFrame[] labelDocumentWithPreciseMatches(ProcessedDocument doc,
      Frame[] frames,
      WordNet wordnet,
      IDFMap generalIDF,
      IDFMap domainIDF,
      int tokenType,
      boolean includeDependents,
      boolean requireMembership) {
    System.out.println("labeldocument precise!");
    List<String> alltokens = new ArrayList<String>();

    // Get the key tokens.
    boolean includeCollocationObjects = true;
    List<List<String>> sentTokens = getKeyTokensInDocument(doc, wordnet, generalIDF, domainIDF, tokenType, includeDependents, includeCollocationObjects);
    for( List<String> tokens : sentTokens ) 
      alltokens.addAll(tokens);
    
    // Judge each frame's distance to the document.
    ScoredFrame[] scoredFrames = new ScoredFrame[frames.length];
    int i = 0;
    System.out.println("docprec tokens " + alltokens);
    for( Frame frame : frames ) {
      int numMatches = 0;
      if( frame.getCentralTokens() != null ) {
        for( String token : frame.getCentralTokens() ) {
          if( alltokens.contains(token) ) {
            numMatches++;
            if( frame.contains("v-kidnap") || frame.contains("v-explode") ) System.out.println("* contains " + token);
          }
        }
      }
      scoredFrames[i++] = new ScoredFrame(numMatches, frame);
    }
    
    Arrays.sort(scoredFrames);
    return scoredFrames;
  }
  
  /**
   * Scores each frame against the document's entire collection of tokens.
   * @return An array of each frame scored, sorted in order from the highest.
   */
  public static ScoredFrame[] labelDocumentWithClusters(ProcessedDocument doc,
      Frame[] frames,
      ScoreCache cache,
      WordNet wordnet,
      IDFMap generalIDF,
      IDFMap domainIDF,
      int tokenType,
      boolean includeDependents,
      boolean requireMembership) {
    System.out.println("labeldocument!");
    List<String> alltokens = new ArrayList<String>();

    // Get the key tokens.
    boolean includeCollocationObjects = true;
    List<List<String>> sentTokens = getKeyTokensInDocument(doc, wordnet, generalIDF, domainIDF, tokenType, includeDependents, includeCollocationObjects);
    for( List<String> tokens : sentTokens ) 
      alltokens.addAll(tokens);
    
    // Judge each frame's distance to the document.
    ScoredFrame[] scoredFrames = new ScoredFrame[frames.length];
    int i = 0;
    for( Frame frame : frames ) {
      float score = 0.0f;
      int numtokens = alltokens.size();
      if( numtokens > 0 ) {
        boolean debug = false;
        if( frame.contains("v-explode") ) debug = true;
        if( debug ) System.out.println("labelDocumentWithClusters");
        
        if( LabelDocument.CLUSTER_DOC_SIM.equals("max") )
          score = SingleLinkSimilarity.computeClusterSimilarity(frame.tokens(), alltokens, cache);
        else if( LabelDocument.CLUSTER_DOC_SIM.equals("newlink-tight") )
          score = SingleLinkSimilarity.computeNewLinksClusterSimilarity(frame.tokens(), alltokens, cache, true, debug);
        else if( LabelDocument.CLUSTER_DOC_SIM.equals("newlink") )
          score = SingleLinkSimilarity.computeNewLinksClusterSimilarity(frame.tokens(), alltokens, cache, false, debug);

        score = score / (float)numtokens;
        // Interpolate with key-token score?
//        if( sentenceContainsKeyClusterTokens(sentenceTokens, cluster) )  score = score + 2.0f;
        if( requireMembership && !sentenceContainsKeyClusterTokens(alltokens, frame) ) score = 0.3f * score;
      }
      scoredFrames[i++] = new ScoredFrame(score, frame);
    }
    
    Arrays.sort(scoredFrames);
    return scoredFrames;
  }


  /**
   * This is really for testing, but it reads entire files of documents, and labels each
   * document with the best clusters.  Prints the results on each document.
   */  
  public static void labelDocumentsWithClusters(ProcessedData dataReader,
      Frame[] frames,
      ScoreCache cache,
      WordNet wordnet,
      IDFMap generalIDF,
      IDFMap domainIDF,
      int tokenType,
      boolean includeDependents) {
    dataReader.nextStory();

    while( dataReader.getParseStrings() != null ) {
      List<ScoredFrame[]> sentenceLabels = labelSentencesWithClusters(dataReader.getDocument(),
          frames, cache, wordnet, generalIDF, domainIDF, tokenType, includeDependents, false);
      // Sum each cluster's score for each sentence.
      ScoredFrame[] overallArray = sumSentenceScores(sentenceLabels);

      // Print it Sorted
      System.out.println("*** Sorted Clusters Entire Document " + dataReader.currentStoryNum() + " ***");
      for( ScoredFrame scored : overallArray )
        System.out.printf("%.4f\t%s\n", scored.score(), scored.frame().tokens());
    }
  }

  /**
   * Takes a sentence and returns a list of only the important tokens, ignoring those that
   * aren't seen much in the domain.
   */
  public static List<String> getKeyTokensInSentenceRemoveDomainRare(int sid, Tree tree, List<TypedDependency> sentdeps, List<NERSpan> ners,
      WordNet wordnet, IDFMap generalIDF, IDFMap domainIDF, int tokenType, boolean includeDependents, boolean includeCollocationObjects) {
    List<String> tokens = getKeyTokensInSentence(sid, tree, sentdeps, ners, wordnet, generalIDF, tokenType, includeDependents, includeCollocationObjects);
//    System.out.println("removerare first " + tokens);
    tokens = removeLowCounts('n', tokens, domainIDF, 50); 
    tokens = removeLowCounts('v', tokens, domainIDF, 10); 
    tokens = removeLowCounts('v', tokens, generalIDF, 100); 
    tokens = removeLowCounts('n', tokens, generalIDF, 500);
    tokens = removeUnknownToWordnet(tokens, wordnet);
//    System.out.println("  - now " + tokens);
    return tokens;
  }
  
  /**
   * Takes a sentence and returns a list of only the important tokens.
   */
  public static List<String> getKeyTokensInSentence(int sid, Tree tree, List<TypedDependency> sentdeps, List<NERSpan> ners,
      WordNet wordnet, IDFMap generalIDF, int tokenType, boolean includeDependents, boolean includeCollocationObjects) {
    List<String> tokens = CountTokenPairs.getKeyTokens(sid, tree, sentdeps, ners, wordnet, null, tokenType, includeDependents, false, false, includeCollocationObjects);
    tokens = removeLowIDF(tokens, generalIDF, 1.2f, false);
    if( LabelDocument.REMOVE_REPORTING_WORDS ) removeReportingWords(tokens);
//    if( includeCollocationObjects ) removeNERCollocations(tokens);
    return tokens;
  }
  
  /**
   * Takes a document and returns a list of tokens for each sentence with only the important tokens.
   */
  public static List<List<String>> getKeyTokensInDocument(ProcessedDocument doc,
      WordNet wordnet, IDFMap generalIDF, IDFMap domainIDF, int tokenType, boolean includeDependents, boolean includeCollocationObjects) {
    List<List<String>> allTokens = new ArrayList<List<String>>();
    int sid = 0;

    for( Tree tree : doc.trees() ) {
      List<TypedDependency> sentdeps = doc.deps.get(sid);
//      List<String> tokens = getKeyTokensInSentence(sid, tree, sentdeps, wordnet, generalIDF, tokenType, includeDependents, includeCollocationObjects);
      List<String> tokens = getKeyTokensInSentenceRemoveDomainRare(sid, tree, sentdeps, doc.ners, wordnet, generalIDF, domainIDF, tokenType, includeDependents, includeCollocationObjects);
      allTokens.add(tokens);
      sid++;
    }
    return allTokens;
  }
  
  /**
   * Helper function that takes the cluster scores for each sentence (list of sentences),
   * and sums the scores across sentences.  (cluster X's score for each sentence summed up)
   * Sorts the total scores and returns the array.
   * @retun Array of scored clusters with their total summed scores.
   */
  public static ScoredFrame[] sumSentenceScores(List<ScoredFrame[]> sentenceLabels) {
    // Loop over each sentence's assigned cluster scores.
    Map<Integer, Double> overallScores = new HashMap<Integer, Double>();
    Map<Integer, Frame> idlookup = new HashMap<Integer, Frame>();
    
    for( ScoredFrame[] scores : sentenceLabels ) {
      for( ScoredFrame scored : scores ) {
        if( scored.score() > 0.0f ) {
          idlookup.put(scored.frame().getID(), scored.frame());
          Util.incrementCount(overallScores, scored.frame().getID(), scored.score());
        }
      }
    }

    // Convert to array and sort.
    int i = 0;
    ScoredFrame[] overallArray = new ScoredFrame[overallScores.size()];
    for( Integer clusterID : overallScores.keySet() ) {
      overallArray[i++] = new ScoredFrame(overallScores.get(clusterID), idlookup.get(clusterID));
    }
    Arrays.sort(overallArray);

    return overallArray;
  }
  
  /**
   * Keep the top n winners of each sentence, return them all.
   */
  public static List<ScoredFrame> topSentenceWins(List<ScoredFrame[]> sentenceLabels, int n) {
    List<ScoredFrame> winners = new ArrayList<ScoredFrame>();
    Set<Integer> added = new HashSet<Integer>();

    // Loop over each sentence's assigned cluster scores.    
    for( ScoredFrame[] scores : sentenceLabels ) {
      for( int i = 0; i < n; i++ ) {
        if( scores.length > i ) {
          ScoredFrame scored = scores[i];
          if( scored.score() > -999.9 ) {
            if( !added.contains(scored.frame().getID()) ) {
              added.add(scored.frame().getID());
              winners.add(scored);
              System.out.println("Adding Top " + scored.frame().getID());
            }
          }
        }
      }
    }
    return winners;
  }
  
  public static ScoredFrame[] topBlendedSentenceWins(ProcessedDocument doc, List<ScoredFrame[]> sentenceLabels, int n) {
    ScoredFrame[] neworder = new ScoredFrame[sentenceLabels.get(0).length];

    System.out.println("topBlended " + sentenceLabels.size() + " sentences.");
    
    for( int senti = 0; senti < sentenceLabels.size()-1; senti++ ) {
      ScoredFrame[] one = sentenceLabels.get(senti);
      ScoredFrame[] two = sentenceLabels.get(senti+1);
      
//      System.out.println("  sentence " + senti);
      
      int i = 0;
      for( ScoredFrame scored1 : one ) {
        for( ScoredFrame scored2 : two ) {
          if( scored1.frame().getID() == scored2.frame().getID() ) {
            double newscore = (scored1.score() + scored2.score()) / (double)(scored1.numTokensScored()+scored2.numTokensScored());
//            System.out.printf("  frame %d\t%.3f\t%.3f\t%.3f\n", scored1.frame().getID(), scored1.score(), scored2.score(), newscore);
            if( neworder[i] == null || neworder[i].score() < newscore ) {
              ScoredFrame newframe = new ScoredFrame(newscore, scored1.frame()); 
              if( scored1.maxProb() > scored2.maxProb() ) newframe.setMaxProb(scored1.maxProb());
              neworder[i] = newframe;
            }
            break;
          }
        }
        i++;
      }
    }
    
    // Special case for documents with only one sentence.
    if( sentenceLabels.size() == 1 ) {
      int sentOneLength = doc.trees().get(0).size();
      neworder = sentenceLabels.get(0);
      for( ScoredFrame frame : neworder )
        frame.setScore(frame.score() / (double)frame.numTokensScored());
    }
    
    Arrays.sort(neworder);
    return neworder;
  }
  
  public static List<ScoredFrame> top3BlendedSentenceWins(List<ScoredFrame[]> sentenceLabels, int n) {
    List<ScoredFrame> winners = new ArrayList<ScoredFrame>();
    Set<Integer> added = new HashSet<Integer>();

    for( int senti = 0; senti < sentenceLabels.size()-2; senti++ ) {
      ScoredFrame[] one = sentenceLabels.get(senti);
      ScoredFrame[] two = sentenceLabels.get(senti+1);
      ScoredFrame[] three = sentenceLabels.get(senti+2);
      ScoredFrame[] neworder = new ScoredFrame[one.length];
      
      int i = 0;
      for( ScoredFrame scored1 : one ) {
        for( ScoredFrame scored2 : two ) {
          if( scored1.frame().getID() == scored2.frame().getID() ) {
            for( ScoredFrame scored3 : three ) {
              if( scored2.frame().getID() == scored3.frame().getID() ) {
                // Assume the scores are log probabilities.
                ScoredFrame newscored = new ScoredFrame(scored1.score() + scored2.score() + scored3.score(), scored1.frame());
                neworder[i++] = newscored;
              }
            }
          }
        }
      }

      Arrays.sort(neworder);
      
      for( int xx = 0; xx < n; xx++ ) {
        if( neworder.length > xx ) {
          ScoredFrame scored = neworder[xx];
          if( scored.score() > -999.9 ) {
            System.out.println("Merged top " + scored.frame().getID() + " " + scored.score());

            if( !added.contains(scored.frame().getID()) ) {
              added.add(scored.frame().getID());
              winners.add(scored);
              System.out.println("Adding Top " + scored.frame().getID());
            }
          }
        }
      }
    }
    return winners;
  }
  
  /**
   * This is a specialty function that looks for two sentences in a row that seem to have
   * a cluster scored consistently high.  It returns a cluster under the following condition:
   * One sentence ranks the cluster as its highest cluster, and a neighboring sentence (before or
   * after) ranks the cluster within its top 7 (number chosen somewhat arbitrarily).
   */
  public static List<ScoredFrame> consistentSentenceWins(List<ScoredFrame[]> sentenceLabels) {
    List<ScoredFrame> winners = new ArrayList<ScoredFrame>();
    Set<Integer> added = new HashSet<Integer>();
    ScoredFrame previousWinner = null;
    Set<Integer> previousTop = new HashSet<Integer>();
    
    System.out.println("consistentSentenceWins top!");
    
    // Loop over each sentence's assigned cluster scores.    
    for( ScoredFrame[] scores : sentenceLabels ) {
      // Sometimes the top scores are actually zero (-999.99)
      if( scores.length > 1 && scores[0].score() != -999.99 ) {
        int id0 = scores[0].frame().getID();

        // If this sentence's best score was in the previous sentence's top n.
        if( previousTop.contains(id0) ) {
          if( !added.contains(id0) ) {
            added.add(id0);
            winners.add(new ScoredFrame(scores[0].score()*2.0, scores[0].frame()));
            System.out.println("Adding Consistent " + scores[0]);
          }       
        }
        previousTop.clear();

        // If the previous sentence's top score is in this sentence's top n.
        int i = 0;
        for( ScoredFrame scored : scores ) {
          if( previousWinner != null && scored.frame().getID() == previousWinner.frame().getID() ) {
            if( !added.contains(scored.frame().getID()) ) {
              added.add(scored.frame().getID());
              winners.add(new ScoredFrame(previousWinner.score()*2.0, previousWinner.frame()));
              System.out.println("Adding Consistent2 " + scored);
            }
          }
          if( scored.score() != -999.99 )
            previousTop.add(scored.frame().getID());
          i++;
          if( i == 6 ) break;
        }

        // Reset the previous variable.
        previousWinner = scores[0];
      }
      else {
        previousWinner = null;
        previousTop.clear();
      }
    }
    return winners;
  }

  /**
   * Helper function that takes the cluster scores for each sentence (list of sentences),
   * and finds sentences that have a clear winner.  Return all of those clear winners.
   * @retun Array of objects with scores and the ReadingCluster object that is scored.
   */
  public static List<ScoredFrame> significantSentenceWins(List<ScoredFrame[]> sentenceLabels) {
    List<ScoredFrame> winners = new ArrayList<ScoredFrame>();
    Set<Integer> added = new HashSet<Integer>();

    // Loop over each sentence's assigned cluster scores.
    for( ScoredFrame[] scores : sentenceLabels ) {
      System.out.println("Next sig sent");
      double diff12 = 0.0;
      if( scores.length > 1 ) {
        if( scores[1].score() == 0.0 )
          diff12 = 2.0; // default, high score if it's the only scored cluster
        else
          diff12 = (scores[0].score() - scores[1].score()) / scores[1].score();

        // DEBUG
        if( diff12 > 0.1f ) System.out.printf("SigSent %.3f %d\n", diff12, scores[0].frame().getID());
        
        // If the difference between the first and second cluster is great enough.
        if( diff12 > 0.6f ) {
          ScoredFrame cluster = scores[0];
           if( !added.contains(cluster.frame().getID()) ) {
            //winners.add(cluster);
            winners.add(new ScoredFrame(diff12, cluster.frame()));
            added.add(cluster.frame().getID());
            System.out.println("Adding1 " + cluster.frame().getID());
          }
        }
      }

      if( diff12 < 0.1f && scores.length > 2 ) {
        double diff23 = (scores[1].score() - scores[2].score()) / scores[2].score();
        if( scores[2].score() == 0.0 ) diff23 = 2.0;

        // DEBUG
        if( diff23 > 0.1f ) System.out.printf("SigSent2 %.3f %d\n", diff23, scores[1].frame().getID());

        if( diff23 > 0.6f ) {
          ScoredFrame scored = scores[1];
          if( !added.contains(scored.frame().getID()) ) {
            //winners.add(cluster);
            winners.add(new ScoredFrame(diff23, scored.frame()));
            added.add(scored.frame().getID());
            System.out.println("Adding2 " + scored.frame().getID());
          }
        }
      }
    }

    return winners;
  }

  /**
   * Helper function that takes the cluster scores for each sentence (list of sentences),
   * and finds sentences that have a clear winner.  Return all of those clear winners.
   * @retun Array of objects with scores and the ReadingCluster object that is scored.
   */
  public static List<ScoredFrame> significantSentenceWinsByProbability(List<ScoredFrame[]> sentenceLabels) {
    List<ScoredFrame> winners = new ArrayList<ScoredFrame>();
    Set<Integer> added = new HashSet<Integer>();

    // Loop over each sentence's assigned cluster scores.
    for( ScoredFrame[] scores : sentenceLabels ) {
      System.out.println("Next sig sent");
      double diff12 = 0.0;
      if( scores.length > 1 ) {
        if( scores[1].score() == 0.0 && scores[0].score() > 0.0 )
          diff12 = 2.0; // default, high score if it's the only scored cluster
        else
          diff12 = Math.abs(scores[0].score() - scores[1].score());

        // DEBUG
        if( diff12 > 1.0 ) System.out.printf("SigSent %.3f %d\n", diff12, scores[0].frame().getID());
        
        // If the difference between the first and second cluster is great enough.
        if( diff12 > 1.0 ) {
          ScoredFrame cluster = scores[0];
           if( !added.contains(cluster.frame().getID()) ) {
            //winners.add(cluster);
            winners.add(new ScoredFrame(diff12, cluster.frame()));
            added.add(cluster.frame().getID());
            System.out.println("Adding1 " + cluster.frame().getID());
          }
        }
      }

      if( diff12 < 0.1f && scores.length > 2 ) {
        double diff23 = Math.abs(scores[1].score() - scores[2].score());
        if( scores[2].score() == 0.0 ) diff23 = 2.0;

        // DEBUG
        if( diff23 > 1.0 ) System.out.printf("SigSent2 %.3f %d\n", diff23, scores[1].frame().getID());

        if( diff23 > 1.0 ) {
          ScoredFrame scored = scores[1];
          if( !added.contains(scored.frame().getID()) ) {
            //winners.add(cluster);
            winners.add(new ScoredFrame(diff23, scored.frame()));
            added.add(scored.frame().getID());
            System.out.println("Adding2 " + scored.frame().getID());
          }
        }
      }
    }

    return winners;
  }
  
  /**
   * @return A list of tokens with low IDF ones removed.
   */
  public static List<String> removeLowIDF(List<String> tokens, IDFMap generalIDF, float cutoff) {
    return removeLowIDF(tokens, generalIDF, cutoff, true);
  }


  public static List<String> removeLowIDF(List<String> tokens, IDFMap generalIDF, float cutoff, boolean debug) {
    List<String> saved = new ArrayList<String>();
    for( String token : tokens ) {
      float idf = generalIDF.get(token);
      if( idf == 0.0f || idf > cutoff )
        saved.add(token);
      else if( debug )
        System.out.println("ClusterMUC ignoring keytoken " + token);
    }
    return saved;
  }

  public static List<String> removeUnknownToWordnet(List<String> tokens, WordNet wordnet) {
    List<String> saved = new ArrayList<String>();
    for( String token : tokens ) {
      if( token.startsWith("v-") || token.startsWith("j-") || 
          (!wordnet.isUnknown(token.substring(2)) && !wordnet.isNamedEntity(token.substring(2))) )
        saved.add(token);
//      else
//        System.out.println("wordnet remove " + token);
    }
    return saved;
  }
  
  public static List<String> removeLowCounts(char pos, List<String> tokens, IDFMap idf, float cutoff) {
    List<String> saved = new ArrayList<String>();
    for( String token : tokens ) {
      int count = idf.getDocCount(token);
      if( count > cutoff || token.charAt(0) != pos )
        saved.add(token);
      else
        System.out.println("removing " + token + " count " + count + " cutoff " + cutoff);
    }
    return saved;
  }

  public static boolean isReportingWord(String token) {
    String base = token.substring(2);
    for( String report : _reportingWords ) {
      if( base.toLowerCase().equals(report) )
        return true;
    }
    return false;
  }
  
  private static List<String> removeNERCollocations(List<String> tokens) {
    List<String> saved = new ArrayList<String>();
    for( String token : tokens ) {
      if( !token.contains("#o#PERSON") && !token.contains("#o#LOCATION") && !token.contains("#o#ORGANIZATION") )
        saved.add(token);
    }
    return saved;
  }
  
  private static void removeReportingWords(List<String> tokenlist) {
    for( String report : _reportingWords ) {
      tokenlist.remove("v-" + report);
      tokenlist.remove("n-" + report);
    }
  }

  /**
   * Run the basic bottom-up agglomerative clustering algorithm on the given tokens.
   * Stop clustering when a single merged cluster reaches the given maxClusterSize size.
   * @return Objects with scores and their clusters.  Clusters are List<String> objects.
   */
  public static List<ReadingCluster> hierarchicalCluster(List<String> tokenlist, ScoreCache cache,
      int similarityType, int maxClusterSize) {
    System.out.println("Hierarchical Clustering!!");

    if( LabelDocument.REMOVE_REPORTING_WORDS ) removeReportingWords(tokenlist);
    
    // Setup the clusterer.
    HierarchicalClustering clusterer = new HierarchicalClustering();
    clusterer.setMinInitialSimilarityScore(0.01f);
    clusterer.setMinClusteringScore(0.01f);

    // Cluster.
    List<Triple> history = clusterer.efficientCluster(tokenlist, cache, similarityType);

    // Reconstruct the clusters from the history list.
    System.out.println("Reconstructing clusters!");
    Collection<Set<Integer>> myclusters = 
      ClusterUtil.reconstructClusters(history, tokenlist, 10, maxClusterSize);

    List<ReadingCluster> scores = new ArrayList<ReadingCluster>();
    int id = 0;
    for( Set<Integer> clust : myclusters ) {
      System.out.print("**");
      ClusterUtil.printCluster(tokenlist, clust);
      List<String> strs = ClusterUtil.clusterIdsToStrings(clust, tokenlist);
      Map<String,Double> tokenscores = ClusterUtil.scoreClusterTokens(strs, cache);
      double clusterScore = ClusterUtil.computeClusterScoreFull(strs, cache);
      // Create a cluster object.
      ReadingCluster cluster = new ReadingCluster(id++, tokenscores);
      cluster.setClusterScore(clusterScore);
      scores.add(cluster);
    }

    return scores;
  }
  
  /**

   * @param docTokens All tokens in the domain.
   * @param clusterTokens All tokens in the cluster.
   * @param corefCounts The number of times slots share coreferring arguments.
   * @return An unsorted list of neighbor words.
   */
  public static SortableScore[] nearbyWordsBySlot(Collection<String> docTokens, Collection<String> clusterTokens, CountTokenPairs corefCounts) {
    List<SortableScore> scored = new ArrayList<SortableScore>();
    Counter<String> coverage = new ClassicCounter<String>();
    Counter<String> totalCorefs = new ClassicCounter<String>();
    Counter<String> clusterMemberTotalCorefs = new ClassicCounter<String>();
    
    for( String member : clusterTokens ) {
      // Find all slots for this cluster token.
      for( String slot : corefCounts.tokensThatStartWith(member) ) {
        Map<String,Integer> counts = corefCounts.getCountsInt(slot);
        if( counts != null ) {
          for( Map.Entry<String, Integer> entry : counts.entrySet() ) {
            // Trim "v-kidnap:s" to "v-kidnap"
            String posToken = entry.getKey().substring(0, entry.getKey().indexOf(':'));
            // Don't count tokens that are already in the cluster.
            if( !clusterTokens.contains(posToken) ) {
              coverage.incrementCount(posToken);
              totalCorefs.incrementCount(posToken, entry.getValue());
            }
            else clusterMemberTotalCorefs.incrementCount(posToken, entry.getValue());
          }
        }
      }
    }
    
    for( Map.Entry<String,Double> entry : coverage.entrySet() ) {
      if( entry.getValue() > 3 || entry.getValue() > clusterTokens.size() / 3 ) {
        if( !LabelDocument.REMOVE_REPORTING_WORDS || !isReportingWord(entry.getKey()) )
          scored.add(new SortableScore(totalCorefs.getCount(entry.getKey()), entry.getKey()));
      }
    }
    
    System.out.print("Cluster words BY SLOT: ");
    for( String member : clusterMemberTotalCorefs.keySet() )
      System.out.println("  " + member + " " + clusterMemberTotalCorefs.getCount(member));
    System.out.println();
    
    // Sort
    System.out.print("Nearby words BY SLOT: ");
    SortableScore[] arr = new SortableScore[scored.size()];
    arr = scored.toArray(arr);
    Arrays.sort(arr);
    for( SortableScore item : arr )
      System.out.print("  " + item.key() + " " + item.score());
    System.out.println();
    
    return arr;
  }
  
  /**
   * This finds all tokens that are very close to the cluster ... requiring them to have evidence
   * of linking with a majority of the cluster tokens, and to have an overall strong association.
   * @param docTokens All tokens in the domain.
   * @param clusterTokens All tokens in the cluster.
   * @param cache Pairwise scores between tokens
   * @param withClusterWords If true, we score the cluster members too, if false, we omit them.
   * @return An unsorted list of neighbor words.
   */
  public static SortableScore[] nearbyWordsScored(Collection<String> docTokens, Collection<String> clusterTokens, ScoreCache cache, boolean withClusterWords) {
    Set<SortableScore> scored = new HashSet<SortableScore>();
    Set<SortableScore> clusterScored = new HashSet<SortableScore>();
    
    for( String token : docTokens ) {
      if( !LabelDocument.REMOVE_REPORTING_WORDS || !isReportingWord(token) ) {
        float sumscore = 0.0f;
        int matches = 0;
        for( String member : clusterTokens ) {
          float score = cache.getScore(member, token);
          if( score >= 1.2f ) {
            matches++;
          }
          sumscore += score;
        }

        if( matches > clusterTokens.size()*0.5f && !clusterTokens.contains(token) )
          scored.add(new SortableScore((sumscore/(float)clusterTokens.size()), token));
        else if( clusterTokens.contains(token) )
          clusterScored.add(new SortableScore((sumscore/(float)clusterTokens.size()), token));
          
      }
    }
    
//    System.out.print("Nearby cluster word scores: ");
//    SortableScore[] arr = new SortableScore[clusterScored.size()];
//    arr = clusterScored.toArray(arr); 
//    Arrays.sort(arr);
//    double sum = 0.0;
//    for( SortableScore item : arr ) {
//      System.out.print("  " + item.key() + " " + item.score());
//      sum += item.score();
//    }
//    double average = sum / (double)arr.length;
//    System.out.println("\nAverage score " + average);

    // Return the cluster's own words too, if desired.
    if( withClusterWords )
      scored.addAll(clusterScored);
    
    // Sort
    System.out.print("Nearby words: ");
    SortableScore[] arr = new SortableScore[scored.size()];
    arr = scored.toArray(arr);
    Arrays.sort(arr);
    for( SortableScore item : arr )
      System.out.print("  " + item.key() + " " + item.score());
    System.out.println();
    
    return arr;
  }
  
  public static List<String> nearbyWords(List<String> tokenlist, Collection<String> clusterTokens, ScoreCache cache) {
    SortableScore[] arr = nearbyWordsScored(tokenlist, clusterTokens, cache, false);
    List<String> neighbors = new ArrayList<String>();
    for( SortableScore scored : arr )
      neighbors.add(scored.key());
    return neighbors;
  }
  
  public static boolean clustersOverlap(Collection<String> cluster1, Collection<String> cluster2) {
    if( cluster1 == null || cluster2 == null )
      return false;
    
    int overlap = 0;
    for( String token : cluster1 ) {
      if( cluster2.contains(token) ) 
        overlap++;
    }

    if( overlap > (cluster1.size()/2) )
      return true;
    else
      return false;
  }

  /**
   * Find the three topics that are KIDNAP, BOMBING and ATTACK, return their indices.
   */
  public static int[] getClusterIDs(ReadingCluster[] clusters) {
    int[] ids = new int[3];
    for( int i = 0; i < ids.length; i++ ) ids[i] = -1;
    int id = 0;

    for( ReadingCluster cluster : clusters ) {
      if( cluster.contains("v-kidnap") && cluster.contains("v-release") )
        ids[0] = id;
      else if( cluster.contains("n-bomb") && (cluster.contains("v-explode") || cluster.contains("v-go_off")) )
        ids[1] = id;
      else if( cluster.contains("v-attack") )
        ids[2] = id;
      id++;
    }

    // check that they've all been set.
    for( int i = 0; i < ids.length; i++ ) { 
      if( ids[i] == -1 ) {
        System.out.println("ERROR in ClusterMUC, couldn't find 3 main clusters: " + Arrays.toString(ids));
        System.exit(-1);
      }
    }
    return ids;  
  }

  public static boolean isNovelCluster(List<String> cluster, List<SortableObject<List<String>>> scores) {
    for( SortableObject<List<String>> score : scores )
      if( clustersOverlap(cluster, score.key()) )
        return false;
    return true;
  }
  
  public static boolean isNovelCluster(ReadingCluster cluster, List<ReadingCluster> clusters) {
    for( ReadingCluster other : clusters )
      if( clustersOverlap(cluster.getTokens(), other.getTokens()) )
        return false;
    return true;
  }

  public static SortableObject<List<String>>[] removeDuplicateClusters(SortableObject<List<String>>[] scores) {
    List<SortableObject<List<String>>> trimmed = new ArrayList<SortableObject<List<String>>>();
    for( SortableObject<List<String>> score : scores ) {
      if( isNovelCluster(score.key(), trimmed) ) {
        trimmed.add(score);
        System.out.println("adding cluster: " + score.key());
      }
      else
        System.out.println("duplicate cluster: " + score.key());
    }
    SortableObject<List<String>>[] arr = new SortableObject[trimmed.size()];
    return trimmed.toArray(arr);
  }

  public static ReadingCluster[] removeSmallClusters(ReadingCluster[] scores, int trimSize) {
    List<ReadingCluster> trimmed = new ArrayList<ReadingCluster>();
    for( ReadingCluster cluster : scores ) {
      if( cluster.size() > trimSize )
        trimmed.add(cluster);
      else
        System.out.println("trimming small cluster: " + cluster);
    }
    ReadingCluster[] arr = new ReadingCluster[trimmed.size()];
    return trimmed.toArray(arr);
  }
  
  public static ReadingCluster[] removeDuplicateClusters(ReadingCluster[] scores) {
    List<ReadingCluster> trimmed = new ArrayList<ReadingCluster>();
    for( ReadingCluster cluster : scores ) {
      if( isNovelCluster(cluster, trimmed) ) {
        trimmed.add(cluster);
        System.out.println("adding cluster: " + cluster);
      }
      else
        System.out.println("duplicate cluster: " + cluster);
    }
    ReadingCluster[] arr = new ReadingCluster[trimmed.size()];
    return trimmed.toArray(arr);
  }

  public static float scoreWithCluster(String token, List<String> clusterList, ScoreCache cache,
      IDFMap domainIDF) {
    float score = 0.0f;
    // ** SKIP WORDS WITH LOW TRAINING COUNTS? **
    if( domainIDF.getDocCount(token) >= 5) {
      // Score the word with the cluster.
      if( !clusterList.contains(token) ){
        Set<String> wrapper = new HashSet<String>();
        wrapper.add(token);
        //        System.out.println("new");
        score = SingleLinkSimilarity.computeNewLinksClusterSimilarity(clusterList, wrapper, cache);
      } 
      // Score the cluster word by removing it, and score with remaining cluster.
      else {
        Set<String> wrapper = new HashSet<String>();
        wrapper.add(token);
        List<String> cloned = new ArrayList<String>(clusterList);
        cloned.remove(token);
        //        System.out.println("old");
        score = SingleLinkSimilarity.computeNewLinksClusterSimilarity(cloned, wrapper, cache);
        // If the cluster was of size one!
        if( cloned.size() == 0 )
          score = 10.0f;
        // Give the score a boost.  Since we compare it to n-1 elements in the cluster, it is
        // actually at a disadvantage to words not in the cluster who compare to n elements.
        score *= 1.5f;
      }
    } 
    else System.out.println("Skipping low count token " + token);
    return score;
  }

  public static void scaleToUnit(ReadingCluster[] clusters) {
    double max = 0.0;
    double min = Double.MAX_VALUE;
    for( ReadingCluster cluster : clusters ) {
      double value = cluster.score();
      if( value > max ) max = value;
      if( value < min ) min = value;
    }

    double range = max - min;

    // Scale between [0,1]
    for( ReadingCluster cluster : clusters ) {
      double value = cluster.score();
      double scaledValue = (value - min) / range;
      cluster.setClusterScore(scaledValue);
    }
  }
}
