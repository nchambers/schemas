package nate.reading;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import edu.stanford.nlp.trees.TypedDependency;

import nate.GigaDocReader;
import nate.IDFMap;
import nate.NERSpan;
import nate.util.Util;

/**
 * Takes a file of dependencies and a file of NE tags for those docs.
 * It counts all tokens that are in the gov or dep slots of typed dependencies
 * whose indices are part of an NE tag.
 * This is a quick way to get all the tokens that are parts of names and organizations.
 */
public class NamedEntityWords {
  GigaDocReader _nerReader;
  GigaDocReader _depsReader;
  IDFMap _idf;
  Set<String> _thewords = null;
  

  public NamedEntityWords(String depsPath, String nerPath, IDFMap idf) {
    if( nerPath != null ) _nerReader   = new GigaDocReader(nerPath);
    if( depsPath != null ) _depsReader   = new GigaDocReader(depsPath);
    _idf = idf;
  }

  public Set<String> getWords() {
    if( _thewords != null ) return _thewords;

    // Count how many times each word has an NE label.
    Map<String,Integer> counts = new HashMap<String, Integer>();
    while( _depsReader.nextStory() ) {
      _nerReader.nextStory(_depsReader.currentStory());
      System.out.println(_depsReader.currentStory());
    
      Collection<String> words = getWords(_depsReader.getDependencies(), _nerReader.getNER());
      for( String word : words ) {
        Util.incrementCount(counts, word.toLowerCase(), 1);
      }
    }
    
    _depsReader.close();
    _nerReader.close();

    // Save the words that were seen with NE labels often.
    Set<String> allwords = new HashSet<String>();
    for( Map.Entry<String, Integer> entry : counts.entrySet() ) {
      int freq = _idf.getFrequency("n-" + entry.getKey());
      float ratio = (float)entry.getValue()/(float)freq;
      System.out.println("ratio " + entry.getKey() + " = " + ratio);
      if( ratio > .2 ) {//entry.getValue() > 2 ) {        
        allwords.add(entry.getKey());
      }
    }

    _thewords = allwords;
    return allwords;
  }
  
  public List<String> getWords(List<List<TypedDependency>> deps, List<NERSpan> ners) {
    List<String> words = new ArrayList<String>();
    
    for( NERSpan span : ners ) {
//      System.out.println("span " + span);
      List<TypedDependency> sentdeps = deps.get(span.sid());
      for( TypedDependency dep : sentdeps ) {
        int govindex = dep.gov().index();
        int depindex = dep.dep().index();
        if( span.start() <= govindex && span.end() > govindex ) {
          words.add(dep.gov().value());
//          System.out.println("  adding " + dep.gov().value());
        }
        if( span.start() <= depindex && span.end() > depindex ) {
          words.add(dep.dep().value());
//          System.out.println("  addin2 " + dep.dep().value());
        }
      }
    }
    
    return words;
  }
}
