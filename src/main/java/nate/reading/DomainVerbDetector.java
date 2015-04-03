package nate.reading;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nate.CalculateIDF;
import nate.CountVerbDepCorefs;
import nate.IDFMap;
import nate.Pair;
import nate.WordEvent;
import nate.narrative.EventPairScores;
import nate.util.SortableScore;
import nate.util.Util;

/**
 * Takes two IDF counts: words from a single domain, and from a general corpus.
 * Given these counts, it detects the top words for that domain.  There are a
 * couple approaches, the best seems to be relative frequency ratios.  The
 * Filatova approach of domain conditional probability times IDF is also here.
 *
 * Output is to STDOUT.  Top words with scores.
 */
public class DomainVerbDetector {
  public IDFMap _domainIDF;
  public IDFMap _generalIDF;
  public IDFMap _initialIDF;
  public InitialWords _initialWords;
  // Set by Nate, instead of a verb stop list.
  float _filatovaIDFCutoff = 2.0f;

  public DomainVerbDetector(String[] args) {
    this(args[0], args[1]);
  }

  public DomainVerbDetector(String domainPath, String generalPath) {
    _domainIDF = new IDFMap(domainPath);
    _generalIDF = new IDFMap(generalPath);
  }

  public DomainVerbDetector(String domainPath, String generalPath, String initialsPath) {
    this(domainPath, generalPath);
    if( initialsPath != null ) {
      _initialIDF = new IDFMap(initialsPath);
      _initialWords = new InitialWords(_initialIDF, _generalIDF);
      System.out.println("loaded _initialWords in domainverbdetector");
    } else System.out.println("WARNING: initialsPath null in DomainVerbDetector()");
  }

  public DomainVerbDetector(IDFMap dmap, IDFMap gmap) {
    _domainIDF = dmap;
    _generalIDF = gmap;
  }


  public void detectVerbs() {
    System.out.println("**Filatova Domain Score");
    detectWordsFilatova(false);
    System.out.println("**Likelihood Ratio");
    detectWordsRelativeFrequencyRatio(false);
    System.out.println("**IDF Ratio");
    detectWordsIDFRatio();
  }

  /**
   * The probability of a word and its POS tag in the corpus, as counted by
   * the given IDF scores and counts.
   */
  public static double probabilityOfWord(String word, String posTag, IDFMap idf) {
    String key = CalculateIDF.createKey(word, CalculateIDF.normalizePOS(posTag));
    return probabilityOfWord(key, idf);
  }

  public static double probabilityOfWord(String key, IDFMap idf) {
    int freq = idf.getFrequency(key);
    double prob = (double)freq / (double)idf.totalCorpusCount();
    return prob;
  }
  
  public static double likelihoodRatio(String key, IDFMap idf1, IDFMap idf2) {
    double prob1 = probabilityOfWord(key, idf1);
    double prob2 = probabilityOfWord(key, idf2);
    return prob1 / prob2;
  }

  public static double probabilityOfTmodGivenWord(String word, CountVerbDepCorefs depCorefCounts) {
    Set<String> relns = depCorefCounts.getRelns(word);

    if( relns != null ) {
      int count = 0;
      for( String reln : relns )
        count += depCorefCounts.getCount(word, reln);
      double tmodCount = depCorefCounts.getCount(word, "tmod");

      double prob = (double)tmodCount / (double)count;
      return prob;
    }

    else return 0.0;
  }

  /**
   * P( has-coref-arg | word )
   * Gives the probability that a word's argument is coreferent.
   * @param word Should be in the format of CountVerbDepCorefs entries... "POS-word"
   * @param countCutoff The number of times the given word should have been seen for
   *                    us to give it a score...otherwise we return zero.
   */
  public static double salienceScore(String word,
      int countCutoff,
      CountVerbDepCorefs depCorefCounts) {
    // Count total seen arguments for this word.
    int count = 0;
    int corefCount = 0;

    Set<String> relns = depCorefCounts.getRelns(word);
    if( relns != null ) {
      for( String reln : relns ) {
        count += depCorefCounts.getCount(word, reln);
        corefCount += depCorefCounts.getCorefCount(word, reln);
      }
    }
    else System.out.println("salienceScore: depscounts didn't have " + word);

    // The overall count for this word should be high enough to score with confidence.
    if( count >= countCutoff ) {
      // Probability that an argument is coreferrent.
      double score = (double)corefCount / (double)count;
      //      System.out.printf("%s\t%d/%d\t=%.4f\n", word, corefCount, count, score);
      return score;
    }

    return 0.0;
  }

