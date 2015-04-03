package nate.reading;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import nate.cluster.ClusterUtil;
import nate.util.Triple;
import nate.util.Util;
import nate.util.WordNet;

/**
 * Represents clusters of words that are synonymous-like.
 * Names each cluster and stores basic information such as the general
 * type of word (e.g. person, physical-object, location).
 *
 */
public class WordClasses {
  Map<String,List<String>> wordClasses = null;
  Map<String,String> wordToClass = null;
  Map<String,FrameRole.TYPE> classToType = null;
  
  public WordClasses() {
  }

  /**
   * Add a list of words as a single synonymous set of words.
   * @return The name of the new word class for this list of words.
   */
  public String addWordClass(List<String> words) {
    if( words == null || words.size() == 0 )
      return null;
    if( wordClasses == null ) {
      wordClasses = new HashMap<String,List<String>>();
      wordToClass = new HashMap<String,String>();
    }
    
    List<String> thelist = new ArrayList<String>();

    String className = "-" + words.get(0) + "-";
    for( String word : words ) {
      thelist.add(word);
      if( wordToClass.containsKey(word) ) {
        System.err.println("ERROR: WordClasses already contains " + word + " as a " + wordToClass.get(word));
        System.exit(-1);
      }
      wordToClass.put(word, className);
    }
    wordClasses.put(className, thelist);
    return className;
  }

  
  /**
   * Lookup the word class for a word, the set of words that it belongs to,
   * represented by the class' name.
   * @param word A token (e.g. n-hostage)
   * @return The word class (e.g. -captive-)
   */
  public String getWordClass(String word) {
    if( wordToClass != null && word != null )
      return wordToClass.get(word);
    else return null;
  }
  
  /**
   * Lookup the type of words in a given word class, such as PERSON, EVENT, etc.
   * @param wordClassName The name of an existing class (set of words).
   * @return The general type.
   */
  public FrameRole.TYPE getClassType(String wordClassName) {
    if( classToType != null && wordClassName != null )
      return classToType.get(wordClassName);
    else return null;
  }
  
  /**
   * Determines what the general word type is for each set of synonymous words.
   * It looks up in WordNet each word, and counts how many of each type are seen,
   * saving the highest count as the set's overall type.
   */
  public void calculateWordTypes(WordNet wordnet) {
    if( wordClasses != null ) {
      for( String className : wordClasses.keySet() ) {

        // Count how many of each type we see.
        Map<FrameRole.TYPE,Integer> typeCounts = new HashMap<FrameRole.TYPE,Integer>();
        for( String word : wordClasses.get(className) ) {
          if( SlotTypeCache.isPerson(word, wordnet) ) 
            Util.incrementCount(typeCounts, FrameRole.TYPE.PERSON, 1);

          if( SlotTypeCache.isLocation(word, wordnet) ) 
            Util.incrementCount(typeCounts, FrameRole.TYPE.LOCATION, 1);

          if( SlotTypeCache.isPhysObject(word, wordnet) ) 
            Util.incrementCount(typeCounts, FrameRole.TYPE.PHYSOBJECT, 1);

          if( SlotTypeCache.isOther(word, wordnet) ) 
            Util.incrementCount(typeCounts, FrameRole.TYPE.OTHER, 1);

          if( SlotTypeCache.isEvent(word, wordnet) ) 
            Util.incrementCount(typeCounts, FrameRole.TYPE.EVENT, 1);
        }

        // Keep the type with the most counts;
        FrameRole.TYPE bestType = FrameRole.TYPE.OTHER;
//        int best = (typeCounts.containsKey(bestType) ? typeCounts.get(bestType) : 0);
        int best = 0;
        System.out.println("class " + className);

        for( FrameRole.TYPE type : typeCounts.keySet() ) {
          if( type != FrameRole.TYPE.OTHER ) {
            int count = typeCounts.get(type);
            System.out.println("type " + type + " count " + count);
//            if( (type != FrameRole.TYPE.OTHER && count > best ) ||
//                (type == FrameRole.TYPE.OTHER && count > best*2) ||
//                (bestType == FrameRole.TYPE.OTHER && count >= (double)best*.75) ) {
            if( count > best || (count == best && (bestType == FrameRole.TYPE.EVENT || bestType == FrameRole.TYPE.LOCATION)) ) {
              best = count;
              bestType = type;
            }
          }
        }
        if( typeCounts.containsKey(FrameRole.TYPE.OTHER) && typeCounts.get(FrameRole.TYPE.OTHER) > best*2 )
          bestType = FrameRole.TYPE.OTHER;

        if( classToType == null ) classToType = new HashMap<String, FrameRole.TYPE>();
        classToType.put(className, bestType);
      }
    }
  }
  
  /**
   * Creates a set of word classes based on the clustering history.  The history is from
   * the ClusterUtil package's output from a clustering algorithm.
   * @param history Output from a clusterer.
   * @param args The input words to the clustering algorithm, in the same order.
   * @return A new WordClasses object.
   */
  public static WordClasses fromClusterHistory(List<Triple> history, List<String> args) {
    WordClasses classes = new WordClasses();
    List<List<String>> clusters = ClusterUtil.reconstructClusters(history, args);
    for( List<String> cluster : clusters )
      classes.addWordClass(cluster);
    return classes;
  }
  
  public String toString() {
    StringBuffer sb = new StringBuffer();
    if( wordClasses != null ) {
      for( String name : wordClasses.keySet() ) {
        sb.append("[");
        sb.append(name);
        if( classToType != null )
          sb.append(" (" + classToType.get(name) + ")");
        sb.append(":\t");
        for( String word : wordClasses.get(name) ) {
          sb.append(word);
          sb.append(" ");
        }
        sb.append("] ");
      }
    }
    return sb.toString();
  }
}
