package nate.reading;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import nate.CountTokenPairs;
import nate.CountVerbDepCorefs;
import nate.CountVerbDeps;
import nate.EntityMention;
import nate.GigaDocReader;
import nate.GigawordHandler;
import nate.GigawordProcessed;
import nate.IDFMap;
import nate.MUCHandler;
import nate.NERSpan;
import nate.cluster.ClusterUtil;
import nate.narrative.EventPairScores;
import nate.util.HandleParameters;
import nate.util.Ling;
import nate.util.PMICalculator;
import nate.util.SortableScore;
import nate.util.TreeOperator;
import nate.util.Util;
import nate.util.WordNet;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TypedDependency;

/**
 *
 * We calculate the IDF scores for verb-dependency relations by
 * using the verb-deps counts files.  These files includes total
 * occurrence counts as well as document appearance counts.
 *
 * -mucdocs
 * MUC file with full text documents.
 *
 * -muckeys
 * MUC template file that matches -mucdocs.
 *
 * -parsed
 * Parse trees of the MUC documents.
 *
 * -deps
 * Dependencies of the MUC documents.
 *
 * -events
 * Coref info of the MUC documents.
 *
 * -ner
 * NER labels for the MUC documents.
 *
 * -domaincounts
 * Counts of verb-deps within the MUC domain.
 *
 * -corpuscounts
 * Counts of verb-deps over an entire corpus.
 *
 * -domainidf
 * Domain IDF scores. POS tagged format.
 *
 * -corpusidf
 * Corpus IDF scores. POS tagged format.
 * 
 * -initialidf
 * IDF scores of words in the first couple of sentences. POS format.
 *
 * -paircounts
 * File of token pair counts from the domain...CountTokenPairs.java
 *
 * -type
 * The type of tokens to use in clustering: e.g. vb, vbnn, vbnom
 *  
 */
public class StatisticsDeps {
  String _inputFile;
  CountVerbDepCorefs _domainCounts;
  CountVerbDeps _corpusCounts;
  CountTokenPairs _tokenPairCounts;
  Map<String,Integer> _templateCounts;
  Map<String,Integer> _otherCounts;
  Map<String,Integer> _posCounts;
  Map<String,Integer> _posCountsEntitySpecific;
  Map<Integer,Integer> _corefCountsHuman;
  Map<Integer,Integer> _corefCountsOrg;
  Map<Integer,Integer> _corefCountsPhys;
  Map<Integer,Integer> _mentionCountsHuman;
  Map<Integer,Integer> _mentionCountsOrg;
  Map<Integer,Integer> _mentionCountsPhys;
  Map<Integer,Integer> _mucMentionCountsHuman;
  Map<Integer,Integer> _mucMentionCountsOrg;
  Map<Integer,Integer> _mucMentionCountsPhys;
  Map<NERSpan.TYPE,Integer> _nerCounts;
  Map<String,Double> _domainLikelihoodRatios;
  MUCKeyReader _mucKeyReader;
  MUCHandler _mucDocuments;
  ProcessedData _dataReader;
//  GigawordHandler _parseReader;
//  GigaDocReader _depsReader;
//  GigaDocReader _corefReader;
//  GigaDocReader _nerReader;
  WordNet _wordnet;
  String _matlabOutput = "output.m";
  PrintWriter _matlabOut;
  DomainVerbDetector _detector;
  int _tokenType = CountTokenPairs.VERBS_AND_NOMINALS;


  public StatisticsDeps(String args[]) {
    HandleParameters params = new HandleParameters(args);

    // Read the counts files into memory.
    _domainCounts = new CountVerbDepCorefs();
    _domainCounts.fromFile(params.get("-domaincounts"));
    System.out.println("Domain Counts total docs = " + _domainCounts.getTotalDocs());
    _corpusCounts = new CountVerbDeps();
    _corpusCounts.fromFile(params.get("-corpuscounts"));
    System.out.println("Corpus Counts total docs = " + _corpusCounts.getTotalDocs());
    _tokenPairCounts = new CountTokenPairs();
    _tokenPairCounts.fromFile(params.get("-paircounts"));

    // MUC Templates.
    _mucKeyReader = new MUCKeyReader(params.get("-muckeys"));

    // MUC Documents.
    _mucDocuments = new MUCHandler(params.get("-mucdocs"));

    // Parse handler.
    _dataReader = new ProcessedData(params.get("-parsed"), params.get("-deps"), params.get("-events"), params.get("-ner"));

//    _parseReader = new GigawordProcessed(params.get("-parsed"));
//    _depsReader  = new GigaDocReader(params.get("-deps"));
//    _corefReader = new GigaDocReader(params.get("-events"));
//    _nerReader = new GigaDocReader(params.get("-ner"));

    // Type of tokens to cluster.
    if( params.hasFlag("-type") ) 
      _tokenType = CountTokenPairs.getType(params.get("-type"));
    System.out.println("tokenType = " + _tokenType);
    
    // WordNet
    _wordnet = new WordNet(params.get("-wordnet"));

    // Detect key words for the domain.
    _detector = new DomainVerbDetector(params.get("-domainidf"), params.get("-corpusidf"), params.get("-initialidf"));
    System.out.println("domainIDF = " + params.get("-domainidf"));
    System.out.println("corpusIDF = " + params.get("-corpusidf"));
    System.out.println("initialIDF = " + params.get("-initialidf"));

    // Matlab Debugging for graphs...
    try {
      _matlabOut = new PrintWriter(new BufferedWriter(new FileWriter(_matlabOutput, false)));
    } catch( Exception ex ) { ex.printStackTrace(); }

    _templateCounts = new HashMap();
    _otherCounts = new HashMap();
    _posCounts = new HashMap();
    _posCountsEntitySpecific = new HashMap();
    _corefCountsHuman = new HashMap();
    _corefCountsOrg = new HashMap();
    _corefCountsPhys = new HashMap();
    _mentionCountsHuman = new HashMap();
    _mentionCountsOrg = new HashMap();
    _mentionCountsPhys = new HashMap();
    _mucMentionCountsHuman = new HashMap();
    _mucMentionCountsOrg = new HashMap();
    _mucMentionCountsPhys = new HashMap();
    _nerCounts = new HashMap();
  }

  /**
   * Add n instances of a score to a list of seen scores for the given
   * relation.  Each relation has a list of scores in the given map.
   */
  private void appendRelnScore(String reln, Double score, int repeated, 
      Map<String,List<Double>> items) {
    List<Double> occ = items.get(reln);
    if( occ == null ) {
      occ = new LinkedList();
      items.put(reln, occ);
    }
    for( int i = 0; i < repeated; i++ ) 
      occ.add(new Double(score));
  }

  /**
   * Calculate the likelihood ratio between two corpora using the counts
   * in their two IDFMaps.
   */
  public static Map<String,Double> likelihoodRatios(IDFMap map1, IDFMap map2) {
    double maxRatio = 50.0;
    Map<String,Double> ratios = new HashMap();

    for( String word : map1.getWords() ) {
      double prob1 = (double)map1.getFrequency(word) / (double)map1.totalCorpusCount();
      double prob2 = (double)map2.getFrequency(word) / (double)map2.totalCorpusCount();
      double ratio = maxRatio;
      if( prob2 > 0.0 ) ratio = prob1 / prob2;
      ratios.put(word, ratio);
    }
    return ratios;
  }

  private SortableScore[] sortMap(Map<String,Double> scores) {
    SortableScore[] sorted = new SortableScore[scores.size()];
    int i = 0;
    for( Map.Entry<String,Double> entry : scores.entrySet() )
      sorted[i++] = new SortableScore(entry.getValue(), entry.getKey());
    Arrays.sort(sorted);
    return sorted;
  }

