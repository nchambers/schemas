package nate.reading;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import nate.CountTokenPairs;
import nate.EntityMention;
import nate.IDFMap;
import nate.util.SortableObject;
import nate.util.Util;
import nate.util.WordNet;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TypedDependency;
//import scala.collection.Iterable;
//import scalanlp.util.Index;
//import edu.stanford.nlp.tmt.lda.GibbsLDA;

/**
 * 
 * @author chambers
 *
 */
public class TopicsMUC {
  List<Map<String,Double>> _topics = new ArrayList<Map<String, Double>>();
//  GibbsLDA _model = null;
  int _numTopics = 100;

  // The cutoff that is used when the topic is trimmed to remove all words whose mass
  // is below this threshold.  This is used to cluster syntactic arguments into semantic
  // roles, and we don't want to cluster less likely words' slots.
  public static double WORD_SCORE_CUTOFF = 0.01;

  
  public TopicsMUC() {
  }

  public TopicsMUC(String dirname) {
    ldaFromFile(dirname);
  }
    
  public void ldaFromFile(String dirname) {
//    _model = GibbsLDA.loadModel(new File(dirname));
  }
  
  /**
   * 
   * @param document Words separated by spaces.
   * @return An array of topic IDs with probabilities, sorted by the highest.
   */
  public SortableObject[] labelDocumentWithTMTTopics(String document) {
    System.out.println("** TMT document-level topic probs **");
    double[] topicProbs = new double[10]; // fake holder
   
    SortableObject[] scores = new SortableObject[topicProbs.length];
    
    int i = 0;
    for( double prob : topicProbs ) {
      System.out.printf("  topic %d,%.3f ", i, prob);
      scores[i] = new SortableObject(prob, new Integer(i));
      i++;
    }
    System.out.println();
    Arrays.sort(scores);
    return scores;
  }
  
  /**
   * This function gets the per-word topic distributions over the entire document, and assigns
   * them at the sentence level.  It doesn't work real well if you want individual sentences to
   * stand out because the best document-topics are going to be the best term-documents no matter
   * what the terms are...
   * @param fulltext  The entire text.
   * @param sentences The individual sentences, which should be the same as the fulltext.
   * @return A List of frames for each sentence.
   */
  public List<ScoredFrame[]> labelSentencesWithTMTTopics(String fulltext, List<String> sentences, Frame[] frames) {
    List<ScoredFrame[]> sentenceScores = new ArrayList<ScoredFrame[]>();
    
    // If I want to get per word topics ... the code ignores tokens that didn't appear
    // in training.  So we need to tokenize our document in the same way that the Scala
    // code tokenizes.  This line gives us those tokens.
//    Index<String> index = _model.termIndex();
//    double[][] termprobs = _model.inferPerWordTopicDistribution(fulltext);
    
    int doci = 0;
    for( String sentence : sentences ) {
//      scala.collection.Iterable<String> ldaTokensUsed = _model.tokenizer().get().apply(sentence);
//      scala.collection.Iterator<String> ldaiter = ldaTokensUsed.iterator();
      Map<Integer,Double> sentenceTopicProbs = new HashMap<Integer, Double>();
      
      // DEBUG
//      System.out.println("real sentence: " + sentence);
//      System.out.println("num lda tokens: " + ldaTokensUsed.size());
      
    }
    
    System.out.println("Returning sentence topic scores..." + sentenceScores.size());
    return sentenceScores;
  }
  
  public List<ScoredFrame[]> labelSentencesWithTMTTopics(List<String> sentences, Frame[] frames) {
    List<ScoredFrame[]> sentenceScores = new ArrayList<ScoredFrame[]>();

    return sentenceScores;
  }
    

  /**
   * Get the tokens' probabilities from a single topic.
   * @param topicid The ID of the topic we want.
   * @return A map from token strings to their probabilities.
   */
  public Map<String,Double> topicTokenProbs(int topicid) {
    Map<String,Double> probs = new HashMap<String, Double>();
    return probs;
  }

