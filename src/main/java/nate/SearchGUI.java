package nate;

import java.awt.event.MouseListener;
import java.awt.event.ActionListener;
import java.awt.event.KeyListener;
import java.util.Vector;
import java.util.Set;

import java.awt.TextArea;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.DefaultListModel;

import java.awt.BorderLayout;


public class SearchGUI {
  JFrame main;
  TextArea text;
  // List of documents/stories that are search results
  JFrame storyFrame;
  JList storyList;
  DefaultListModel listModel = new DefaultListModel();
  // Search box
  JTextField searchBox;
  // Text searching
  JFrame tsearchFrame;
  JTextField tsearchBox;

  String currentText = ""; // current story in the window
  int marker = 0;

  int listWidth = 200;
  int listHeight = 500;
  int textWidth = 500;
  int textHeight = listHeight;


  SearchGUI() {
  }

  public void initialize() {
    // Panel to show list of stories
    initStoryFrame();
    // Text search box
    initTextSearchFrame();

    main = new JFrame();
    text = new TextArea("",30,100,TextArea.SCROLLBARS_VERTICAL_ONLY);
    //    text.select(14,18);

    main.add(text);
    main.setSize(textWidth,textHeight);
    //    main.setLocationRelativeTo(storyFrame);
    main.setLocation(listWidth,0);
    main.show();
  }

  private void initStoryFrame() {
    // JList
    storyList = new JList(listModel);
    JScrollPane scrollPane = new JScrollPane(storyList);

    // TextField
    searchBox = new JTextField(30);

    // Main Frame
    storyFrame = new JFrame();
    storyFrame.add(scrollPane);
    storyFrame.add(searchBox, BorderLayout.PAGE_END);
    storyFrame.setSize(listWidth,listHeight);
    storyFrame.show();
  }

  private void initTextSearchFrame() {
    // TextField
    tsearchBox = new JTextField(20);

    // Main Frame
    tsearchFrame = new JFrame();
    tsearchFrame.add(tsearchBox);
    tsearchFrame.setSize(100,50); // doesn't get big enough by itself
  }


  public void addMouseListener(MouseListener listener) {
    storyList.addMouseListener(listener);
  }
  public void addActionListener(ActionListener listener) {
    searchBox.addActionListener(listener);
    tsearchBox.addActionListener(listener);
  }
  public void addKeyListener(KeyListener listener) {
    text.addKeyListener(listener);
    tsearchBox.addKeyListener(listener);
  }


  /**
   * Search the text area for a substring
   */
  public void searchNew(String search) {
    marker = 0;
    searchNext(search);
    main.show();
  }

  public void searchNext(String search) {
    int found = currentText.indexOf(search.toLowerCase(), marker);
    if( found >= 0 ) {
      text.select(found,found+search.length());
      marker = found+1;
    }
  }

  public void searchPrevious(String search) {
    int previous = -1;

    int found = currentText.indexOf(search.toLowerCase());
    while( found < marker-1 ) {
      previous = found;
      found = currentText.indexOf(search.toLowerCase(), previous+1);
    }

    if( previous > -1 ) {
      text.select(previous,previous+search.length());      
      marker = previous+1;
    }
  }

  /**
   * Change the list of stories
   */
  public void showStoryResults(Vector<String> stories) {
    if( stories != null )
      storyList.setListData(stories);
  }

  public void showStoryResults(Object stories[]) {
    if( stories != null )
      storyList.setListData(stories);
  }

  /**
   * Clear the list of stories
   */
  public void clearStoryResults() {
    listModel.clear();
  }

  /**
   * Remove all the text from the story screen
   */
  public void clearText() {
    text.setText("");
  }

  public void showTextSearch() {
    tsearchFrame.show();
  }

  public void hideTextSearch() {
    tsearchFrame.hide();
  }

  public void clearTextSearch() {
    tsearchBox.setText("");
  }

  /**
   * Replace all text with this text
   */
  public void showStory(String newtext) {
    currentText = newtext.toLowerCase();
    //    System.out.println("Showing story..." + newtext.substring(0,20));
    text.setText(newtext);
    main.show();
  }

  public JList storyList() {
    return storyList;
  }

  public JTextField searchBox() {
    return searchBox;
  }
  public JTextField textSearchBox() {
    return tsearchBox;
  }

  public String getSelectedStory() { 
    return (String)storyList.getSelectedValue();
  }

  public String getSearchString() {
    return searchBox.getText();
  }

  public String getTextSearchString() {
    return tsearchBox.getText();
  }
}
