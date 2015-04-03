package nate.order;


/**
 * Types of features for tlinks
 */
public enum FeatureType {
  BEFORE,
    POS1_0,
    POS1_1,
    POS1_2, // POS_2 is the event root
    POS1_3,
    TENSE1,
    ASPECT1,
    MODAL1,
    POLARITY1,
    CLASS1,
    POS2_0,
    POS2_1,
    POS2_2,
    POS2_3,
    TENSE2,
    ASPECT2,
    MODAL2,
    POLARITY2,
    CLASS2,
    DOMINANCE,
    ENTITY_MATCH,
    PREP1,
    PREP2,
    SAME_SENTENCE,
    WORD1,
    WORD2,
    LEMMA1,
    LEMMA2,
    SYNSET1,
    SYNSET2,
    TENSE_PAIR,
    ASPECT_PAIR,
    CLASS_PAIR,
    POS_BIGRAM1,
    POS_BIGRAM2,
    POS_BIGRAM,
    TENSE_MATCH,
    ASPECT_MATCH,
    CLASS_MATCH;

  /*
  BEFORE(0, "BEFORE"),
    POS1_0(1, "POS1_0"),
    POS1_1(2, "POS1_1"),
    POS1_2(3, "POS1_2"),
    POS1_3(4, "POS1_3"),
    TENSE1(5, ""),
    ASPECT1(6, ""),
    MODAL1(7, ""),
    POLARITY1(8, ""),
    CLASS1(9, ""),
    POS2_0(10, ""),
    POS2_1(11, ""),
    POS2_2(12, ""),
    POS2_3(13, ""),
    TENSE2(14, ""),
    ASPECT2(15, ""),
    MODAL2(16, ""),
    POLARITY2(17, ""),
    CLASS2(18, ""),
    DOMINANCE(19, ""),
    ENTITY_MATCH(20, ""),
    PREP1(21, ""),
    PREP2(22, ""),
    SAME_SENTENCE(23, ""),
    WORD1(24, ""),
    WORD2(25, ""),
    LEMMA1(26, ""),
    LEMMA2(27, ""),
    SYNSET1(28, ""),
    SYNSET2(29, ""),
    TENSE_PAIR(30, ""),
    ASPECT_PAIR(31, ""),
    CLASS_PAIR(32, ""),
    POS_BIGRAM1(33, ""),
    POS_BIGRAM2(34, ""),
    POS_BIGRAM(35, ""),
    TENSE_MATCH(36, ""),
    ASPECT_MATCH(37, ""),
    CLASS_MATCH(38, "");
  */

  /*
  private final int id;
  private final String label;

  FeatureType(final int id, final String label) {
    this.id = id;
    this.label = label;
  }

  public int id() { return id; }
  public String toString() { return label; }
  */
}