  /**
   * Finds the top Topics for this document, returns them in order.
   * @param trees
   * @param alldeps
   * @param entities
   * @param topics
   * @param wordnet
   * @param generalIDF
   * @param includeDependents
   * @return
   */
  public List<ScoredFrame> labelDocumentWithTMTTopics(Frame[] frames,
      ProcessedDocument doc,
      WordNet wordnet,
      IDFMap generalIDF,
      boolean includeDependents,
      int numDocClusters,
      int numSentClusters) {
    List<String> docSentences = new ArrayList<String>();
    String fulltext = "";

    int sid = 0;
    for( Tree tree : doc.trees() ) {
      List<TypedDependency> sentdeps = doc.deps.get(sid);
      // We use BASE, because the given topics fully specify what tokens are in the topics ... hence
      // it is which topics are loaded at runtime that really determine which tokens are taken
      // into account.  If a verb-only topic is loaded, then only verbs are scored, all else get zeros.
      List<String> tokens = CountTokenPairs.getKeyTokens(sid, tree, sentdeps, doc.ners, wordnet, null, CountTokenPairs.BASE, includeDependents, false, false, true);
      //tokens = ClusterMUC.removeLowIDF(tokens, generalIDF, 1.2f);
      String sentence = stringify(tokens);
      docSentences.add(sentence);
      fulltext += " " + sentence;

      System.out.println("(topictmt) sentence key tokens: " + sentence);
      sid++;
    }

    List<ScoredFrame> best = new ArrayList<ScoredFrame>();
    Set<Integer> bestids = new HashSet<Integer>();

    // *****
    // Get the best topics for the whole document.
    SortableObject[] topicIDScores = labelDocumentWithTMTTopics(fulltext);
    for( int i = 0; i < Math.min(numDocClusters, topicIDScores.length); i++ ) {
      SortableObject scored = topicIDScores[i];
      int topicID = (Integer)scored.key();
//      ScoredFrame clusty = new ScoredFrame(scored.score(), new Frame(topicID, topicTokenProbs(topicID), 0.0));
      if( scored.score() > 0.0 ) {
        ScoredFrame clusty = new ScoredFrame(scored.score(), new Frame(0.0, frames[topicID]));
        System.out.printf("Top %d\t%d\t%.5f\n", i, clusty.frame().getID(), clusty.score());
        //System.out.printf("Top %d score %.4f: %s\n", i, scored.score(), clusty);
        best.add(clusty);
        bestids.add(clusty.frame().getID());
      }
    }
    
    // NOTE: this helps a little in recall, but F1 improvement isn't all that much ... takes a long time to run!
    // *****
    // Get sentence level best topics.
    if( numSentClusters > 0 ) {
      List<ScoredFrame[]> sentenceScores = labelSentencesWithTMTTopics(docSentences, frames);
      List<ScoredFrame[]> sentenceScores2 = labelSentencesWithTMTTopics(fulltext, docSentences, frames);
      List<ScoredFrame> bestBySentence = ClusterMUC.significantSentenceWins(sentenceScores);
      List<ScoredFrame> consistentWinners = ClusterMUC.consistentSentenceWins(sentenceScores);
      ClusterMUC.mergeClusterLists(bestBySentence, consistentWinners);

      ScoredFrame[] misc = new ScoredFrame[bestBySentence.size()];
      for( int i = 0; i < misc.length; i++ ) misc[i] = (bestBySentence.get(i));
      Arrays.sort(misc);

      for( int i = 0; i < misc.length; i++ )
        System.out.println("misc item: " + misc[i].frame().getID());
      System.out.println("best size " + best.size() + " numDoc " + numDocClusters + " numSent " + numSentClusters);
      
      for( int id : bestids ) System.out.println("bestid: " + id);
      
      // Add these in sorted order.    
      for( int i = 0; i < misc.length; i++ ) {
        // Exit now if we have added enough topics already.
        if( best.size() == (numDocClusters + numSentClusters) )
          break;
        // Don't re-add topics already on our list.
        if( !bestids.contains(misc[i].frame().getID()) ) {
          best.add(misc[i]);
          bestids.add(misc[i].frame().getID());
          System.out.println("Misc " + i + " topic: " + misc[i].frame().getID());
        }
      }
    }
    
    return best;
  }
  