  /**
   * P( has-coref-arg | word ) * P( word ) * P( tmod | word )
   * @param word
   * @param countCutoff
   * @param depCorefCounts
   * @param domainIDF
   * @return
   */
  public static double discourseSalienceScore(String word,
      int countCutoff,
      CountVerbDepCorefs depCorefCounts,
      IDFMap domainIDF) {
    double salienceScore = salienceScore(word, countCutoff, depCorefCounts);
    double wordProb = probabilityOfWord(word, domainIDF);
    // NOTE: adding tmod boosted precision significantly in KIDNAP.
    //     *** investigate more?
    double tmodProb = probabilityOfTmodGivenWord(word, depCorefCounts);
    double score = salienceScore * wordProb * tmodProb;
    //    double score = salienceScore * wordProb;
    return score;
  }

  /**
   * Return the top words based on their "salience" ... words that tend to have
   * arguments that corefer to other aspects in the document.
   *     score = P( has-coref-arg | word ) * P( word ) * P( tmod | word)
   *
   * Note: filters low IDF words from the general corpus out
   */
  public List<String> detectWordsOnlyDiscourseSalience(CountVerbDepCorefs depCorefCounts, boolean verbsOnly) {
    List<SortableScore> scored = detectWordsDiscourseSalience(depCorefCounts, verbsOnly);
    List<String> words = new ArrayList<String>();
    for( SortableScore score : scored ) words.add(score.key());
    return words;
  }
  public List<SortableScore> detectWordsDiscourseSalience(CountVerbDepCorefs depCorefCounts, boolean verbsOnly) {
    System.out.println("---Salience Detector---");
    System.out.println(depCorefCounts.getWords().size() + " words in the counts");

    // Score each word.
    int i = 0;
    List<SortableScore> wordScores = new ArrayList<SortableScore>();
    for( String word : depCorefCounts.getWords() ) {
      if( !verbsOnly || word.startsWith("v-") ) {
        double idfscore = _generalIDF.get(word);
        // Skip words with low IDFs in the general corpus.
        if( idfscore < _filatovaIDFCutoff && idfscore > 0.0 ) {
          //	System.out.println("salience: skipping low general IDF of " + word);
        }
        else {
          double score = discourseSalienceScore(word, 20, depCorefCounts, _domainIDF);
          if( score > 0.0 )
            wordScores.add(new SortableScore(score, word));
          //	else System.out.println("salience skipping word " + word + " due to low score");
        }
      }
    }

    // Sort by score
    SortableScore[] arr = new SortableScore[wordScores.size()];
    arr = wordScores.toArray(arr);
    Arrays.sort(arr);
    Util.scaleToUnit(arr);

    // Output
    System.out.println("Salience SORTED");
    List<SortableScore> words = new ArrayList<SortableScore>();
    i = 0;
    for( SortableScore score : arr ) {
      if( score.score() < 0.0 ) break;
      else System.out.printf("salience: %d %s\t%.4f\n", i++, score.key(), score.score());
      words.add(score);
    }

    return words;
  }

  /**
   * Merge likelihood ratio and salience scores into one score.
   *  salience = P( has-coref-arg | word ) * Pdomain( word ) * P( tmod | word)
   *  ratio = Pdomain(word) / Pgeneral(word) 
   */
  public List<SortableScore> detectWordsSalienceAndRatio(CountVerbDepCorefs depCorefCounts, boolean verbsOnly) {
    List<SortableScore> ratios = detectWordsRelativeFrequencyRatio(verbsOnly);
    List<SortableScore> saliences = detectWordsDiscourseSalience(depCorefCounts, verbsOnly);

    Map<String,Double> merged = new HashMap<String, Double>();
    for( SortableScore ratio : ratios )
      merged.put(ratio.key(), ratio.score());

    for( SortableScore salience : saliences ) {
      Double ratioScore = merged.get(salience.key());
      if( ratioScore != null )
        salience.setScore(salience.score() * ratioScore);
      else
        salience.setScore(0.1); // ?? some low number ??
    }

    return saliences;
  }