  /**
   * Takes counts in the token pairs format (not shared coref counts).
   * Converts to PMI using the domain's IDF counts of individual tokens.
   * @param keeplist List of words to compute pairs over, skip all others.
   *                 Set to null if you want to compute all pairs.
   * @param docCountCutoff The number of times a token needs to appear in the corpus to be included.                
   * @param pairCountCutoff The min number of times a pair was seen to include in the PMI calculations.
   */
  public static EventPairScores pairCountsToPMI(CountTokenPairs paircounts, IDFMap domainIDF,
      Set<String> keeplist, int docCountCutoff, int pairCountCutoff) {
    return pairCountsToPMI(paircounts, domainIDF, keeplist, docCountCutoff, (double)pairCountCutoff);
  }
  public static EventPairScores pairCountsToPMI(CountTokenPairs paircounts, IDFMap domainIDF,
      Set<String> keeplist, int docCountCutoff, double pairCountCutoff) {
    EventPairScores cache = new EventPairScores();
    // Calculate PMI scores for pairs.
    System.out.println("calculating pmis...");
    for( String lemma1 : paircounts.floatKeySet() ) {
      if( keeplist == null || keeplist.contains(lemma1) ) {
        if( domainIDF.getDocCount(lemma1) > docCountCutoff ) {
          for( String lemma2 : paircounts.floatKeySet(lemma1) ) {
            if( keeplist == null || keeplist.contains(lemma2) ) {
              if( domainIDF.getDocCount(lemma2) > docCountCutoff ) {
                double pairCount = paircounts.getCount(lemma1, lemma2);
                if( pairCount > pairCountCutoff ) {
                  int freq1 = domainIDF.getDocCount(lemma1);
                  int freq2 = domainIDF.getDocCount(lemma2);
                  if( freq1 != 0 && freq2 != 0 ) {
                    double pmi = PMICalculator.calculatePMI(pairCount, paircounts.getTotalCount(),
                        freq1, freq2, domainIDF.totalCorpusCount(), false);
                    int min = (freq1 < freq2 ? freq1 : freq2);
                    pmi *= (double)((double)min / (double)(min + 10));
                    cache.addScore(lemma1, lemma2, (float)pmi);
                  }
                }
              }
            }
          }
        }
      }
    }
    return cache;
  }

  /*
   * Takes counts in the token pairs format (not shared coref counts).
   * Converts to conditional probability scores using the domain's IDF counts of individual tokens.
   * NOTE: this is a symmetric score, so it is not exactly conditional probability.
   *       It is the count of pairs divided by the max count of either individual token.
   * @param keeplist List of words to compute pairs over, skip all others.
   *                 Set to null if you want to compute all pairs.
   * @param cutoff The min number of times a pair was seen to include in the PMI calculations.
   */
  public static EventPairScores pairCountsToConditionalProb(CountTokenPairs paircounts, IDFMap domainIDF,
      Set<String> keeplist, int cutoff) {
    return pairCountsToConditionalProb(paircounts, domainIDF, keeplist, (double)cutoff);
  }
  public static EventPairScores pairCountsToConditionalProb(CountTokenPairs paircounts, IDFMap domainIDF,
      Set<String> keeplist, double cutoff) {
    EventPairScores cache = new EventPairScores();
    // Calculate probability scores for pairs.
    System.out.println("calculating conditional probs...");
    for( String lemma1 : paircounts.floatKeySet() ) {
      if( keeplist == null || keeplist.contains(lemma1) ) {
        for( String lemma2 : paircounts.floatKeySet(lemma1) ) {
          if( keeplist == null || keeplist.contains(lemma2) ) {
            double pairCount = paircounts.getCount(lemma1, lemma2);
            if( pairCount > cutoff ) {
              int freq1 = domainIDF.getDocCount(lemma1);
              int freq2 = domainIDF.getDocCount(lemma2);
              if( freq1 != 0 && freq2 != 0 ) {
                double prob = pairCount / Math.max(freq1,freq2);
                //		System.out.printf("%s\t%s\t%.2f\n", lemma1, lemma2, prob);
                cache.addScore(lemma1, lemma2, (float)prob);
              }
            }
          }
        }
      }
    }
    return cache;
  }


  /**
   * @return The average salience score of the cluster members.
   */
  private double salienceScoreCluster(List<String> cluster) {
    double sum = 0.0;
    for( String member : cluster ) {
      double salience = _detector.salienceScore(member, 0, _domainCounts);
      double tmodProb = _detector.probabilityOfTmodGivenWord(member, _domainCounts);    
      sum += salience * tmodProb;
    }
    return sum / (double)cluster.size();
  }

  /**
   * @return The average likelihoodratio score of the cluster members.
   */
  private double ratioScoreCluster(List<String> cluster, Map<String,Double> ratios) {
    double sum = 0.0;
    for( String member : cluster ) {
      Double score = ratios.get(member);
      if( score != null )
        sum += score * Math.log(_detector._domainIDF.getDocCount(member));
    }
    return sum / (double)cluster.size();
  }

  /**
   * @return The average likelihoodratio score of the cluster members.
   */
  private double initialScoreCluster(List<String> cluster) {
    double sum = 0.0;
    for( String member : cluster ) {
      float score = _detector._initialWords.scoreToken(member, 200);
      sum += score;
    }
    return sum / (double)cluster.size();
  }

  /**
   * Retrieves certain tokens deemed important to the domain.
   * - Only verbs and nouns that are Events in WordNet are included.
   * - There are document cutoffs between [2,20] based on domain corpus size.
   * - Words are ignored if their likelihood ratio is too low against a general corpus.
   *     - This is set very low for the *all* set, only a couple are removed.
   */
  public static Set<String> keyDomainTokens(IDFMap domainIDF, IDFMap generalIDF, WordNet wordnet,
      CountVerbDepCorefs domainCounts, int tokenType) {
    Set<String> keyTokens = new HashSet<String>();

    // IDF cutoff from large general corpus.
    float generalIDFCutoff = 1.3f;

    // Words in consideration, document cutoff.
    int docCountCutoff = (int)(.03f * (float)domainIDF.numDocs());
    if( docCountCutoff < 2 ) docCountCutoff = 2;
//    if( docCountCutoff > 10 ) docCountCutoff = 10;
    if( docCountCutoff > 5 ) docCountCutoff = 5;
    System.out.println("keyDomainTokens docCountCutoff = " + docCountCutoff);

    // Calculate Likelihood Ratios
    Map<String,Double> ratios = likelihoodRatios(domainIDF, generalIDF);

    // Choose the words with high counts.
    for( String word : domainIDF.getWords() ) {
      char pos = word.charAt(0);
      String lemma = word.substring(2);
      if( domainIDF.getDocCount(word) > docCountCutoff ) {
        if( CountTokenPairs.tokenMatchesDesiredType(lemma, pos, wordnet, tokenType) ) {
          if( generalIDF.get(word) > generalIDFCutoff ) {
//            double salience = DomainVerbDetector.salienceScore(word, docCountCutoff, domainCounts);
//            System.out.println(word + "\tratio=" + ratios.get(word) + "\tdocs=" + domainIDF.getDocCount(word) + "\tsalience=" + salience);
            // Skip words without high likelihood ratios.
            //	  if( ratios.get(word) > 2.0 ) { // 2.0 for kidnap, bombing
            //	  if( ratios.get(word) > 1.6 ) { // good for attack?
            if( ratios.get(word) > 0.2 ) { // good for all docs??
              keyTokens.add(word);
              //	      listRatios.add(new SortableScore(ratios.get(word), word));
            }
//            else System.out.println("skipping ratio: " + word);
            //	}
            //	else System.out.println("skipping salience: " + word);
          } //else System.out.println("skipping general idf: " + word);
        }// else System.out.println("skipping unwanted word type: " + word);
      } //else System.out.println("skipping doc occurrence: " + word);
    }

    return keyTokens;
  }

