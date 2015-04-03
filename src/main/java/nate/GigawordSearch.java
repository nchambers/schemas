package nate;

import java.awt.event.MouseListener;
import java.awt.event.MouseEvent;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;
import java.io.File;

import java.util.Vector;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import java.sql.ResultSet;


/**
 * Interfaces with a mysql database of word counts, to allow basic
 * document searching for tokens.
 */
public class GigawordSearch implements MouseListener, ActionListener, KeyListener {
  String DOMAIN = "gigaword";
  Database db;
  String searchTerms[];
  SearchGUI gui;
  String dataPath; // directory of gigaword files


  GigawordSearch(String[] args) {
    handleParameters(args);
    db = new Database(DOMAIN);
    gui = new SearchGUI();
    gui.initialize();    
    gui.addMouseListener(this);
    gui.addActionListener(this);
    gui.addKeyListener(this);
  }


  private void handleParameters(String[] args) {
    dataPath = args[0];
  }


  /**
   * @return A vector of story names that contain all the words
   */
  public Set<String> docsWithWordIDs(Vector<Integer> words) {
    String base = "select d.docName FROM idToDoc d, wordCounts w WHERE w.docID=d.id AND w.wordID=";
    boolean first = true;
    Set intersected = new HashSet();

    // Search for each word, intersect their results
    for( Integer id : words ) {
      String select = base + id;

      // Send the query
      Set temp = new HashSet();
      ResultSet results = db.query(select);
      try {
	while( results.next() ) temp.add( results.getString(1) );
      } catch( Exception ex ) { ex.printStackTrace(); }

      // Intersect the results
      if( first ) intersected = temp;
      else intersected.retainAll(temp);
      first = false;
    }

    return intersected;
  }


  /**
   * @return A vector of story names that contain all the wordss
   */
  //  public Vector<String> docsWithWords(Vector<String> words) {
  public Set<String> docsWithWords(String words[]) {
    Vector<Integer> ids = new Vector();

    for( String word : words ) {
      int id = CountWords.wordToIDSQL(word,db);
      if( id == -1 ) System.out.println("Unseen word: " + word);
      else ids.add(id);
    }

    return docsWithWordIDs(ids);
  }

  public void userSearched(String search) {
    search = search.trim();
    if( search.length() > 0 ) {
      String terms[] = search.split("\\s+");
      
      Set<String> docs = docsWithWords(terms);
      System.out.println("Found " + docs.size() + " matching docs");

      String sorted[] = new String[docs.size()];
      docs.toArray(sorted);
      Arrays.sort(sorted);
      gui.showStoryResults(sorted);
    }    
  }

  public void search() {
    Set<String> stories = docsWithWords(searchTerms);
    for( String name : stories ) {
      System.out.println(name);
    }
  }

  /**
   * Read the story from file, show it in the GUI text pane
   */
  public void showStory(String storyName) {
    String file = storyName.substring(0,14) + ".gz";
    GigawordHandler gh = new GigawordHandler(dataPath + File.separator + file);

    String story = gh.getStory(storyName);
    gui.showStory(story);
  }


  /**
   * MOUSE LISTENER FUNCTIONS
   */
  public void mouseClicked(MouseEvent e) {
    if (e.getClickCount() == 2) {
      //      System.out.println("Double clicked on " + gui.getSelectedStory());
      gui.showStory("Loading...");
      showStory(gui.getSelectedStory());
    }
  }
  public void mouseEntered(MouseEvent e) { }
  public void mouseExited(MouseEvent e) { }
  public void mousePressed(MouseEvent e) { }
  public void mouseReleased(MouseEvent e) { }


  /**
   * ACTION LISTENER FUNCTIONS
   */
  public void actionPerformed(ActionEvent e) {
    //    System.out.println(e);
    // story search box
    if( e.getSource() == gui.searchBox() ) {
      gui.clearStoryResults();
      userSearched(gui.getSearchString());
    } 
    // text search box
    else {
      gui.searchNew(gui.getTextSearchString());
    }
  }

  /**
   * KEY LISTENER FUNCTIONS
   */ 
  public void keyPressed(KeyEvent e) { }
  public void keyReleased(KeyEvent e) { }
  public void keyTyped(KeyEvent e) {
    if( e.getKeyChar() == 'f' ) {
      if( e.isMetaDown() || e.isControlDown() || e.isAltDown() )
	gui.showTextSearch();
    }
    else if( e.getKeyChar() == 'g' ) {
      if( e.isMetaDown() || e.isControlDown() || e.isAltDown() )
	if( e.isShiftDown() )
	  gui.searchPrevious(gui.getTextSearchString());
	else gui.searchNext(gui.getTextSearchString());
    }
    // OSX has some mysterious key for Meta-Delete...
    // The normal functions don't work, so I have to search the param string
    else if( e.paramString().indexOf("Delete") > -1 ) {
      if( e.getSource() == gui.textSearchBox() )
	gui.clearTextSearch();
    }
  }

  public static void main(String[] args) {
    GigawordSearch gs = new GigawordSearch(args);
    //    gs.search();
  }
}
