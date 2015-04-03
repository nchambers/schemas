package nate.reading;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import nate.BasicEventAnalyzer;
import nate.CalculateIDF;
import nate.IDFMap;
import nate.Pair;
import nate.util.Util;
import nate.util.WordNet;
import net.didion.jwnl.data.POS;
import net.didion.jwnl.data.Synset;


/**
 * This class runs the old Resnik WordNet 'information content' idea.  It counts
 * how many words were seen under any given synset's entire subtree.  The root
 * will contain the entire corpus count.  Information content is then calculated
 * as the portion of the corpus under a synset, divided by the total root count.
 * IC = -log(P(synset))
 * 
 * This code appends the information content scores to the words in an IDF file.
 * Each word is scored by the first synset wordnet returns.
 * 
 * java InformationContent <wordnet-jwnl-file> <idf-file>
 *
 */
public class InformationContent {
  WordNet _wordnet;
  Map<Integer,Integer> _synsetCounts;
  int _totalAtWordnetRoot; // total number of tokens that percolated to the top!
  
  public InformationContent(IDFMap idf, WordNet wordnet) {
    calculateContent(idf, wordnet);
    _wordnet = wordnet;
  }
  
  public void calculateContent(IDFMap idf, WordNet wordnet) {
    _synsetCounts = new HashMap<Integer,Integer>();

    int i = 0;
    for( String word : idf.getWords() ) {
      // Don't bother on rare words...
      if( idf.getFrequency(word) > 0 ) {
        if( i % 500 == 0 ) {
          System.out.println(i + "/" + idf.getWords().size() + ":\t" + word);
          Util.reportMemory();
        }
        Pair split = CalculateIDF.splitKey(word);
        String token = (String)split.second();
        char pos = (Character)split.first();
        // Only do verbs and nouns.
//        if( pos == 'v' || pos == 'n' ) {
          POS type = POS.NOUN;
          if( pos == 'v' ) type = POS.VERB;
          else if( pos == 'j' ) type = POS.ADJECTIVE;

          Set<Synset> ancestors = wordnet.getAllSynsetAncestors(token, type);
          if( ancestors != null)
            for( Synset synset : ancestors )
              Util.incrementCount(_synsetCounts, synset.hashCode(), idf.getFrequency(word));
//        }
      }
//      if( i == 3000 ) break;
      i++;
    }
    
    Synset root = wordnet.getRootSynset();
    System.out.println("root synset\t" + root);
    _totalAtWordnetRoot = _synsetCounts.get(root.hashCode());
    System.out.println("root total\t" + _totalAtWordnetRoot);
  }

  public void addInformationContentScores(IDFMap idf) {
    int i = 0;
    for( String word : idf.getWords() ) {
      // Don't bother on rare words...
      if( idf.getFrequency(word) > 5 ) {
        double score = getICAtSynset(word);
        idf.setInformationContent(word, (float)score);
      }
      i++;
    }
  }
  
  
  public double getCountAtSynset(String key) {
    Pair split = CalculateIDF.splitKey(key);
    String token = (String)split.second();
    char pos = (Character)split.first();
    POS type = POS.NOUN;
    if( pos == 'v' ) type = POS.VERB;
    else if( pos == 'j' ) type = POS.ADJECTIVE;
    return getCountAtSynset(token, type);
  }
  
  /**
   * Get how many tokens were seen under the synset for the given token.
   */
  public int getCountAtSynset(String token, POS type) {
    Synset[] synsets = _wordnet.synsetsOf(token, type);
    if( synsets != null && synsets.length > 0 ) {
      Integer score = _synsetCounts.get(synsets[0].hashCode());
      if( score == null ) return 0;
      else return score;
    }
    else return 0;
  }
  
  public double getICAtSynset(String key) {
    Pair split = CalculateIDF.splitKey(key);
    String token = (String)split.second();
    char pos = (Character)split.first();
    POS type = POS.NOUN;
    if( pos == 'v' ) type = POS.VERB;
    else if( pos == 'j' ) type = POS.ADJECTIVE;
    return getICAtSynset(token, type);
  }

  public double getICAtSynset(String token, POS type) {
    int count = getCountAtSynset(token, type);
    if( count == 0 )
      return 0.0;
    
    double prob =  (double)count / (double)_totalAtWordnetRoot;
    double score = -1 * Math.log(prob);
    if( Double.isInfinite(score) )
      return 0.0;
    else
      return score;
  }  
    
  
  public static void main(String[] args) {
    WordNet net = new WordNet(args[0]);
    IDFMap idf = new IDFMap(args[1]);

    InformationContent ic = new InformationContent(idf, net);
    ic.addInformationContentScores(idf);
    idf.saveToFile("tokens-lemmas-ic.idf");
    
    String[] tests = { "n-person", "n-bomb", "n-device", "n-car", "n-vehicle", "n-hostage", "n-suspect", "n-building", "n-terrorist" };
    for( String test : tests )
      System.out.println("IC of " + test + ":\t " + ic.getCountAtSynset(test) + "\t" + ic.getICAtSynset(test));
  }
}
