package nate;

import java.io.File;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import nate.util.Util;


/**
 * Counts words in Gigaword for MYSQL storage to search for
 * documents with specific words.
 *
 * MYSQL CREATION
 * create table idToDoc (id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, docName varchar(20));
 * create table idToWord (id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, word varchar(50));
 * create table wordCounts (wordID INT, docID INT, count INT);
 *
 * alter table idToDoc ADD INDEX(docName);
 * alter table idToWord ADD INDEX(word);
 * alter table wordCounts ADD INDEX(wordID,docID);
 * 
 */
public class CountWords {
  public static String DOMAIN = "gigaword";
  public static String ID_TO_DOC = "idToDoc";
  public static String ID_TO_WORD = "idToWord";
  public static String WORD_COUNTS = "wordCounts";

  String duplicatesPath = "duplicates";
  String dataPath;
  int startingFile = 0;
  Database db;
  HashMap<String,Integer> wordToID;
  HashSet<String> duplicates;
  HashMap<String,Integer> counts = new HashMap(1024); // global for memory efficiency
  private IDFMap idf;
  private String idfCache = "";


  CountWords(String[] args) {
    handleParameters(args);
    db = new Database(DOMAIN);
    wordToID = new HashMap(320000);
    // Duplicate Gigaword files to ignore
    duplicates = GigawordDuplicates.fromFile(duplicatesPath);
    // IDFs for verbs 
    idf = new IDFMap(75000);
    idf.fromFile(idfCache);
  }


  private void handleParameters(String[] args) {
    int i = 0;
    while( i < args.length ) {
      if( args[i].equalsIgnoreCase("-start") && args.length > i+1 ) {
	startingFile = Integer.parseInt(args[i+1]);
	System.out.println("Start parsing file " + startingFile);
	i++;
      }
      else if( args[i].equalsIgnoreCase("-idf") && args.length > i+1 ) {
	idfCache = args[i+1];
      }
      i++;
    }

    dataPath = args[args.length - 1];
  }


  /**
   * @returns The ID number for the story, creates a new one if non-existent
   */
  private int storyToID(String storyname) {
    String select = "SELECT id FROM " + ID_TO_DOC + " WHERE docName LIKE'" + storyname + "'";

    try {
      // Check if the story already has an ID
      ResultSet result = db.query(select);
      if( result.next() ) {
	return Integer.parseInt(result.getString("id"));
      }
      // Else generate an ID for the new story
      else {
	String insert = "INSERT INTO " + ID_TO_DOC + " (docName) VALUES ('" + storyname + "')";
	String arr[] = { "id" };
	result = db.insert(insert,arr);
	if( result.next() ) return result.getInt(1);
	else {
	  System.out.println("ERROR storyToID, no result auto-generated");
	  System.exit(1);
	  return -1;
	}
      }
    } catch( Exception ex ) { ex.printStackTrace(); }
    return -1;
  }


  /**
   * @returns The ID number for the word, creates a new one if non-existent
   */
  public static int wordToIDSQL(String word, Database db) {
    //    System.out.println("Looking up wordID for " + word);
    String select = "SELECT id FROM " + ID_TO_WORD + " WHERE word LIKE '" + word + "'";

    try {
      // Check if the word already has an ID
      ResultSet result = db.query(select);
      if( result.next() ) {
	//	System.out.println("Found word id");
	return Integer.parseInt(result.getString("id"));
      }
      // Else generate an ID for the new story
      else {
	//	System.out.println("Word doesn't exist yet");
	String insert = "INSERT INTO " + ID_TO_WORD + " (word) VALUES ('" + word + "')";
	String arr[] = { "id" };
	result = db.insert(insert,arr);

	if( result.next() ) {
	  return result.getInt(1);
	} else {
	  System.out.println("ERROR wordToIDSQL, no result auto-generated");
	  System.exit(1);
	  return -1;
	}
      }
    } catch( Exception ex ) { ex.printStackTrace(); System.exit(1);}
    return -1;
  }

  /**
   * This is a cache wrapper around the SQL function to 
   * speed up repeated queries.
   * @returns The ID number for the word, creates a new one if non-existent
   */
  private int wordToID(String word) {
    // check the cache first
    if( wordToID.containsKey(word) ) return wordToID.get(word);
    else {
      int wordID = wordToIDSQL(word, db);
      if( wordID != -1 ) wordToID.put(word,wordID);
      else {
      	System.out.println("ERROR: no ID generated for word");
      	System.exit(1);
      }
      return wordID;
    }
  }

  /**
   * Adds a count for this word in this story

  private void countWord(String word, int storyID) {
    word = cleanWord(word);

    // Make sure the word is not empty
    if( word.length() > 0 ) {
      // SQL only takes 50 chars per word as I set it up
      if( word.length() > 50 ) word = word.substring(0,49);

      // Get the word's ID
      int wordID = wordToID(word);
      //      System.out.println("word " + word + " id " + wordID);

      // Check if the word exists in the story already
      String select = "SELECT count FROM " + WORD_COUNTS + " WHERE wordID=" + wordID + " AND docID=" + storyID;
      ResultSet result = db.query(select);
      try {
	// Yes, we have a word count in this story
	if( result.next() ) {
	  //	  System.out.println("Seen already, adding 1...");
	  int count = result.getInt("count");
	  String update = "UPDATE " + WORD_COUNTS + " SET count=" + (count+1) + " WHERE wordID=" + wordID + " AND docID=" + storyID;
	  db.insert(update);
	}
	// No, this is the first time we see this word in this story
	else {
	  //	  System.out.println("New in story...");
	  String insert = "INSERT INTO " + WORD_COUNTS + " (wordID, docID, count) VALUES (" + 
	    wordID + "," + storyID + ",1)";
	  db.insert(insert);
	}
      } catch( Exception ex ) { ex.printStackTrace(); }
    }
  }
   */


