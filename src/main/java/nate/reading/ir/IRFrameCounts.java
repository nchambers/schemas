package nate.reading.ir;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import nate.BasicEventAnalyzer;
import nate.CountTokenPairs;
import nate.IDFMap;
import nate.args.CountArgumentTypes;
import nate.args.VerbArgCounts;
import nate.util.Directory;
import nate.util.Util;

/**
 * This class holds all of the token, pair, and idf counts from a corpus that
 * only has files for specific learned frames.  For instance, a kidnap frame
 * has retrieved similar documents to the frame from gigaword, and this class
 * stores all of the token counts from that subcorpus.
 * 
 */
public class IRFrameCounts {
  String _distPath;
  String _corefPath;
  String _argPath;
  String _idfPath;
  Collection<String> _desiredSlots;
  
  private CountTokenPairs _pairDistanceCounts;
  private CountTokenPairs _pairCorefCounts;
  private VerbArgCounts _slotArgCounts;
  private IDFMap _idf;
  private int _frameID;
      
  /**
   * 
   * @param distPath    Path to slot pairs based on distance.
   * @param corefPath   Path to slot pairs based on coref arguments.
   * @param slotArgPath Path to argument counts per slot.
   * @param idfPath     Path to token counts and idf scores.
   * @param keySlots    Set of slots (v-kidnap:s) that we want to load from disc.
   */
  public IRFrameCounts(int id, String distPath, String corefPath, String slotArgPath, String idfPath, Collection<String> keySlots) {
    _distPath = distPath;
    _corefPath = corefPath;
    _argPath = slotArgPath;
    _idfPath = idfPath;
    _desiredSlots = keySlots;
    _frameID = id;
   
    // Only load for kidnap and bombing.
//    if( id == 18 || id == 3 || id == 32 ) {
//  if( id == 18 || id == 3 ) {
//    if( id == 18 || id == 3 || id == 1 ) {
//    if( id == 3 ) {
//    if( true ) {
//    if( id < 50 ) {
//      if( distPath != null ) {
//        _pairDistanceCounts = new CountTokenPairs();
//        _pairDistanceCounts.fromFile(distPath, keySlots);
//      }
//      else System.err.println("distPath null");
//
//      if( corefPath != null ) {
//        _pairCorefCounts = new CountTokenPairs();
//        _pairCorefCounts.intsFromFile(corefPath, keySlots, true);
//      } else System.err.println("corefPath null");
//    }
    
//    if( slotArgPath != null ) _slotArgCounts = new VerbArgCounts(slotArgPath, 1);
//    else System.err.println("slotArgPath null");
    
//    if( idfPath != null ) {
//      _idf = new IDFMap(idfPath);
//      if( _slotArgCounts != null )
//        addVerbObjectsToIDF(_idf, _slotArgCounts);
//    } else System.err.println("idf null");
  }

  /**
   * The following functions dynamically read data from the disk as it is
   * required per frame.  This hopefully reduces what is loaded so we ignore
   * frames that aren't used in the code.
   */
  public CountTokenPairs pairDistanceCounts() {
    if( _pairDistanceCounts == null && _distPath != null ) {
      System.out.println("Loading distance pairs (frame " + _frameID + ")...");
      _pairDistanceCounts = new CountTokenPairs();
      _pairDistanceCounts.fromFile(_distPath, _desiredSlots);
      Util.reportMemory();
    }
    else if( _distPath == null )
      System.out.println("pairDistanceCounts null distPath");      
    return _pairDistanceCounts;
  }
  public CountTokenPairs pairCorefCounts() {
    if( _pairCorefCounts == null && _corefPath != null ) {
      System.out.println("Loading coref pairs (frame " + _frameID + ")...");
      _pairCorefCounts = new CountTokenPairs();
      _pairCorefCounts.intsFromFile(_corefPath, _desiredSlots, true);
      Util.reportMemory();
    }
    else if( _corefPath == null )
      System.out.println("pairCorefCounts null corefPath");      
    return _pairCorefCounts;
  }
  public VerbArgCounts slotArgCounts() {
    if( _slotArgCounts == null && _argPath != null ) {
      System.out.println("Loading arg counts (frame " + _frameID + ")...");
      _slotArgCounts = new VerbArgCounts(_argPath, 1);
    }
    return _slotArgCounts;
  }
  public IDFMap idf() {
    if( _idf == null && _idfPath != null ) {
      _idf = new IDFMap(_idfPath);
      // We need to add the #o# objects to the tokens, so load the argument counts.
      VerbArgCounts argCounts = slotArgCounts();
      if( argCounts != null )
        addVerbObjectsToIDF(_idf, argCounts);
    }
    return _idf; 
  }
  public int frameID() { return _frameID; }
  
  
  public void clearPairDistanceCounts() {
    if( _pairDistanceCounts != null ) {
      _pairDistanceCounts.clear();
      _pairDistanceCounts = null;
    }
  }
  
