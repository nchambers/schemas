package nate;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.HashMap;
import java.util.Map;


public class WordIndex {
  HashMap<String,Integer> index = new HashMap();
  HashMap<Integer,String> indexToString = new HashMap();
  int count = 0;

  public WordIndex() { }
  public WordIndex(String filename) {
    setIndex(indexFromFile(filename));
    invertIndex();
  }


  private boolean isNumeral(String token) {
    if( token.matches("[\\d\\,\\-\\.$%]+") )
      return true;
    else return false;
  }


  /**
   * Index the given string word * lowercase before calling!
   */
  private void index(String word) {
    // Normalize all numerals
    if( isNumeral(word) ) word = "<NUM>";

    // Save the index
    if( !index.containsKey(word) )  index.put(word, ++count);
  }

  /**
   * Lookup the word for a given index.
   */
  public String indexToWord(Integer index) {
    return indexToString.get(index);
  }

  /**
   * Write a String->Integer mapping to a file
   * @param index HashMap of Strings to Strings
   * @param filename The file to write to
   */
  public void indexToFile(HashMap<String,Integer> index, String filename) {
    try {
      BufferedWriter out = new BufferedWriter(new FileWriter(filename));
      for( String str : index.keySet() ) {
	out.write(str + " " + index.get(str) + "\n");
      }
      out.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
  }


  /**
   * Read an index from a text file
   * @return Mapping from String to Integer
   */
  public HashMap<String,Integer> indexFromFile(String filename) {
    HashMap<String,Integer> index = new HashMap();
    String line;

    try {
      BufferedReader in = new BufferedReader(new FileReader(filename));
      while( (line = in.readLine()) != null ) {
	int pos = line.lastIndexOf(' ');
	Integer ind = Integer.parseInt(line.substring(pos+1));
	index.put(line.substring(0,pos), ind);
      }
      in.close();
    } catch( Exception ex ) { ex.printStackTrace(); }

    return index;
  }


  /**
   * Create the opposite lookup table.
   */
  public void invertIndex() {
    for( Map.Entry<String,Integer> entry : index.entrySet() ) {
      indexToString.put(entry.getValue(), entry.getKey());
    }
  }


  /**
   * Set the hashmap and last word index
   */
  public void setIndex(HashMap newindex) {
    index = newindex;
    count = 0;
    for( Integer val : index.values() ) {
      if( val > count ) count = val;
    }
    System.out.println("Set words max: " + count);
  }



  /**
   * Returns the integer for a word, or if it doesn't exist, creates a
   * new index and adds it to our list.
   */
  public Integer get(String word) {
    String atom;
    if( isNumeral(word) ) atom = "<NUM>";
    else atom = word.toLowerCase();

    if( !index.containsKey(atom) ) {
      //      System.out.println("ERROR: new word " + atom);
      index(atom);
    }
    return index.get(atom);
  }

  public HashMap<String,Integer> index() { return index; }

}