  /**
   * Adds a count for this word in this story
   * @param storyID The ID for the story
   * @param word An already cleaned word
   * @param count The number to put in the database
   */
  private void setWordCount(int storyID, String word, int count) {

    // Make sure the word is not empty
    if( word.length() > 0 ) {
      // SQL only takes 50 chars per word as I set it up
      if( word.length() > 50 ) word = word.substring(0,49);

      // Get the word's ID
      int wordID = wordToID(word);
      //      System.out.println("word " + word + " id " + wordID);

      // Execute the insertion
      String insert = "INSERT INTO " + WORD_COUNTS + " (wordID, docID, count) VALUES (" + 
      	wordID + "," + storyID + ","  + count + ")";
      db.insert(insert);
      //      db.addBatch(insert);
    }
  }


  /**
   * Counts the tokens in all the sentences. Saves to SQL.
   */
  private void analyzeSentences(Vector<String> sentences, String storyname) {
    int storyID = storyToID(storyname);
    counts.clear();

    // Count all the words in the sentences
    for( String sentence : sentences ) {
      String words[] = sentence.split("\\s+");
      
      for( String word : words ) {
	word = cleanWord(word);
	// Only count words that are seen more than once
	if( idf.getDocCount(word) > 1 ) {
	//		if( word.length() > 0 ) {
	  if( counts.containsKey(word) ) counts.put(word,counts.get(word)+1);
	  else counts.put(word,1);
	}
      }
    }

    // Put all the counts for this story in SQL
    for( String word : counts.keySet() ) {
      //      System.out.println(storyID + " " + word + " " + counts.get(word));
      setWordCount(storyID, word, counts.get(word));
    }
  }



  public void processData() {
    int numDocs = 0;
    int numFiles = 0;

    if( dataPath.length() > 0 ) {
      
      File dir = new File(dataPath);
      if( dir.isDirectory() ) {
	String files[] = dir.list();

	for( String file : files ) {
	  // Only look at *.gz files
	  //	  if( !file.startsWith(".") && file.endsWith(".gz") ) {
	  if( file.contains("_1994") || file.contains("_1995") ) {
	    if( numFiles >= startingFile ) {
	      System.out.println("file: " + file);
	      GigawordHandler giga = new GigawordHandler(dataPath + File.separator + file);

	      // Read the documents in this file 
	      Vector<String> sentences = giga.nextStory();
	      while( sentences != null ) {
		if( duplicates.contains(giga.currentStory()) )
		  System.out.println("Duplicate " + giga.currentStory());
		else {
		  numDocs++;
		  System.out.println(numDocs + ": (" + giga.currentDoc() + "/" + giga.numDocs() + ") " + giga.currentStory);

		  analyzeSentences(sentences, giga.currentStory());

		  if( numDocs % 250 == 0 ) Util.reportMemory();
		  //	      if( numDocs == 2 ) return;
		}

		sentences = giga.nextStory();
	      }
	    }
	    numFiles++;
	  }
	}
      }
    }
  }

  /**
   * @param word A single token to be normalized
   * @return The token with abbreviation stripped and lowercased
   */
  public String cleanWord(String word) {
    // don't trim abbreviations
    //    if( word.endsWith(".") && word.length() > 1 &&
    //	Character.isUpperCase(word.charAt(word.length()-2)) ) { }

    if( word.length() > 0 ) {
      int start = 0;
      int end = word.length();

      while( end > 0 && !Character.isLetterOrDigit(word.charAt(end-1)) ) end--;
      while( start < end && !Character.isLetterOrDigit(word.charAt(start)) ) start++;

      if( start == end ) word = "";
      else if( start > 0 || end < word.length() ) word = word.substring(start,end);
    }

    /*
    // trim all ending punctuation
    while( word.matches(".*[\\(\\)\\-\\.\\,\\?\\!\\'\\`\"]") ) {
      word = word.substring(0,word.length()-1);
    }
    // trim all starting punctuation
    while( word.matches("[\\(\\)\\-\\.\\,\\?\\!\\'\\`\"].*") ) {
      word = word.substring(1,word.length());
    }
    */

    // escape all quotes
    //    System.out.print(word);
    if( word.indexOf('\\') > -1 ) word = word.replace("\\","\\\\");
    if( word.indexOf('\'') > -1 ) word = word.replace("'","\\'");
    //    System.out.println("..." + word);
    // return lowercased
    return word.toLowerCase();
  }


  public static void main(String[] args) {
    CountWords cw = new CountWords(args);
    cw.processData();
  }
}