  /**
   * Add the counts in the given files to our global counts.
   */
  public void addCounts(VerbArgCounts mucSlotArgCounts, CountTokenPairs mucDistPairs, CountTokenPairs mucCorefPairs) {
    if( _slotArgCounts != null ) _slotArgCounts.addCounts(mucSlotArgCounts);
    if( _pairDistanceCounts != null ) _pairDistanceCounts.addCountsFloat(mucDistPairs);
    if( _pairCorefCounts != null ) _pairCorefCounts.addCountsInt(mucCorefPairs);
  }

  /**
   * We cluster not just single tokens, but also verb-object collocations.  In order to do this,
   * we need document and frequency counts for the verb-objects.  The IDF map only has single
   * tokens, so we add the verb-objects to this data structure.
   */
  public static void addVerbObjectsToIDF(IDFMap idf, VerbArgCounts slotCounts) {
    for( String slot : slotCounts.keySet() ) {
      if( slot.endsWith(":o") ) {
        for( Map.Entry<String,Integer> entry : slotCounts.getArgsForSlot(slot).entrySet() ) {
          if( entry.getValue() > 10 ) {
            String newname = CountArgumentTypes.connectObject(slot.substring(0, slot.indexOf(':')), entry.getKey());
            idf.setFrequency(newname, entry.getValue());
            int docCount = (int)((float)entry.getValue()*0.9f);
            idf.setDocCount(newname, docCount);
            float idfscore = (float)Math.log((float)idf.numDocs() / (float)docCount);
            idf.setIDFScore(newname, idfscore);
          }
        }
      }
    }
  }
  
  /**
   * Take a directory that has a subdirectory for each frame: the directory is an integer,
   * the frame ID.
   * @param mainDirPath The base directory containing all frame count directories: "muc-counts/irframes"
   * @param keySlots The target slots (v:kidnap:s) that we load from disk, ignoring others.
   * @return
   */
  public static Map<Integer,IRFrameCounts> loadFrameCountsFromDirectory(String mainDirPath, Collection<String> keySlots,
      VerbArgCounts mucSlotArgCounts, CountTokenPairs mucDistPairs, CountTokenPairs mucCorefPairs) {
    System.out.println("Loading counts from directory " + mainDirPath);
    Map<Integer,IRFrameCounts> frameIDToCounts = new HashMap<Integer,IRFrameCounts>();
    File dir = new File(mainDirPath);
    String[] files = dir.list();
    Arrays.sort(files);
    
    for( String file : files ) {
      if( file.matches("^\\d+$") ) {
        System.out.println("Loading counts for frame " + file);
        
        // Find the paths for all of the counts files in this directory.
        String subDirPath = mainDirPath + File.separatorChar + file; 
        String corefPath = Directory.nearestFile("token-pairs-coref.counts", subDirPath);
        if( corefPath != null ) corefPath = subDirPath + File.separatorChar + corefPath;
        String distancePath = Directory.nearestFile("token-pairs-base-govdep-distlog4.counts", subDirPath);
        if( distancePath != null ) distancePath = subDirPath + File.separatorChar + distancePath;
        String slotArgPath = Directory.nearestFile("argcounts-verbs.arg", subDirPath);
        if( slotArgPath != null ) slotArgPath = subDirPath + File.separatorChar + slotArgPath;
        String idfPath = Directory.nearestFile(".idf", subDirPath);
        if( idfPath != null ) idfPath = subDirPath + File.separatorChar + idfPath;

        // Create the counts Object and load the files into memory.
        System.out.println("  corefPath " + corefPath);
        System.out.println("  distPath " + distancePath);
        System.out.println("  slotArgPath " + slotArgPath);
        System.out.println("  idfPath " + idfPath);
        Integer frameID = Integer.valueOf(file);
        IRFrameCounts counts = new IRFrameCounts(frameID, distancePath, corefPath, slotArgPath, idfPath, keySlots);
        counts.addCounts(mucSlotArgCounts, mucDistPairs, mucCorefPairs);
        frameIDToCounts.put(frameID, counts);
        
        Util.reportMemory();
        
//        if( file.equals("3") ) break;
      }
    }
    
    System.out.println("Loaded IR counts for " + frameIDToCounts.size() + " frames.");
    return frameIDToCounts;
  }
}
