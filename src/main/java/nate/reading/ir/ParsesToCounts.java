package nate.reading.ir;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import nate.CalculateIDF;
import nate.GigawordHandler;
import nate.util.TreeOperator;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.trees.Tree;

/**
 * Reads files that contain parse trees of news stories and outputs the 
 * POS-token counts of all occurrences in each story.
 *
 */
public class ParsesToCounts {

  public ParsesToCounts() { }

  /**
   * Reads counts from a tab-separated file.  One Counter per line, keys and values
   * alternate after each tab.
   * @param filename The file to read.
   * @return A counter.
   */
  public static IntCounter<String> fromFile(String filename) {
    BufferedReader in = null;
    try {
      String line;
      in = new BufferedReader(new FileReader(filename));
      while( (line = in.readLine()) != null ) {
        return fromString(line);
      }
    } catch( IOException ex ) { ex.printStackTrace(); }
    return null;
  }
  
  /**
   * Parses a single line that is tab-separated keys/values from an IntCounter.
   * @param line The line with the counts.
   * @return A counter, unless the line is null, returns null.
   */
  public static IntCounter<String> fromString(String line) {
    if( line != null ) {
      IntCounter<String> counter = new IntCounter<String>();
      String[] parts = line.split("\t");
      for( int i = 0; i < parts.length; i++ ) {
        counter.incrementCount(parts[i], Integer.valueOf(parts[i+1]));
      }
      return counter;
    } else return null;
  }
  
  /**
   * Convert a counter to a single String line, tab-separated by keys and counts.
   * @param counter The counter to stringify.
   * @return A string of the counter, or null if the given counter is null.
   */
  public static String counterToString(IntCounter<String> counter) {
    if( counter != null ) {
      StringBuffer sb = new StringBuffer();
      int i = 0;
      for( String key : counter.keySet() ) {
        if( i++ > 0 ) sb.append('\t');
        sb.append(key);
        sb.append('\t');
        sb.append(counter.getIntCount(key));
      }
      return sb.toString();
    } else return null;
  }
  
  public static IntCounter<String> countPOSTokens(Collection<Tree> trees) {
    IntCounter<String> particleParents = new IntCounter<String>();
    IntCounter<String> tokens = new IntCounter<String>();
    for( Tree tree : trees ) {
//      System.out.println("tree: " + tree);
      Collection<Tree> leaves = TreeOperator.leavesFromTree(tree);
      for( Tree leaf : leaves ) {
        // Lowercase the token.
        String token = leaf.getChild(0).value().toLowerCase();
        String tag = leaf.value();

        // Particles.
        // e.g. (PRT (RP up))
        if( tag.equals("RP") ) {
          Tree gparent = leaf.ancestor(2, tree);
          Tree gparentTree = gparent.children()[0];
          String gVerbHead = gparentTree.getChild(0).value().toLowerCase();
          char gTag = CalculateIDF.normalizePOS(gparentTree.value());

          String headKey = CalculateIDF.createKey(gVerbHead + "_" + token, gTag);
//          System.out.println("Particle verb " + headKey);

//          System.out.println("  Incrementing " + headKey);
          tokens.incrementCount(headKey);
          particleParents.incrementCount(CalculateIDF.createKey(gVerbHead, gTag));
        }
        // All other words.
        else {
          // e.g. v-helping
          String key = CalculateIDF.createKey(token, CalculateIDF.normalizePOS(tag));
          tokens.incrementCount(key);
        }
      }
    }

    // Remove counts from verbs that are counted with their particles.
    for( Map.Entry<String, Double> entry : particleParents.entrySet() ) {
//      System.out.println("  Decrementing " + entry.getKey());
      tokens.decrementCount(entry.getKey(), entry.getValue());
    }

//    System.out.println("Returning " + tokens);
    return tokens;
  }

  
  /**
   * For debugging...
   */
  public static void main(String[] args) {
    GigawordHandler reader = new GigawordHandler(args[0]);
    
    Collection<String> parses = reader.nextStory();
    Counter<String> tokenCounts = ParsesToCounts.countPOSTokens(TreeOperator.stringsToTrees(parses));
    
    System.out.println(tokenCounts);
  }
}