  /**
   * Cluster the pair scores given in the token pair counts file.
   * @return Clusters (lists of strings) with their cluster scores, sorted.
   */
  private ReadingCluster[] clusterPairs() {
    Map<String,Double> ratios = likelihoodRatios(_detector._domainIDF, _detector._generalIDF);
    List<SortableScore> listRatios = new ArrayList();
    float generalIDFCutoff = 1.3f;

    // Words in consideration, document cutoff.
    int docCountCutoff = (int)(.03f * (float)_detector._domainIDF.numDocs());
    if( docCountCutoff < 2 ) docCountCutoff = 2;

    // Pairs in consideration, pair count cutoff.
    int pairCountCutoff = (int)(.01f * (float)_detector._domainIDF.numDocs());
    if( pairCountCutoff < 2 ) pairCountCutoff = 2;

    if( docCountCutoff > 5 ) docCountCutoff = 5;
    if( pairCountCutoff > 5 ) pairCountCutoff = 5;

    System.out.println("docCountCutoff = " + docCountCutoff);
    System.out.println("pairCountCutoff = " + pairCountCutoff);

    // Choose the words with high counts.
    Set<String> tokensLemmas = keyDomainTokens(_detector._domainIDF, _detector._generalIDF, _wordnet,
        _domainCounts, _tokenType);

    /*
    for( String word : _detector._domainIDF.getWords() ) {
      if( (word.charAt(0)=='v' && _detector._domainIDF.getDocCount(word) > docCountCutoff) ||
	  (word.charAt(0)=='n' && _detector._domainIDF.getDocCount(word) > docCountCutoff) ) {
	if( word.charAt(0)=='v' || _wordnet.isNounEvent(word.substring(2)) ) {
	  if( _detector._generalIDF.get(word) > generalIDFCutoff ) {
	    double salience = _detector.salienceScore(word, docCountCutoff, _domainCounts);

	    //	if( salience > 0.02f ) {
	    System.out.println(word + "\tratio=" + ratios.get(word) + "\tdocs=" + _detector._domainIDF.getDocCount(word) + "\tsalience=" + salience);
	    // Skip words without high likelihood ratios.
	    //	  if( ratios.get(word) > 2.0 ) { // 2.0 for kidnap, bombing
	    //	  if( ratios.get(word) > 1.6 ) { // good for attack?
	    if( ratios.get(word) > 0.2 ) { // good for all docs??
	      tokensLemmas.add(word);
	      listRatios.add(new SortableScore(ratios.get(word), word));
	    }
	    else System.out.println("skipping ratio: " + word);
	    //	}
	    //	else System.out.println("skipping salience: " + word);
	  } else System.out.println("skipping general idf: " + word);
	} else System.out.println("skipping non-event noun: " + word);
      } else System.out.println("skipping word: " + word);
    }
     */

    // DEBUG : top salience scores.
    System.out.println("Top Salience Words");
    List<String> topSalienceWords = _detector.detectWordsOnlyDiscourseSalience(_domainCounts, false);
    Util.firstN(topSalienceWords, 100);
    for( String w : topSalienceWords ) {
      if( (w.charAt(0)=='v' && _detector._domainIDF.getDocCount(w) > docCountCutoff) ||
          (w.charAt(0)=='n' && _detector._domainIDF.getDocCount(w) > docCountCutoff) )
        if( ratios.get(w) > 2.0 )
          System.out.println("good salience: " + w);
    }

    // Calculate PMI scores for pairs.
    EventPairScores cache = null;
    System.out.println("calculating pmis...");
    cache = pairCountsToPMI(_tokenPairCounts, _detector._domainIDF, tokensLemmas, docCountCutoff, pairCountCutoff);
    //    System.out.println("calculating conditional probs...");
    //    cache = pairCountsToConditionalProb(_tokenPairCounts, _detector._domainIDF,
    //					tokensLemmas, pairCountCutoff);

    // Put the set into a list form that can be indexed..
    List<String> tokenlist = new ArrayList();
    for( String token : tokensLemmas ) tokenlist.add(token);
    tokensLemmas.clear();

    // Cluster.
    List<ReadingCluster> clusters = ClusterMUC.hierarchicalCluster(tokenlist, cache, ClusterUtil.NEW_LINK, 40);

    // Word-Based Starter Clustering.
    int i = 0;
    //     System.out.println("Clustering tokens!");
    //     IncrementalClustering clusty = new IncrementalClustering();
    //     Map<String,List<String>> clusters = clusty.cluster(tokenlist, cache, ClusterUtil.NEW_LINK, 10);
    //     for( String main : clusters.keySet() ) {
    //       List<String> cluster = clusters.get(main);
    //       if( cluster.size() > 1 )
    // 	scores.add(new SortableObject(ClusterUtil.computeClusterScoreFull(cluster, cache),
    // 				      cluster));
    //     }

    // Sort the clusters by cluster score.
    ReadingCluster[] sortedClusters = new ReadingCluster[clusters.size()];
    sortedClusters = clusters.toArray(sortedClusters);
    Arrays.sort(sortedClusters);
    sortedClusters = ClusterMUC.removeDuplicateClusters(sortedClusters);
 
    // Score clusters by salience and likelihood ratio.
    ReadingCluster[] salienceScores = new ReadingCluster[sortedClusters.length];
    ReadingCluster[] ratioScores = new ReadingCluster[sortedClusters.length];
    ReadingCluster[] initialScores = new ReadingCluster[sortedClusters.length];
    i = 0;
    for( ReadingCluster cluster : sortedClusters ) {
      salienceScores[i] = new ReadingCluster(salienceScoreCluster(cluster.getTokens()), cluster);
      ratioScores[i] = new ReadingCluster(ratioScoreCluster(cluster.getTokens(), ratios), cluster);
      initialScores[i++] = new ReadingCluster(initialScoreCluster(cluster.getTokens()), cluster);
    }

    // Scale all scores to [0,1]
    ClusterMUC.scaleToUnit(sortedClusters);
    ClusterMUC.scaleToUnit(salienceScores);
    ClusterMUC.scaleToUnit(ratioScores);
    ReadingCluster[] mergedScores = new ReadingCluster[sortedClusters.length];
    i = 0;
    for( ReadingCluster cluster : sortedClusters ) {
      double merged = (cluster.score() + salienceScores[i].score() + ratioScores[i].score()) / 3;
      mergedScores[i++] = new ReadingCluster(merged, cluster);
    }

    System.out.println("**** Cluster Scores ****");
    for( ReadingCluster obj : sortedClusters )
      System.out.printf("**%.2f\t%s\n", obj.score(), obj.getTokens());
    System.out.println("**** Salience Scores ****");
    Arrays.sort(salienceScores);
    for( ReadingCluster obj : salienceScores )
      System.out.printf("**%.2f\t%s\n", obj.score(), obj.getTokens());
    System.out.println("**** Ratio Scores ****");
    Arrays.sort(ratioScores);
    for( ReadingCluster obj : ratioScores )
      System.out.printf("**%.2f\t%s\n", obj.score(), obj.getTokens());
    System.out.println("**** Merged Scores ****");
    Arrays.sort(mergedScores);
    for( ReadingCluster obj : mergedScores )
      System.out.printf("**%.2f\t%s\n", obj.score(), obj.getTokens());
    System.out.println("**** Initial Scores ****");
    Arrays.sort(initialScores);
    for( ReadingCluster obj : initialScores )
      System.out.printf("**%.2f\t%s\n", obj.score(), obj.getTokens());

    return mergedScores;
  }

