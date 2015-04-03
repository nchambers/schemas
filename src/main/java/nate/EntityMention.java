/**
 * Nate Chambers
 * Stanford University
 * October 2009
 */

package nate;

import java.util.Collection;

import opennlp.tools.util.Span;

/**
 * The entity IDs are indexed from 0
 * The sentence IDs are indexed from 1, not 0
 * The char spans are indexed from 0, and the end point is exclusive.
 * The word spans are indexed from 1, and the end point is inclusive.
 */
public class EntityMention {
  int entityID, sentenceID, start, end;
  String string = "";
  boolean wordBased = false;
  NERSpan.TYPE namedEntity = NERSpan.TYPE.NONE;

  
  public EntityMention(int sid, String text, Span span, int eid) {
    this(sid,text,span.getStart(),span.getEnd(),eid);
    wordBased = false;
  }

  public EntityMention(int sid, String text, int start, int end, int eid) {
    entityID = eid;
    sentenceID = sid;
    this.start = start;
    this.end = end;
    string = text;
    wordBased = true;
  }

  /**
   * @return The highest ID of all given mentions in the list.
   */
  public static int maxID(Collection<EntityMention> mentions) {
    int max = -1;
    for( EntityMention mention : mentions ) {
      if( mention.entityID() > max )
        max = mention.entityID();
    }
    return max;
  }
  
  /**
   * @param sent Plain text of a sentence
   * @desc Converts the character span of this mention into a word
   *       indexed span, based on the given sentence.
   */
  public void convertCharSpanToIndex(String sent) {
    //    System.out.println(sent);
    if( !wordBased ) {
      int s,e,wordCount = 1;

      for( int i = 0; i < start; i++ )
        if( sent.charAt(i) == ' ' ) wordCount++;
      s = wordCount;

      for( int i = start; i < end; i++ )
        if( sent.charAt(i) == ' ' ) wordCount++;
      e = wordCount;

      start = s;
      end = e;
      wordBased = true;
    }
  }

  public void setSpan(int s, int e) {
    start = s;
    end = e;
  }

  public void setSpan(Span span) {
    start = span.getStart();
    end = span.getEnd();
  }

  public void setEntityID(int id) {
    entityID = id;
  }
  
  public void setNamedEntity(NERSpan.TYPE val) {
    namedEntity = val;
  }
  

  public boolean isWordSpan() { return wordBased; }
  public int entityID() { return entityID; }
  public int sentenceID() { return sentenceID; }
  public int sid() { return sentenceID; }
  public int start() { return start; }
  public int end() { return end; }
  public String string() { return string; }
  public NERSpan.TYPE namedEntity() { return namedEntity; }

  //  <ENT>32 8 17 18 the defense</ENT>
  public static EntityMention fromString(String str) {
    int space = str.indexOf(' ');
    int space2 = str.indexOf(' ',space+1);
    int space3 = str.indexOf(' ',space2+1);
    int space4 = str.indexOf(' ',space3+1);

    int sid = Integer.parseInt(str.substring(0,space));
    int eid = Integer.parseInt(str.substring(space+1,space2));
    int start = Integer.parseInt(str.substring(space2+1,space3));

    int end;
    String word;
    if( space4 > -1 ) {
      end = Integer.parseInt(str.substring(space3+1,space4));
      word = str.substring(space4+1,str.length());
    }
    else {
      end = Integer.parseInt(str.substring(space3+1,str.length()));
      word = "unknown";
    }

    return new EntityMention(sid,word,start,end,eid);
  }

  public String toString() {
    //    return entityID + " " + sentenceID + " " + string + " [" + start + ".." + end + "]";
    return sentenceID + " " + entityID + " " + start + " " + end + " " + string;
  }
}