  /**
   * Sentence is represented by a list of tokens.  These could be a subset of the
   * actual sentence...just give it the important tokens you want included in the
   * scoring function.
   * @return An array of scored objects, the objects are Integer IDs of the clusters, 
   *         the ID is the array position of the given cluster array.
   
  public static ReadingCluster[] labelSentenceWithTopics(Collection<String> tokens, 
      List<ReadingCluster> topics) {
    int numTopics = topics.size();
    ReadingCluster[] labels = new ReadingCluster[numTopics];
    int i = 0;

    for( ReadingCluster topic : topics ) {
      double score = 0.0f;
      for( String token : tokens ) {
        // TODO: summing probabilities doesn't make total sense here
        //       ...but don't want to multiply zero probabilities
        if( topic.contains(token) )
          score += topic.getTokenScore(token);
      }
      int numtokens = tokens.size();
      labels[i++] = new ReadingCluster(((numtokens == 0) ? 0.0f : (score/(float)numtokens)), topic);
    }

    Arrays.sort(labels);
    int xx = 0;
    for( ReadingCluster item : labels ) {
      System.out.printf("  scored %.4f\t%s\n", item.score(), item.getTokens());
      if( xx++ == 3 ) break;
    }

    return labels;
  }
  
  /**
   * @return A list of arrays, one array for each sentence.  The array contains the cluster
   *         scores for that particular sentence.  The list is as long as the number of sentences.
   *         Each SortableObject has an Integer ID for the cluster it scores, the IDs are the
   *         array positions in the given clusters array.
   
  public static List<ReadingCluster[]> labelDocumentWithTopics(Collection<Tree> trees,
                 Vector<Vector<TypedDependency>> alldeps,
                 Vector<EntityMention> entities,
                 List<ReadingCluster> topics,
                 WordNet wordnet,
                 IDFMap generalIDF,
                 boolean includeDependents) {
    List<ReadingCluster[]> labelScoresBySentence = new ArrayList<ReadingCluster[]>();
    List<List<String>> docTokens = new ArrayList();
    
    int sid = 0;
    for( Tree tree : trees ) {
      Vector<TypedDependency> sentdeps = alldeps.elementAt(sid);
      // We use BASE, because the given topics fully specify what tokens are in the topics ... hence
      // it is which topics are loaded at runtime that really determine which tokens are taken
      // into account.  If a verb-only topic is loaded, then only verbs are scored, all else get zeros.
      List<String> tokens = CountTokenPairs.getKeyTokens(sid, tree, sentdeps, wordnet, null, CountTokenPairs.BASE, includeDependents);
//      tokens = ClusterMUC.removeLowIDF(tokens, generalIDF, 1.2f);
      docTokens.add(tokens);
      
      System.out.println("(topic) sentence key tokens: " + tokens);
      labelScoresBySentence.add(labelSentenceWithTopics(tokens, topics));
      sid++;
    }

    return labelScoresBySentence;
  }
  */
  
  public static double scoreWithTopic(String token, Frame topic, IDFMap domainIDF) {
    double score = 0.0f;
    // ** SKIP WORDS WITH LOW TRAINING COUNTS? **
    if( domainIDF.getDocCount(token) >= 5) {
      // Score the word with the cluster.
      if( topic.contains(token) ) {
        score = topic.tokenScore(token);
      }
    } 
    //    else System.out.println("Skipping low count token " + token);
    return score;
  }
  
  /**
   * Read the topics from the model file, then finds the three topics that are 
   * KIDNAP, BOMBING and ATTACK, and return their indices.
   */
  public int[] getTMTTopicIDs() {
    // Set the global topics.
    _topics.clear();
    for (int topicID = 0; topicID < _numTopics; ++topicID) {
      Map<String,Double> tokenProbs = topicTokenProbs(topicID);
      _topics.add(tokenProbs);
    }
    return getTopicIDs();
  }
  