  /**
   * @return A list of relations that were seen in the given slots.
   */
  private Collection<String> calculateSlotStatistics(Map<String,Integer> slotCounts, String type) {
    System.out.println("*** STATISTICS ***");
    int numEntries = 0;
    int totalCount = 0;
    double ratios = 0.0;
    double domainProbs = 0.0;
    double corpusProbs = 0.0;
    double corpusIDFs = 0.0;
    Map<String,Integer> relnCounts = new HashMap();
    Map<String,Double> relnDomainProbs = new HashMap();
    Map<String,Double> relnCorpusProbs = new HashMap();
    Map<String,Double> relnLikelihoods = new HashMap();
    Map<String,Double> relnCorpusIDFs = new HashMap();
    Map<String,List<Double>> relnAllRatios = new HashMap();
    Map<String,List<Double>> relnAllIDFs = new HashMap();
    Map<String,List<Double>> relnAllDomainProbs = new HashMap();
    Set<String> relns = new HashSet();
    //    Map<String,Integer> posTags = new HashMap();

    // Loop over the slots and calculate the scores.
    int xx = 0;
    for( Map.Entry<String,Integer> entry : slotCounts.entrySet() ) {
      String slot = entry.getKey();
      Integer count = entry.getValue();
      numEntries++;
      totalCount += count;

      int hyphen   = slot.lastIndexOf('-');
      String token = slot.substring(0, hyphen);
      String reln  = slot.substring(hyphen+1);
      relns.add(reln);

      // Get counts of these tokens and relations.
      int domainCount = _domainCounts.getCount(token, reln);
      int corpusCount = _corpusCounts.getCount(token, reln);
      double domainProb = (double)domainCount / (double)_domainCounts.getTotalCount();
      double corpusProb = (double)corpusCount / (double)_corpusCounts.getTotalCount();
      double likelihoodRatio = 0.0;
      if( domainProb > 0.0 )
        likelihoodRatio = domainProb / (corpusProb > 0.0 ? corpusProb : 100.0*domainProb);
      // If token-reln not seen in corpus, score it a 12.
      double relnCorpusIDF = 12.0;
      if( _corpusCounts.getDocCount(token, reln) > 0 )
        relnCorpusIDF = Math.log(_corpusCounts.getTotalDocs() / _corpusCounts.getDocCount(token, reln));

      // Put a ceiling on ratios.
      if( likelihoodRatio > 50.0 ) likelihoodRatio = 50.0;

      System.out.printf("%s %s count=%d domaincount=%d corpidf=%.1f dprob=%.5f cprob=%.5f ratio=%.1f\n", 
          token, reln, count, domainCount,
          relnCorpusIDF, domainProb, corpusProb, likelihoodRatio);

      domainProbs += (double)count * domainProb;
      corpusProbs += (double)count * corpusProb;
      ratios += (double)count * likelihoodRatio;
      corpusIDFs += (double)count * relnCorpusIDF;

      // For MATLAB histograms - store all scores.
      appendRelnScore(reln, likelihoodRatio, count, relnAllRatios);
      appendRelnScore(reln, domainProb, count, relnAllDomainProbs);
      appendRelnScore(reln, relnCorpusIDF, count, relnAllIDFs);

      // Count relations (across all tokens seen with them).
      Util.incrementCount(relnCounts, reln, count);
      Util.incrementCount(relnDomainProbs, reln, (double)count * domainProb);
      Util.incrementCount(relnCorpusProbs, reln, (double)count * corpusProb);
      Util.incrementCount(relnLikelihoods, reln, (double)count * likelihoodRatio);
      Util.incrementCount(relnCorpusIDFs, reln, (double)count * relnCorpusIDF);

      xx++;
      //      if( xx == 10 ) break;
    }

    // Average the counts.
    for( String reln : relnCounts.keySet() ) {
      double count = (double)relnCounts.get(reln);
      relnDomainProbs.put(reln, relnDomainProbs.get(reln) / count);
      relnCorpusProbs.put(reln, relnCorpusProbs.get(reln) / count);
      relnLikelihoods.put(reln, relnLikelihoods.get(reln) / count);
      relnCorpusIDFs.put(reln, relnCorpusIDFs.get(reln) / count);
    }

    // Print the relation averages.
    for( String reln : relnCounts.keySet() ) {
      System.out.printf("**reln** %s\t\tseencount=%d\tcidf=%.1f\tdprob=%.4f cprob=%.4f ratio=%.2f\n", reln, 
          relnCounts.get(reln), relnCorpusIDFs.get(reln),
          relnDomainProbs.get(reln), relnCorpusProbs.get(reln), 
          relnLikelihoods.get(reln));
    }

    // Print overall averages.
    System.out.println("Num slots = " + totalCount);
    System.out.println("Num slot types = " + numEntries);
    System.out.println("Avg likelihood ratio = " + (ratios / (double)totalCount));
    System.out.println("Avg corpus IDF score = " + (corpusIDFs / (double)totalCount));
    System.out.println("Avg domain probability = " + (domainProbs / (double)totalCount));
    System.out.println("Avg corpus probability = " + (corpusProbs / (double)totalCount));

    // MATLAB input. --- Change the for loop to graph different scores.
    for( Map.Entry<String,List<Double>> entry : relnAllRatios.entrySet() ) {
      //    for( Map.Entry<String,List<Double>> entry : relnAllDomainProbs.entrySet() ) {
      //    for( Map.Entry<String,List<Double>> entry : relnAllIDFs.entrySet() ) {
      String reln = entry.getKey();
      String var = reln + type;
      _matlabOut.write(var + " = [");
      //      for( Double score : entry.getValue() ) _matlabOut.write(" %.6f", score);
      for( Double score : entry.getValue() ) _matlabOut.write(String.format(" %.6f", score));
      _matlabOut.write(" ];\n");
      _matlabOut.write("[" + var + "n, " + var + "num] = hist(" + var + ", 20);\n");
    }

    return relns;
  }

  public static void calculateNERStatistics(Map<NERSpan.TYPE,Integer> counts) {
    System.out.println("** 0 " + counts.get(NERSpan.TYPE.NONE));
    System.out.println("** PER\t" + counts.get(NERSpan.TYPE.PERSON));
    System.out.println("** ORG\t" + counts.get(NERSpan.TYPE.ORGANIZATION));
    System.out.println("** LOC\t" + counts.get(NERSpan.TYPE.LOCATION));
  }

  private void calculateCorefStatistics(Map<Integer,Integer> counts) {
    int sum = 0;
    int less4 = 0;
    for( Integer freq : counts.keySet() ) {
      sum += counts.get(freq);
      if( freq < 4 ) less4 += counts.get(freq);
    }

    System.out.println("** 1 " + counts.get(1));
    System.out.println("** 2 " + counts.get(2));
    System.out.println("** 3 " + counts.get(3));
    System.out.println("** 4+ " + (sum-less4));
  }

  //  private void calculateCorefStatistics(Map<String,Map<Integer,Integer>> counts) {
  //  }

  private void calculatePOSTagStatistics(Map<String,Integer> counts) {
    Map<String,Integer> normedTags = new HashMap();

    int total = 0;
    for( String tag : counts.keySet() ) {
      total += counts.get(tag);
      Util.incrementCount(normedTags, normPOS(tag), counts.get(tag));
    }

    for( String tag : normedTags.keySet() ) {
      System.out.printf("** %s\t%d\t%.1f\n", tag, normedTags.get(tag),
          (100.0 * (double)normedTags.get(tag) / (double)total));
    }
  }