  /**
   * idf-ratio: log(D / D(x)) / log(Dd / Dd(x))
   */
  public List<String> detectWordsIDFRatio () {
    int domainTotalOccurrences = 0;
    int generalTotalOccurrences = 0;    

    // Sum total frequencies of domain words.
    for( String word : _domainIDF.getWords() ) {
      domainTotalOccurrences += _domainIDF.getFrequency(word);
    }

    // Sum total frequencies of general words.
    for( String word : _generalIDF.getWords() ) {
      generalTotalOccurrences += _generalIDF.getFrequency(word);
    }

    // Score each word.
    List<SortableScore> wordScores = new ArrayList<SortableScore>();
    for( String word : _domainIDF.getWords() ) {

      // Skip words seen only a few times...they have high variance in scores.
      if( _domainIDF.getDocCount(word) > 100 ) {
        // IDF of the word IN THE DOMAIN.
        double domainIDF = (double)_domainIDF.numDocs() / (double)_domainIDF.getDocCount(word);

        // IDF of the word OUT OF DOMAIN.
        double generalIDF = 1.0;
        if( _generalIDF.getDocCount(word) > 0 )
          generalIDF = (double)_generalIDF.numDocs() / (double)_generalIDF.getDocCount(word);

        // Calculate the ratio.
        // We want words with lower domain IDF than the general corpus.
        double score = generalIDF / domainIDF;

        //  System.out.printf("idf: %s\td=%.5f\tg=%.5f\t==%.2f\n", word, domainIDF, generalIDF, score);

        wordScores.add(new SortableScore(score, word));
      }
    }

    // Sort by score
    SortableScore[] arr = new SortableScore[wordScores.size()];
    arr = wordScores.toArray(arr);
    Arrays.sort(arr);

    // Output
    System.out.println("SORTED");
    List<String> words = new ArrayList<String>();
    for( SortableScore score : arr ) {
      if( score.score() < 0.0 ) break;
      else System.out.printf("idfratio: %s\t%.6f\t%d\n", score.key(), score.score(), _domainIDF.getDocCount(score.key()));
      words.add(score.key());
    }
    return words;
  }
  
  /**
   * Select the core domain specific words by a relative frequency ratio.
   * It is the probability of the word in the domain, divided by the probability
   * of the word in the general counts.  This works great except that novel or
   * rare words can skyrocket because their small occurrence in the domain is
   * a random chance or specific to a single article.
   *
   * Thus, this function sets a cutoff on eligible words, ignoring those that
   * appear in less than 5% of the domain's documents.  Doing this almost 
   * completely removes nouns from the top.  Not doing it includes a bunch of 
   * very specific nouns and names.
   *
   * P( word | domain ) / P( word | general-corpus )
   */
  public List<String> detectWordsOnlyRelativeFrequencyRatio(boolean verbsOnly) {
    List<SortableScore> scored = detectWordsRelativeFrequencyRatio(verbsOnly);
    List<String> words = new ArrayList<String>();
    for( SortableScore score : scored ) {
      if( score.score() > 0.0f ) words.add(score.key());
    }
    return words;
  }

  public List<SortableScore> detectWordsRelativeFrequencyRatio(boolean verbsOnly) {
    // Ignore words that appear very rarely in the general corpus.
    int generalCutoff = Math.round(_generalIDF.numDocs() * 0.0004f + 0.5f);
    System.out.println("general doc cutoff = " + generalCutoff);

    int domainCutoff = Math.round(_domainIDF.numDocs() * 0.05f + 0.5f);
    System.out.println("domain doc cutoff = " + domainCutoff);

    // Score each word.
    List<SortableScore> wordScores = new ArrayList<SortableScore>();
    for( String word : _domainIDF.getWords() ) {
      if( !verbsOnly || word.startsWith("v-") ) {
        if( _domainIDF.getDocCount(word) < domainCutoff || 
            _generalIDF.getDocCount(word) < generalCutoff ) {
          wordScores.add(new SortableScore(0.0, word));
        }
        else {
          // Probability of the word IN THE DOMAIN.
          double domainProb = (double)_domainIDF.getFrequency(word) / (double)_domainIDF.totalCorpusCount();
          // Probability of the word OUT OF DOMAIN.
          double genProb = 1.0;
          if( _generalIDF.getFrequency(word) > 0 )
            genProb = (double)_generalIDF.getFrequency(word) / (double)_generalIDF.totalCorpusCount();
          // Calculate the ratio.
          double score = domainProb / genProb;

          //	System.out.printf("%s d=%.5f g=%.5f ==%.2f\n", word, domainProb, genProb, score);
          wordScores.add(new SortableScore(score, word));
        }
      }
    }

    // Scale the scores.
    // Sort by score
    SortableScore[] arr = new SortableScore[wordScores.size()];
    arr = wordScores.toArray(arr);
    Arrays.sort(arr);
    Util.scaleToUnit(arr);

    // Output
    //    System.out.println("SORTED");
    List<SortableScore> words = new ArrayList<SortableScore>();
    for( SortableScore score : arr ) {
      if( score.score() < 0.0 ) break;
      else System.out.printf("likelihood: %s\t%.6f\t%d\n", score.key(), score.score(), _domainIDF.getDocCount(score.key()));
      words.add(score);
    }
    return words;
  }


