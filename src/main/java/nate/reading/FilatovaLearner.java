package nate.reading;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import nate.CountTokenPairs;
import nate.EntityMention;
import nate.IDFMap;
import nate.NERSpan;
import nate.args.CountArgumentTypes;
import nate.args.VerbArgCounts;
import nate.util.Ling;
import nate.util.SortableObject;
import nate.util.SortableScore;
import nate.util.TreeOperator;
import nate.util.Util;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TypedDependency;

public class FilatovaLearner {
  VerbArgCounts _domainSlotArgCounts;
  List<String> _topWords;
  IDFMap _domainIDF;
  IDFMap _generalIDF;
  
  public FilatovaLearner(IDFMap domainIDF, IDFMap generalIDF, VerbArgCounts counts) {
    _domainIDF = domainIDF;
    _generalIDF = generalIDF;
    _domainSlotArgCounts = counts;
  }
  
  public List<String> getTopVerbs(String scoreType) {
    DomainVerbDetector verbDetector = new DomainVerbDetector(_domainIDF, _generalIDF);

    List<String> topWords = null;    
    if( scoreType.equals("fila") ) {
      topWords = verbDetector.detectWordsFilatova(true);
    }
    else if( scoreType.equals("chambers") ) {
//      topWords = verbDetector.detectWordsChambers(_generalPairScores);
    }
    else if( scoreType.equals("likelihood") ) {
      topWords = verbDetector.detectWordsOnlyRelativeFrequencyRatio(true);
    }
    else if( scoreType.equals("salience") ) {
//      topWords = verbDetector.detectWordsOnlyDiscourseSalience(_relnCountsDomain);
    }
    // Filatova's paper takes the top 50.
    Util.firstN(topWords, 50);

    _topWords = topWords;
    return topWords;
  }
  
  
  public List<String> getVerbSlots(String word) {
    System.out.println("getVerbSlots word " + word);
    List<SortableScore> scores = new ArrayList<SortableScore>();
    for( String slot : _domainSlotArgCounts.keysThatStartWith(word) ) {
      Map<String,Integer> argCounts = _domainSlotArgCounts.getArgsForSlot(slot);
      for( Map.Entry<String, Integer> entry : argCounts.entrySet() ) {
        scores.add(new SortableScore(entry.getValue(), slot + "-" + entry.getKey()));
      }
    }
    
    SortableScore[] arr = new SortableScore[scores.size()];
    arr = scores.toArray(arr);
    Arrays.sort(arr);
    
    List<String> slots = new ArrayList<String>();
    for( SortableScore score : arr ) {
      if( score.score() > 1 )
        slots.add(score.key());
    }
    
    return slots;
  }
  
 
}