  private void calculateEventRepetitionStatistics(Map<String,Integer> counts) {
    Map<String,Integer> normedTags = new HashMap();

    int total = 0;
    for( String tag : counts.keySet() ) {
      total += counts.get(tag);
      Util.incrementCount(normedTags, normPOS(tag), counts.get(tag));
    }

    for( String tag : normedTags.keySet() ) {
      System.out.printf("** %s\t%d\t%.1f\n", tag, normedTags.get(tag),
          (100.0 * (double)normedTags.get(tag) / (double)total));
    }
  }

  private String normPOS(String tag) {
    if( tag.startsWith("NN") ) return "NN";
    if( tag.startsWith("VB") ) return "VB";
    return tag;
  }

  private String posToShort(String tag) {
    if( tag.startsWith("NN") ) return "n";
    if( tag.startsWith("VB") ) return "v";
    if( tag.startsWith("J") ) return "j";
    else return "o";
  }

  /**
   * Convert quotations to single characters.
   */
  private String cleanupLeafString(String leaf) {
    //    System.out.println("  cleanup=" + leaf);
    if( leaf.equals("''") ) {
      //      System.out.println("  WEEEE!");
      leaf = "\"";
    }
    else if( leaf.equals("``") ) leaf = "\"";
    return leaf;
  }

  /**
   * The mentions that humans coded in the MUC templates have punctuation that
   * should be split into separate tokens as they are in the parse trees.
   */
  private String cleanupMention(String mention) {
    mention = mention.toLowerCase();

    mention = mention.replaceAll(", ", " , ");
    mention = mention.replaceAll(": ", " : ");
    mention = mention.replaceAll("; ", " ; ");
    mention = mention.replaceAll("'s ", " 's ");
    mention = mention.replaceAll("' ", " ' ");

    // Split parentheses
    mention = mention.replaceAll("\\(", "-lrb- ");
    mention = mention.replaceAll("\\)", " -rrb-");

    // Split quotations.
    mention = mention.replaceAll("\" ", " \" ");
    mention = mention.replaceAll(" \"", " \" ");
    // Remove quotations if they are on the ends of the entire mention.
    if( mention.charAt(0) == '"' )
      mention = mention.substring(1);
    if( mention.charAt(mention.length()-1) == '"' )
      mention = mention.substring(0,mention.length()-1);

    return mention;
  }

  public static NERSpan.TYPE nerTypeOfEntity(int entityID, Vector<EntityMention> mentions,
      List<NERSpan> ners) {
    //    System.out.println("nerTypeOfEntity: " + entityID + " with " + mentions.size() +
    //		       " mentions and " + ners.size() + " ners");
    for( EntityMention mention : mentions ) {
      if( mention.entityID() == entityID ) {
        //	System.out.println("- match entity " + entityID + " " + mention);
        // Check if this mention has an NER label.
        for( NERSpan ner : ners ) {
          if( ner.sid() == mention.sentenceID()-1 &&
              mention.start() <= ner.start() && mention.end() >= ner.end() ) {
            //	    System.out.println("- span match! " + mention + " ner=" + ner);
            return ner.type();
          }
        }
      }
    }
    return null;
  }

  /**
   * Given a list of tokens, return the index of the first token's position
   * when we see the entire list in a row.  Indices in a tree start at 1, not 0.
   */
  public int indexOfTokenSequence(Tree tree, String[] tokens) {
    Collection<Tree> leaves = TreeOperator.leavesFromTree(tree);

    //    System.out.println("  indexOf: " + Arrays.toString(tokens));

    int marker = 0;
    int i = 1;
    for( Tree leaf : leaves ) {
      //      System.out.println(" leaf=" + leaf);
      String leafString = cleanupLeafString(leaf.firstChild().value().toLowerCase());
      if( leafString.equals(tokens[marker]) ) {
        marker++;
        //	System.out.println("  match!!");
      }
      else marker = 0;
      i++;
      if( marker == tokens.length ) break;
    }

    if( marker > 0 ) return i - tokens.length;
    else return -1;
  }

  /**
   * Convert strings to tree objects.

  private List<Tree> parseStringsToTrees(Vector<String> parses) {
    List<Tree> trees = new LinkedList();
    try {
      TreeFactory tf = new LabeledScoredTreeFactory();
      for( String parse : parses ) {
	PennTreeReader ptr = new PennTreeReader(new BufferedReader(new StringReader(parse)), tf);
	Tree parseTree = ptr.readTree();
	trees.add(parseTree);
      }
    } catch( Exception ex ) { ex.printStackTrace(); }
    return trees;
  }
   */

  /**
   * Given a string and a list of parses, find all occurrences of the string in
   * the parses.  Return a list of the sentences/indices that it was seen.
   *
   * Things we miss:
   *   ALFREDO CRISTIANI -> ( President Alfredo ) Cristiani
   *   [RETIRED] GENERAL -> ( retired ) general
   *   PATRIOTIC UNION PRESIDENT -> Patriotic Union ( UP ) President
   *   CINCHONEROS -> cinchonero
   *   CINCHONERO PEOPLE'S LIBERATION FRONT -> " cinchonero " people's liberation front
   *   D'ABUISSON - > d'aubuisson
   */
  private List<DocSpan> findMention(String mention, List<Tree> trees) {
    mention = cleanupMention(mention);
    List<DocSpan> spans = new LinkedList();

    String[] parts = mention.split("\\s+");

    // Search each tree for the mention.
    int sid = 0;
    for( Tree tree : trees ) {
      int index = indexOfTokenSequence(tree, parts);
      if( index > -1 ) {
        System.out.println("  found! " + sid + "," + index);
        spans.add(new DocSpan(sid, index, index+parts.length));
      }
      sid++;
    }

    return spans;
  }


  /**
   * @return The list of IDs for entities that span the given text span.
   */
  private List<Integer> findCorefClassesForMention(DocSpan span, Vector<EntityMention> entities) {
    List<Integer> corefIDs = new LinkedList();

    for( EntityMention mention : entities ) {
      // Sentence occurrences must match (entitymention sentences start at 1).
      if( mention.sentenceID() == span.sid+1 ) {
        // The entity must subsume the desired span completely.
        if( mention.start() <= span.start && mention.end() >= span.end-1 ) {
          System.out.println(" Coref Matched! " + mention);
          corefIDs.add(mention.entityID());
        }
      }
    }
    System.out.println(" Coref found " + corefIDs.size() + " separate coref entities");
    return corefIDs;
  }

  /**
   * Given a string's span, find the entity in the coref classes that has the same
   * text span and return all spans from the other entity mentions in that class.
   * @param span The span of an entity mention's text.
   * @param entities All coref entities.
   * @return Text spans for each mention that is corefferent with the given span.
   */
  private List<DocSpan> findCorefSpansForTextSpan(DocSpan span, Vector<EntityMention> entities) {
    List<DocSpan> spans = new LinkedList();

    System.out.println("Coref lookup span " + span + " with " + entities.size() + " entities");

    // Find all entity IDs of entities that span the given text span.
    List<Integer> corefIDs = findCorefClassesForMention(span, entities);

    // Now get all the spans for this entity.
    if( corefIDs.size() > 0 ) {
      for( EntityMention mention : entities ) {
        // Only look at the first one...easier.
        if( mention.entityID() == corefIDs.get(0) )
          // Subtract one from sentenceID...index from 0.
          // Add one to end point...spans should end *beyond* the last token, but
          // EntityMention objects end on the last token.
          spans.add(new DocSpan(mention.sentenceID()-1, mention.start(), mention.end()+1));
      }
    }

    return spans;
  }