  /**
   * Find the three topics that are KIDNAP, BOMBING and ATTACK, return their indices.
   */
  public int[] getTopicIDs() {
    int[] ids = new int[3];
    for( int i = 0; i < ids.length; i++ ) ids[i] = -1;
    int id = 0;
    
    // Find KIDNAP, BOMBING
    for( Map<String,Double> topic : _topics ) {
      Double kidnap = topic.get("v-kidnap");
      Double release = topic.get("v-release");
      if( kidnap != null && kidnap > 0.05f && release != null && release > 0.02f ) {
        ids[0] = id;
      }
      Double bomb = topic.get("n-bomb");
      Double explode = topic.get("v-explode");
      Double go_off = topic.get("v-go_off");
      if( bomb != null && bomb > 0.05f && explode != null && explode > 0.0f && go_off != null && go_off > 0.0f ) {
        ids[1] = id;
      }
      id++;
    }
    
    // Find the highest ATTACK topic...
    id = 0;
    double bestAttack = -1.0;
    int bestID = -1;
    for( Map<String,Double> topic : _topics ) {
      Double attack = topic.get("v-attack");
      if( attack != null && attack > bestAttack ) {
        bestAttack = attack;
        bestID = id;
      }
      id++;
    }
    ids[2] = bestID;
    
    // check that they've all been set.
    for( int i = 0; i < ids.length; i++ ) { 
      if( ids[i] == -1 ) {
        System.out.println("ERROR in TopicsMUC, couldn't find 3 main topics: " + Arrays.toString(ids));
        System.exit(-1);
      }
    }
    
    return ids;
  }
  
  /**
   * Reads the output of DanR's LDA code into memory.  The output is just the list of
   * topics that it prints to the command line.
   * @param filename location of the file with the topics.
   
  public void fromFile(String filename) {
    System.out.println("TopicsMUC reading file " + filename);
    // Open the file.
    BufferedReader in = null;
    try {
      in = new BufferedReader(new FileReader(filename));
    } catch( Exception ex ) { 
        System.out.println("Error opening file: " + filename);
        System.exit(-1);
    }

    // Clear the current topics in memory.
    _topics.clear();

    // Read in the topics from file.
    try {
      String line = in.readLine();
      while( (line = in.readLine()) != null ) {
        // e.g. "Topic 10: n-struggle[0.25] n-path[0.16] v-seek[0.06] n-movement[0.05] v-prevent[0.05]"
        if( line.startsWith("Topic ") ) {
          Map<String,Double> probs = new HashMap<String, Double>();
          _topics.add(probs);
//          int spacepos = line.indexOf(' ', 7);
//          int topicnum = Integer.valueOf(line.substring(7, spacepos));
          String parts[] = line.substring(line.indexOf(':')+2).split(" ");
          for( String part : parts ) {
            int left = part.indexOf('[');
            int right = part.indexOf(']');
            String token = part.substring(0,left);
            double prob = Double.valueOf(part.substring(left+1,right));
            // Don't bother putting tokens in memory with 0 probability.
            if( prob > 0.0f )
              probs.put(token, prob);
          }
        }
      }
    } catch( Exception ex ) { 
        ex.printStackTrace(); 
        System.exit(1);
    }
  }
*/
  
  private String stringify(List<String> tokens) {
    String str = "";
    int i = 0;
    for( String token : tokens ) {
      if( i++ == 0 ) str = token;
      else str += " " + token;
    }
    return str;
  }
  
//  public List<Map<String,Double>> getTopics() {
//    return _topics;
//  }

  public List<ReadingCluster> getTopics() {
    List<ReadingCluster> thetopics = new ArrayList<ReadingCluster>();
    for (int topicID = 0; topicID < _numTopics; ++topicID) {
      Map<String,Double> tokenProbs = topicTokenProbs(topicID);
      thetopics.add(new ReadingCluster(topicID, tokenProbs));
    }
    return thetopics;
  }
  
  public int numTopics() {
    return _numTopics;
  }
  
  /**
   * Given a list of word scores, presumably from a topic's word distribution, trim the
   * list to keep only those words that are highly probable.
   * @return A new Map that is a subset of the given one.
   */
  public static Map<String,Double> trimTopicWords(Map<String,Double> tokenScores) {
    Map<String,Double> newScores = new HashMap<String,Double>();
    for( Map.Entry<String,Double> entry : tokenScores.entrySet() ) {
      if( entry.getValue() > WORD_SCORE_CUTOFF )
        newScores.put(entry.getKey(), entry.getValue());
    }
    return newScores;
  }
}
