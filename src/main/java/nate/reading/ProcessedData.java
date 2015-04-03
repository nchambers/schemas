package nate.reading;

import java.util.List;
import java.util.Vector;

import edu.stanford.nlp.trees.TypedDependency;

import nate.EntityMention;
import nate.GigaDocReader;
import nate.GigawordHandler;
import nate.GigawordProcessed;
import nate.NERSpan;


/**
 * This is a class that stores all the processed text formats in one place, namely all the
 * parse trees, dependencies, coref, and NER classes.
 * It opens the files that contain each of these and reads one story in at a time when the
 * API nextStory() is called.
 * 
 * The only required file is a file of parses.  The others can be null.
 */
public class ProcessedData {
  GigawordHandler _parseReader = null;
  GigaDocReader _eventReader = null;
  GigaDocReader _depsReader = null;
  GigaDocReader _nerReader = null;
  Vector<String> _parseStrings;

  String _pPath;
  String _dPath;
  String _cPath;
  String _nPath;
  
  public ProcessedData(String parsePath, String depsPath, String corefPath, String nerPath) {
    _pPath = parsePath;
    _dPath = depsPath;
    _cPath = corefPath;
    _nPath = nerPath;
    reset();
  }

  public void reset() { 
    if( _pPath != null ) _parseReader = new GigawordProcessed(_pPath);
    if( _dPath != null )  _depsReader  = new GigaDocReader(_dPath);
    if( _cPath != null ) _eventReader = new GigaDocReader(_cPath);
    if( _nPath != null )   _nerReader   = new GigaDocReader(_nPath);
    System.out.println("ProcessedData reset() " + _pPath + " - " + _dPath + " - " + _cPath + " - " + _nPath);
  }
  
  public String currentStory() {
    if( _parseReader == null ) {
      System.out.println("ERROR: null parses");
      System.exit(-1);
    }
    return _parseReader.currentStory();
  }

  public int currentStoryNum() {
    if( _parseReader == null ) {
      System.out.println("ERROR: null parses");
      System.exit(-1);
    }
    return _parseReader.currentStoryNum();
  }

  public int currentDoc() {
    if( _parseReader == null ) {
      System.out.println("ERROR: null parses");
      System.exit(-1);
    }
    return _parseReader.currentDoc();
  }
  
  public void nextStory() {
    if( _parseReader == null ) {
      System.out.println("ERROR: null parses");
      System.exit(-1);
    }

    _parseStrings = _parseReader.nextStory();
    if( _eventReader != null )
      _eventReader.nextStory(_parseReader.currentStory());
    if( _depsReader != null )
      _depsReader.nextStory(_parseReader.currentStory());
    if( _nerReader != null )
      _nerReader.nextStory(_parseReader.currentStory());
  }
  
  /**
   * Advance to a specific story.
   */
  public void nextStory(String storyname) {
    if( _parseReader == null ) {
      System.out.println("ERROR: null parses");
      System.exit(-1);
    }

    _parseStrings = _parseReader.nextStory(storyname);
    if( _eventReader != null )
      _eventReader.nextStory(storyname);
    if( _depsReader != null )
      _depsReader.nextStory(storyname);
    if( _nerReader != null )
      _nerReader.nextStory(storyname);
  }

  public Vector<String> getParseStrings() {
    return _parseStrings;
  }
  
  public List<EntityMention> getEntities() {
    return _eventReader.getEntities();
  }
  
  public List<List<TypedDependency>> getDependencies() {
    return _depsReader.getDependencies();
  }
  
  public ProcessedDocument getDocument() {
    return new ProcessedDocument(_parseReader.currentStory(), getParseStrings(), getDependencies(), getEntities(), getNER());
  }
  
  public List<NERSpan> getNER() {
    if( _nerReader != null )
      return _nerReader.getNER();
    else return null;
  }
  
  public void close() {
    if( _eventReader != null )
      _eventReader.close();
    if( _depsReader != null )
      _depsReader.close();
    if( _nerReader != null )
      _nerReader.close();
  }
}