  /**
   * This function finds all dependencies that have a dependent (not the governor) which
   * is covered by this text span.  Further, the governor is not contained in the text
   * span ... this condition prevents returning things like noun-noun modifiers that are
   * part of the span already.
   * @return The slots that the given text span fills, which could include multiple
   *         slots (maybe a verb argument and have modifiers).
   *         Slot Format: "token-relation"
   */
  private List<String> spanToSlots(DocSpan span, List<Tree> trees, Vector<Vector<TypedDependency>> alldeps) {
    // Sentence containing the span.
    Vector<TypedDependency> deps = alldeps.get(span.sid);
    // Parse tree containing the span.
    Tree tree = trees.get(span.sid);

    return spanToSlots(span.start, span.end, tree, deps, _wordnet);
  }

  
  /**
   * TypedDependency word indices start at 1.
   * @param startIndex Word index of the first token we care about.
   * @param endIndex Word index of the word *after* the last token we care about (exclusive end).
   */
  public static List<String> spanToSlots(int startIndex, int endIndex, Tree tree, List<TypedDependency> sentdeps, WordNet wordnet) {
    List<String> slots = new ArrayList<String>();
    Map<Integer, String> particles = Ling.particlesInSentence(sentdeps);

    // Find deps with the span.
    for( TypedDependency dep : sentdeps ) {
      int govIndex = dep.gov().index();
      int depIndex = dep.dep().index();
      // If dependent is in the span, and governor is not.
      if( depIndex >= startIndex && depIndex < endIndex && (govIndex < startIndex || govIndex >= endIndex) ) {
        System.out.println("Found " + dep);
        String gov = CountTokenPairs.buildTokenLemma(dep.gov().value(), dep.gov().index(), tree, particles, wordnet);
        String reln = dep.reln().toString();
        reln = CountTokenPairs.normalizeRelation(reln, true);
        slots.add(CountTokenPairs.attachRelation(gov, reln));
      }
    }
    return slots;
  }

  /**
   * Given a story's dependencies and parse trees, return all possible slots
   * lemmatizing the governing words.
   */
  private List<DocSlot> storyToSlots(List<Tree> trees, 
      Vector<Vector<TypedDependency>> storyDeps) {
    List<DocSlot> slots = new LinkedList();
    int sid = 0;
    for( Vector<TypedDependency> sentenceDeps : storyDeps ) {
      for( TypedDependency dep : sentenceDeps ) {
        String reln = CountTokenPairs.normalizeRelation(dep.reln().toString(), true);
        int govIndex = dep.gov().index();
        String gov = dep.gov().label().value().toString().toLowerCase();
        Tree subtree = TreeOperator.indexToSubtree(trees.get(sid), govIndex);
        String govPOSTag = subtree.label().value();
        gov = _wordnet.lemmatizeTaggedWord(gov, govPOSTag);
        if( CountVerbDeps.isNumber(gov) ) 
          gov = CountVerbDeps.NUMBER_STRING;
        slots.add(new DocSlot(sid, govIndex, gov, govPOSTag, reln));
      }
      sid++;
    }
    return slots;
  }

  /**
   * Search the list of NER labels to find a span that subsumes the given span.
   * @return The type of NER that matches this text span.
   */
  private NERSpan.TYPE spanSubsumesNER(DocSpan span, List<NERSpan> ners) {
    //    System.out.println("spanSubsumesNER with " + ners.size() + " ners");
    for( NERSpan ner : ners ) {
      //      System.out.println("  ner " + ner + " vs " + span);
      if( span.sid == ner.sid() ) {
        if( ner.start() >= span.start && ner.end() <= span.end ) {
          //	  System.out.println("  **matched!");
          return ner.type();
        }
      }
    }
    return NERSpan.TYPE.NONE;
  }


  private boolean isContentReln(String reln) {
    if( reln.contains("det")   ||  // the, a, an : all
        reln.startsWith("aux") || // was, have, will, were
        reln.equals("complm") ||   // that, whether
        reln.equals("advmod") ||  // meanwhile, often, again
        reln.equals("quantmod") ||  // only, about, approximately, at
        reln.equals("neg") ||     // no, never
        reln.equals("cop") ||     // was, is, be
        reln.equals("rel") ||        // which, where, what
        reln.equals("expl") ||        // there
        reln.equals("prt") ||        // up, down, at
        reln.equals("mark")        // because, although, while
    ) return false;
    return true;
  }


  /**
   * This function finds the Coref Entity that has the most coreferring mentions
   * in the text, and records that number of mentions.
   * @param entityIDs A map from MUC type (e.g. perpetrator) to coref IDs of the type's
   *                  mentions in the document.
   */
  private void recordCorefs(Map<String,Set<Integer>> entityIDs, Vector<EntityMention> entities) {
    // Get frequency counts of each entity.
    Map<Integer,Integer> entityCounts = new HashMap();
    for( EntityMention entity : entities )
      Util.incrementCount(entityCounts, entity.entityID(), 1);

    // Now increment the counts of each frequency range for the desired entityIDs.
    for( Map.Entry<String,Set<Integer>> entry : entityIDs.entrySet() ) {
      String type = entry.getKey();
      int max = 0;
      // Of all the mentions that matched, they may have different Coref IDs.
      // -- find the ID with the most coreferring mentions
      // -- OpenNLP makes mistakes, misses corefs, so we may have many IDs
      for( Integer id : entry.getValue() )
        if( entityCounts.get(id) > max ) 
          max = entityCounts.get(id);

      System.out.println("Recording coref " + type + " " + max);
      if( type.equals(MUCTemplate.HUMAN_PERP) || type.equals(MUCTemplate.HUMAN_TARGET) )
        Util.incrementCount(_corefCountsHuman, max, 1);
      else if( type.equals(MUCTemplate.ORG_PERP) )
        Util.incrementCount(_corefCountsOrg, max, 1);
      else if( type.equals(MUCTemplate.PHYS_TARGET) )
        Util.incrementCount(_corefCountsPhys, max, 1);
      else System.out.println("**ERROR: unknown coref type: " + type);
    }
  }

  // The number of mention strings in the documents that match the template
  // annotated strings, and that are corefferent with those matches.
  private void recordMentionCount(String type, int numMentions) {
    System.out.println("Recording coref mentions " + type + " " + numMentions);
    if( type.equals(MUCTemplate.HUMAN_PERP) || type.equals(MUCTemplate.HUMAN_TARGET) )
      Util.incrementCount(_mentionCountsHuman, numMentions, 1);
    else if( type.equals(MUCTemplate.ORG_PERP) )
      Util.incrementCount(_mentionCountsOrg, numMentions, 1);
    else if( type.equals(MUCTemplate.PHYS_TARGET) )
      Util.incrementCount(_mentionCountsPhys, numMentions, 1);
    else
      System.out.println("**ERROR: unknown coref type: " + type);
  }

  // The number of mention strings in the MUC Templates - human annotated.
  private void recordMUCMentionCount(MUCEntity entity) {
    String type = entity.type();
    int numMentions = entity.getMentions().size();
    System.out.println("Recording MUC mention count " + type + " " + numMentions);
    if( type.equals(MUCTemplate.HUMAN_PERP) || type.equals(MUCTemplate.HUMAN_TARGET) )
      Util.incrementCount(_mucMentionCountsHuman, numMentions, 1);
    else if( type.equals(MUCTemplate.ORG_PERP) )
      Util.incrementCount(_mucMentionCountsOrg, numMentions, 1);
    else if( type.equals(MUCTemplate.PHYS_TARGET) )
      Util.incrementCount(_mucMentionCountsPhys, numMentions, 1);
    else
      System.out.println("**ERROR: unknown coref type: " + type);
  }

  /**
   * Add one count to this slot's count.
   */
  private void recordSlot(DocSlot slot, Map<String,Integer> counts) {
    String slotStr = slot.stringForm();
    Integer count = counts.get(slotStr);
    if( count == null ) {
      count = new Integer(1);
      counts.put(slotStr, count);
    }
    else counts.put(slotStr, count + 1);
  }