  public double likelihoodRatio(String word) {
    // Probability of the word IN THE DOMAIN.
    double domainProb = probabilityOfWord(word, _domainIDF);

    // Probability of the word OUT OF DOMAIN.
    double genProb = 1.0;
    if( _generalIDF.getFrequency(word) > 0 )
      genProb = probabilityOfWord(word, _generalIDF);

    // Calculate the ratio.
    return domainProb / genProb;
  }

   /**
   * Uses Filatova's approach to scoring domain words (ACL-2006).
   * It calculates conditional probability of a word, multiplied by an
   * IDF-like score.  This also includes lots of common verbs like be, say,
   * make, report, etc.  
   *
   * Their paper uses a filtered stop-word list to get rid of common verbs,
   * but this function instead filters by IDF score over the general corpus.
   * Their paper is also just verbs, use 'verbsOnly' for this mode.
   *
   * @param verbsOnly True if you want only verbs returned.
   */
  public List<String> detectWordsFilatova(boolean verbsOnly) {
    List<SortableScore> wordScores = new ArrayList<SortableScore>();
    int domainTotalOccurrences = 0;

    System.out.println("detectWordsFilatova...");

    // Sum total frequencies of all seen words.
    for( String word : _domainIDF.getWords() ) {
      if( !verbsOnly || word.startsWith("v-") ) {
        if( _generalIDF.get(word) >= _filatovaIDFCutoff ) {
          domainTotalOccurrences += _domainIDF.getFrequency(word);
        }
      }
    }

    // Score each word.
    for( String word : _domainIDF.getWords() ) {
      if( !verbsOnly || word.startsWith("v-") ) {
        if( _generalIDF.get(word) >= _filatovaIDFCutoff ) {
          // probability of the word
          double score = (double)_domainIDF.getFrequency(word) / (double)domainTotalOccurrences;
          // multiplied by an IDF score
          score *= (double)_domainIDF.getDocCount(word) / (double)_domainIDF.numDocs();
          wordScores.add(new SortableScore(score, word));
        }
      }
    }

    // Sort by score
    SortableScore[] arr = new SortableScore[wordScores.size()];
    arr = wordScores.toArray(arr);
    Arrays.sort(arr);
    List<String> words = new ArrayList<String>();
    // Output
    int i = 0;
    for( SortableScore score : arr ) {
      System.out.println("fila top verb " + i + ": " + score.key() + "\t" + score.score());
      words.add(score.key());
      if( i++ > 100 ) break;
    }

    return words;
  }



  public List<String> detectWordsChambers(EventPairScores pairScores, boolean verbsOnly) {
    // Get top repeated words in the domain.
    System.out.println("chambers calling fila...");
    List<String> filaWords = detectWordsFilatova(verbsOnly);
    Util.firstN(filaWords, 30);

    Map<String,Float> neighborScores = new HashMap<String, Float>();

    // Read narrative schemas graph, following edges to get others.
    for( String vword : filaWords ) {
      String word = vword.substring(2);
      String[] deps = {"s", "o", "p"};
      for( String dep : deps ) {
        String key = EventPairScores.buildKey(WordEvent.stringify(word, WordEvent.VERB), dep);
        Map<String,Float> neighbors = pairScores.getNeighbors(key);
        System.out.print("chambers: " + key);
        System.out.println(" with " + ((neighbors == null) ? "null" : neighbors.size()) + " neighbors");

        if( neighbors != null ) {
          for( Map.Entry<String,Float> entry : neighbors.entrySet() ) {
            Pair split = EventPairScores.split(entry.getKey());
            Float score = entry.getValue();
            Util.incrementCount(neighborScores, (String)split.first(), score);
          }
        }
      }
    }

    // Sort the neighbors by score.    
    SortableScore[] scores = new SortableScore[neighborScores.size()];
    int i = 0;
    for( Map.Entry<String,Float> entry : neighborScores.entrySet() )
      scores[i++] = new SortableScore(entry.getValue(), entry.getKey());
    Arrays.sort(scores);

    System.out.println("numscores = " + scores.length);

    // Return the top 100?
    for( i = 0; i < 70; i++ ) {
      System.out.println("chambers: " + scores[i].key() + "\t" + scores[i].score() + (filaWords.contains(scores[i].key()) ? " " : " **NEW**"));
      // TEMPORARY - add "v-" to match the filatova function
      filaWords.add("v-" + WordEvent.stripWordFromPOSTag(scores[i].key()));
    }
    return filaWords;
  }


  public static void main(String[] args) {
    DomainVerbDetector detector = new DomainVerbDetector(args);
    detector.detectVerbs();
  }
}
