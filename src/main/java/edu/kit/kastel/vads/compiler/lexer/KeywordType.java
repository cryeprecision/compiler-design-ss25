package edu.kit.kastel.vads.compiler.lexer;

import java.util.HashMap;
import java.util.stream.Stream;

public enum KeywordType {
  STRUCT("struct"),
  IF("if"),
  ELSE("else"),
  WHILE("while"),
  FOR("for"),
  CONTINUE("continue"),
  BREAK("break"),
  RETURN("return"),
  ASSERT("assert"),
  TRUE("true"),
  FALSE("false"),
  NULL("NULL"),
  PRINT("print"),
  READ("read"),
  ALLOC("alloc"),
  ALLOC_ARRAY("alloc_array"),
  INT("int"),
  BOOL("bool"),
  VOID("void"),
  CHAR("char"),
  STRING("string"),
  ;

  private final String keyword;

  private final static HashMap<String, KeywordType> keywords = Stream.of(KeywordType.values())
      .collect(HashMap::new, (map, kw) -> map.put(kw.keyword, kw), HashMap::putAll);

  KeywordType(String keyword) {
    this.keyword = keyword;
  }

  public String keyword() {
    return keyword;
  }

  @Override
  public String toString() {
    return keyword();
  }

  public static HashMap<String, KeywordType> keywords() {
    return keywords;
  }
}