  /**
   * Given a token's index in a sentence, and a list of dependencies for that sentence,
   * find all noun arguments of that token.
   */
  private Set<String> nounArgsOf(int govIndex, 
      Vector<TypedDependency> sentenceDeps, 
      Tree tree) {
    Set<String> args = new HashSet();
    for( TypedDependency dep : sentenceDeps ) {
      if( dep.gov().index() == govIndex ) {
        String reln = dep.reln().toString();
        if( isContentReln(reln) && !reln.startsWith("conj") ) {
          int depIndex = dep.dep().index();
          Tree subtree = TreeOperator.indexToSubtree(tree, depIndex);
          String depPOSTag = subtree.label().value();
          if( depPOSTag.startsWith("NN") )
            args.add(reln + "-" + dep.dep().value());
        }
      }
    }
    return args;
  }

  /**
   * Look at all tokens in the document, and print them in order of their
   * appearance in the given ordered list of keywords.
   * @param keywords An ordered list of tokens by importance.
   */
  private void sortStoryWords(List<String> keywords, 
      Vector<Vector<TypedDependency>> storyDeps,
      List<Tree> trees) {
    List<SortableScore> scores = new ArrayList();
    Set<String> seen = new HashSet();
    Map<String, Set<String>> wordArgs = new HashMap();
    int sid = 0;

    for( Vector<TypedDependency> sentenceDeps : storyDeps ) {
      for( TypedDependency dep : sentenceDeps ) {
        int govIndex = dep.gov().index();
        String gov = dep.gov().label().value().toString().toLowerCase();
        Tree subtree = TreeOperator.indexToSubtree(trees.get(sid), govIndex);
        String govPOSTag = subtree.label().value();
        gov = _wordnet.lemmatizeTaggedWord(gov, govPOSTag);
        if( CountVerbDeps.isNumber(gov) ) 
          gov = CountVerbDeps.NUMBER_STRING;

        String tokenkey = posToShort(govPOSTag) + "-" + gov;
        int position = keywords.indexOf(tokenkey);
        if( position > -1 ) {
          if( !seen.contains(tokenkey) ) 
            scores.add(new SortableScore(position, tokenkey));
          seen.add(tokenkey);
          // Save the args
          Set<String> args = nounArgsOf(govIndex, sentenceDeps, trees.get(sid));
          Set<String> allargs = wordArgs.get(tokenkey);
          if( allargs == null ) {
            allargs = new HashSet();
            wordArgs.put(tokenkey, allargs);
          }
          allargs.addAll(args);
        }
      }
      sid++;
    }

    // Sort and print the top words.
    SortableScore[] arr = new SortableScore[scores.size()];
    arr = scores.toArray(arr);
    Arrays.sort(arr);
    for( int i = arr.length-1; i >= 0; i-- ) {
      System.out.print(arr[i]);
      for( String arg : wordArgs.get(arr[i].key()) )
        System.out.print(" | " + arg);
      System.out.println();
    }
  }

  public void process() {
    Vector<String> sentences = _mucDocuments.nextStory();
    int errors = 0;
    int stories = 0;
    int totalMUCEntities = 0;

    // Get the key words for the domain.
    List<SortableScore> wordsByLikelihood = _detector.detectWordsRelativeFrequencyRatio(false);
    int i = 0;
    for( SortableScore score : wordsByLikelihood )
      System.out.println("likelihood: " + (i++) + " " + score.key() + "\t" + score.score());

    /*
    while( sentences != null ) {
      List<DocSlot> slotList = new LinkedList();

      // Get the templates for this story.
      String storyID = _mucDocuments.currentStory();
      System.out.println(storyID);
      List<MUCTemplate> templates = _mucKeyReader.getTemplates(storyID);

      // Get the parses and deps for this story.
      System.out.println(" - reading parses and deps");
      List<Tree> trees = TreeOperator.stringsToTrees(_parseReader.nextStory(storyID));
      _depsReader.nextStory(storyID);
      _corefReader.nextStory(storyID);
      _nerReader.nextStory(storyID);

      if( templates != null ) {

	for( MUCTemplate template : templates ) {
	  // Get the entities in our desired keys.
	  List<MUCEntity> entities = template.getMainEntities();

	  // Get the entities in each template.
	  for( MUCEntity entity : entities ) {
	    System.out.println("entity: " + entity);
	    Set<String> entityPOSTags = new HashSet();
	    List<String> mentions = entity.getMentions();
	    List<DocSpan> entitySpans = new ArrayList();
	    Map<String,Set<Integer>> entityIDs = new HashMap();
	    //	    totalMUCEntities++;
	    totalMUCEntities += entityIDs.size();

	    // Get the string mentions for each entity.
	    for( String mention : mentions ) {
	      System.out.println("  mention: " + mention);
	      // Find each mention's location in the document.
	      // *** Search parse tree for text span ***
	      List<DocSpan> spans = findMention(mention, trees);
	      entitySpans.addAll(spans);

	      if( spans.size() == 0 ) {
		System.out.println("ERROR! no indices found for " + mention);
		errors++;
	      }

	      // **************************************************
	      // *** Search dependencies for text spans. ***
	      // **************************************************
	      for( DocSpan span : spans ) {

		// Save the coref Entity objects (their IDs) that include each text span.
		Set<Integer> ids = entityIDs.get(entity.type());
		if( ids == null ) {
		  ids = new HashSet();
		  entityIDs.put(entity.type(), ids);
		}
		ids.addAll(findCorefClassesForMention(span, _corefReader.getEntities()));

		// Lookup coref class, find all spans for all class members.
		List<DocSpan> corefSpans = findCorefSpansForTextSpan(span, _corefReader.getEntities());
		for( DocSpan cspan : corefSpans ) System.out.println("   - corefspan: " + cspan);
		// Now add the main span to this list (just in case not in returned set).
		corefSpans.add(span);

		for( DocSpan cspan : corefSpans ) {
		  // Find the verb slot that this span fills.
		  System.out.println("   - cspan: " + cspan);
		  List<DocSlot> slots = spanToSlots(cspan, trees, _depsReader.getDependencies());

		  // Errors can happen with bad parses (e.g. noun tagged as a verb).
		  if( slots.size() == 0 ) System.out.println("ERROR! no slots found");

		  for( DocSlot slot : slots ) {
		    slot.token = slot.token.toLowerCase();
		    if( !slotList.contains(slot) ) {
		      slotList.add(slot);
		      entityPOSTags.add(normPOS(slot.posTag));
		    }
		    System.out.println("   - seen slot: " + slot);
		  }
		}


	      }
	    }

	    // Each POS tag seen for this entity gets one count.
	    for( String tag : entityPOSTags ) {
	      System.out.println("   - pos add " + tag);
	      Util.incrementCount(_posCountsEntitySpecific, tag, 1);
	    }

	    // Find the NER type for this entity. Take the first matching span.
	    boolean matched = false;
	    for( DocSpan span : entitySpans ) {
	      System.out.println("  -> span " + span);
	      int nerType = spanSubsumesNER(span, _nerReader.getNER());
	      if( nerType != -1 ) {
		System.out.println("NER match! " + entity + " --> " + NERSpan.typeToString(nerType) );
		Util.incrementCount(_nerCounts, nerType, 1);
		matched = true;
		break;
	      }
	    }
	    if( !matched ) {
	      System.out.println("  no NER - " + entity);
	      Util.incrementCount(_nerCounts, 0, 1);
	    }

	    // Save the coref entity counts.
	    System.out.println("Entity Coref! ");
	    int totalmentions = 0;
	    for( String type : entityIDs.keySet() ) {
	      System.out.println(type);
	      for( EntityMention mention : _corefReader.getEntities() ) {
		if( entityIDs.get(type).contains(mention.entityID()) ) {
		  System.out.println("  -> " + mention);
		  totalmentions++;
		}
	      }
	    }
	    recordCorefs(entityIDs, _corefReader.getEntities());
	    // Number of coref spans we found matching a single MUC entity.
	    recordMentionCount(entity.type(), totalmentions);
	    recordMUCMentionCount(entity);

	  }  // MUCEntity

	}  // MUCTemplate

	// Save the slots.
	for( DocSlot slot : slotList ) {
	  //	  System.out.println("   - recording slot: " + slot);
	  recordSlot(slot, _templateCounts);
	  Util.incrementCount(_posCounts, slot.posTag, 1);
	}

	// Print main words in story.
	System.out.println("Top Story Words - " + storyID);
	sortStoryWords(wordsByLikelihood, _depsReader.getDependencies(), trees);

	stories++;
	//	if( stories == 3 ) break;
      } // if (templates != null)


      // Record all slots that didn't contain an entity we wanted.
      List<DocSlot> storySlots = storyToSlots(trees, _depsReader.getDependencies());
      for( DocSlot slot : storySlots ) {
	if( isContentReln(slot.reln) && !slotList.contains(slot) ) {
	  //	  System.out.println("   - nonkey slot: " + slot);
	  recordSlot(slot, _otherCounts);
	}
      }

      // Next story.
      sentences = _mucDocuments.nextStory();
    }
     */

    System.out.println("** Cluster the token Pairs **");
    ReadingCluster[] clusters = clusterPairs();

    System.out.println("** Initial Sentence Scores **");
    if( _detector._initialWords != null ) {
      SortableScore[] initialScores = _detector._initialWords.calculateKeyTokens(300);
      if( initialScores != null ) {
        for( SortableScore score : initialScores ) {
          System.out.printf("initial: %s\t%.2f\n", score.key(), score.score());
        }
      } else System.out.println("no initial words");
    } else System.out.println("initialWords null");
    
    System.out.println("** Label Sentences **");
    Set<String> tokensLemmas = keyDomainTokens(_detector._domainIDF, _detector._generalIDF, _wordnet,
        _domainCounts, _tokenType);
    EventPairScores cache = pairCountsToPMI(_tokenPairCounts, _detector._domainIDF, tokensLemmas, 5, 5);

    int xx = 0;
    Frame[] frames = new Frame[clusters.length];
    for( ReadingCluster cluster : clusters ) {
      Frame frame = new Frame(cluster.getID(), cluster.getTokenScores(), cluster.score());
      frame.setType("cluster");
      frames[xx++] = frame;
    }
    ClusterMUC.labelDocumentsWithClusters(_dataReader, frames, cache, _wordnet, _detector._generalIDF, _detector._domainIDF, CountTokenPairs.VERBS_AND_NOMINALS, false);


    System.out.println("** Total MUC Entities in Templates = " + totalMUCEntities + " **");

    // Calculate Statistics
    System.out.println("** Stats Keys **");
    Collection<String> relns = calculateSlotStatistics(_templateCounts, "key");
    System.out.println("** Stats Others **");
    calculateSlotStatistics(_otherCounts, "notkey");

    // Print POS Tag Info
    System.out.println("** POS Tags (all entity occurrences) **");
    calculatePOSTagStatistics(_posCounts);
    System.out.println("** POS Tags (one occurrence max per entity) **");
    calculatePOSTagStatistics(_posCountsEntitySpecific);

    // Print Coref Info
    System.out.println("** Coref Frequencies for Humans **");
    calculateCorefStatistics(_corefCountsHuman);
    System.out.println("** Mention Frequencies for Humans **");
    calculateCorefStatistics(_mentionCountsHuman);
    System.out.println("** MUC Mention size for Humans **");
    calculateCorefStatistics(_mucMentionCountsHuman);
    System.out.println("** Coref Frequencies for Org Perps **");
    calculateCorefStatistics(_corefCountsOrg);
    System.out.println("** Mention Frequencies for Org Perps **");
    calculateCorefStatistics(_mentionCountsOrg);
    System.out.println("** MUC Mention size for Org Perps **");
    calculateCorefStatistics(_mucMentionCountsOrg);
    System.out.println("** Coref Frequencies for Physical Targets **");
    calculateCorefStatistics(_corefCountsPhys);
    System.out.println("** Mention Frequencies for Physical Targets **");
    calculateCorefStatistics(_mentionCountsPhys);
    System.out.println("** MUC Mention size for Physical Targets **");
    calculateCorefStatistics(_mucMentionCountsPhys);

    // Print NER Info
    System.out.println("** NER Frequencies for Template Values **");
    calculateNERStatistics(_nerCounts);

    // MATLAB plotting
    for( String reln : relns ) {
      _matlabOut.write(reln + "Z = [ " + reln + "keyn'/sum(" + reln + "keyn), " + 
          reln + "notkeyn'/sum(" + reln + "notkeyn) ];\n");
      _matlabOut.write("bar(" + reln + "Z)\n");
      _matlabOut.write("var = strcat('" + reln.toUpperCase() + "', ' count=', int2str(sum(" + reln + "keyn)), 'vs', int2str(sum(" + reln + "notkeyn)));\n");
      _matlabOut.write("title(var);\n");
      _matlabOut.write("g = figure(1);\n");
      _matlabOut.write("saveas(g, 'reln-" + reln + "', 'jpg');\n");
    }
    _matlabOut.close();

    //    printSlotCounts(_templateCounts);
    //    printSlotCounts(_otherCounts);
    System.out.println("Template errors (muc strings not matched in text): " + errors);
  }


  /**
   * Add one count to this slot's count.
   */
  private void printSlotCounts(Map<String,Integer> counts) {
    if( counts == null ) System.out.println("0 slots");
    else {
      for( Map.Entry<String,Integer> entry : counts.entrySet() ) {
        System.out.println(entry.getKey() + "\t" + entry.getValue());
      }
    }
  }


  /**
   * Convenience class to store a text span in a document.  Contains the
   * sentence ID and the token span within it.
   */
  private class DocSpan {
    public int sid;   // index of sentence containing the span - starts from 0
    public int start; // index of first token
    public int end;   // index of last token +1 (exclusive)

    DocSpan(int sid, int start, int end) {
      this.sid = sid;
      this.start = start;
      this.end = end;
    }
    public String toString() {
      return "(" + sid + "," + start + "-" + end + ")";
    }
  }

  /**
   * Convenience class to store a governor and a relation.
   * Also stores the sentence ID and position of the governor.
   */
  private class DocSlot {
    public int sid;       // sentence the slot is in.
    public int index;     // index of governor token.
    public String token;  // parent of the slot
    public String posTag; // POS tag of the parent token.
    public String reln;   // relation for the slot

    public DocSlot(int sid, int index, String token, String pos, String reln) {
      this.sid = sid;
      this.index = index;
      this.token = token;
      this.posTag = pos;
      this.reln = reln;
    }

    @Override public boolean equals(Object otherObj) {
      if( this == otherObj ) return true;
      if( !(otherObj instanceof DocSlot) ) return false;

      DocSlot other = (DocSlot)otherObj;
      if( other.sid == this.sid && other.index == this.index &&
          this.token.equals(other.token) && this.reln.equals(other.reln) )
        return true;
      else return false;
    }

    public String stringForm() { return token + "-" + reln; }
    public String toString() {
      return stringForm() + " (" + posTag + " sid=" + sid + " index=" + index + ")";
    } 
  }


  public static void main(String[] args) {
    StatisticsDeps stat = new StatisticsDeps(args);
    stat.process();
  }
}
