package HDBViewer;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import org.tango.jhdb.HdbSigInfo;
import org.tango.jhdb.SignalInfo;
import org.tango.jhdb.data.HdbData;

/** A class for loading selection file */

public class ConfigFileReader {
  
  // Global param
  String scriptName = "";
  boolean showError = false;
  int timeInterval = 0; // Last hour
  int hdbMode=0; // Normal mode
  String chartSettings = null;
  String xSettings = null;
  String y1Settings = null;
  String y2Settings = null;

  /* Lexical coce */

  private static final int NUMBER = 1;
  private static final int STRING = 2;
  private static final int COMA = 3;
  private static final int COLON = 4;
  private static final int OPENBRACE = 5;
  private static final int CLOSEBRACE = 6;

  private final String[] lexical_word = {
    "NULL",
    "NUMBER",
    "STRING",
    "COMA",
    "COLON",
    "'{'",
    "'}'"
  };

  private static final String COMA_STR = ",";
  private static final String COLON_STR = ":";
  private static final String OPENBRACE_STR = "{";
  private static final String CLOSEBRACE_STR = "}";

  private static final int MAX_BUFFER_SIZE = 65536;   // 64Ko reading buffer
  private static final int MAX_STRING_LENGTH = 4095;  // Maximum string length

  private int CrtLine;
  private int StartLine;
  private char CurrentChar;

  private char[] loadingBuffer = null;
  private int bufferIdx;
  private int bufferSize;

  private String word;
  private String version;
  private InputStreamReader f;

  private char[] tmpWord = new char[MAX_STRING_LENGTH+1];

  /**
   * Construct a JDFileLoader.
   * @param fr File to be read.
   * @see #parseFile
   */
  public ConfigFileReader(FileReader fr) {
    f = fr;
    CrtLine = 1;
    CurrentChar = ' ';
    loadingBuffer = new char[MAX_BUFFER_SIZE];
    bufferIdx = 0;
    bufferSize = 0;
  }
  
  /**
   * Construct a ConfigFileReader.
   * @param insr File to be read.
   * @see #parseFile
   */
  public ConfigFileReader(InputStreamReader insr) {
    f = insr;
    CrtLine = 1;
    CurrentChar = ' ';
  }

  /**
   * Construct a ConfigFileReader.
   * @param str File to be read.
   * @see #parseFile
   */
  public ConfigFileReader(String str) {
    ByteArrayInputStream bis = new ByteArrayInputStream(str.getBytes());
    f = new InputStreamReader(bis);
    CrtLine = 1;
    CurrentChar = ' ';
  }

  // ****************************************************
  // read the next character in the file
  // ****************************************************
  private void refill_buffer() throws IOException {

    if( bufferIdx>=bufferSize ) {
      bufferSize = f.read(loadingBuffer);
      bufferIdx = 0;
    }

  }


  private void read_char() throws IOException {

    if (loadingBuffer != null) {

      // Use loading buffer
      refill_buffer();

      if (bufferSize <= 0) {
        CurrentChar = 0;
      } else {
        CurrentChar = loadingBuffer[bufferIdx];
        bufferIdx++;
      }

    } else {

      // Simple reading
      if (!f.ready()) {
        CurrentChar = 0;
      } else {
        CurrentChar = (char) f.read();
      }

    }

    if (CurrentChar == '\n') CrtLine++;

  }

  // ****************************************************
  // Go to the next significant character
  // ****************************************************
  private void jump_space() throws IOException {

    while (CurrentChar <= 32 && CurrentChar > 0) read_char();
  }

  // ****************************************************
  // Read the next word in the file                           */
  // ****************************************************
  private String read_word() throws IOException {

    /* Jump space */
    jump_space();

    StartLine = CrtLine;

    /* Treat special character */
    if( CurrentChar == ',' ) {
      read_char();
      return COMA_STR;
    }

    if( CurrentChar == ':' ) {
      read_char();
      return COLON_STR;
    }

    if( CurrentChar == '{' ) {
      read_char();
      return OPENBRACE_STR;
    }

    if( CurrentChar == '}' ) {
      read_char();
      return CLOSEBRACE_STR;
    }

    int wIdx=0;

    /* Treat string */
    if (CurrentChar == '"') {
      tmpWord[wIdx++]=CurrentChar;
      read_char();
      while (CurrentChar != '"' && CurrentChar != 0 && CurrentChar != '\n' && wIdx<MAX_STRING_LENGTH) {
        tmpWord[wIdx++]=CurrentChar;
        read_char();
      }
      if (CurrentChar == 0 || CurrentChar == '\n' || wIdx>=MAX_STRING_LENGTH) {
        IOException e = new IOException("String too long at line " + StartLine);
        throw e;
      }
      tmpWord[wIdx++]=CurrentChar;
      read_char();
      return new String(tmpWord,0,wIdx);
    }

    /* Treat other word */
    while (CurrentChar > 32 && CurrentChar != ':' && CurrentChar != '{'
            && CurrentChar != '}' && CurrentChar != ',' && wIdx<MAX_STRING_LENGTH) {
      tmpWord[wIdx++]=CurrentChar;
      read_char();
    }

    if (wIdx == 0) {
      return null;
    }

    return new String(tmpWord,0,wIdx);

  }

  // ****************************************************
  // return the lexical classe of the next word        */
  // ****************************************************
  private boolean isNumber(String s) {
    boolean ok=true;
    for(int i=0;i<s.length() && ok;i++) {
      char c = s.charAt(i);
      ok = ok & ((c>='0' && c<='9') || c=='.' || c=='e' || c=='E' || c=='-');
    }
    return ok;
  }

  private int class_lex(String word) {

    /* Exception */
    if (word == null) return 0;
    if (word.length() == 0) return STRING;
    if (word.charAt(0)=='\"') return STRING;

    /* Special character */
    if (word.equals(COMA_STR)) return COMA;
    if (word.equals(COLON_STR)) return COLON;
    if (word.equals(OPENBRACE_STR)) return OPENBRACE;
    if (word.equals(CLOSEBRACE_STR)) return CLOSEBRACE;
    if (isNumber(word))   return NUMBER;

    return STRING;
  }

  // ****************************************************
  // Check lexical word
  // ****************************************************
  private void CHECK_LEX(int lt, int le) throws IOException {
    if (lt != le)
      throw new IOException("Invalid syntyax at line " + StartLine + ", " + lexical_word[le] + " expected, got " + word);
  }

  private int getCurrentLine() {
    return StartLine;
  }

  private void jumpPropertyValue() throws IOException {
    // Trigger to the next value
    int lex = class_lex(word);

    if( lex==OPENBRACE) {
      jumpBlock();
      return;
    }

    boolean ok=true;
    while(ok && word!=null) {

      if(lex!=NUMBER && lex!=STRING)
        throw new IOException("Invalid syntyax at line " + StartLine + ": Number or String expected, got" + word);

      word=read_word();
      lex = class_lex(word);
      ok = (lex==COMA);
      if(ok) {
        word=read_word();
        lex = class_lex(word);
      }
    }
  }

  private void jumpBlock() throws IOException {

    int lex = class_lex(word);
    CHECK_LEX(lex, OPENBRACE);
    int nb = 1;
    while (nb > 0 && word != null) {
      word = read_word();
      lex = class_lex(word);
      if (lex == OPENBRACE) nb++;
      if (lex == CLOSEBRACE) nb--;
    }
    if (word == null) throw new IOException("Unexpected end of file");
    word = read_word();

  }

  private void startBlock() throws IOException {
    CHECK_LEX(class_lex(word), OPENBRACE);
    word=read_word();
  }

  private void jumpLexem(int lexem) throws IOException {
    CHECK_LEX(class_lex(word), lexem);
    word=read_word();
  }

  private void endBlock() throws IOException {
    CHECK_LEX(class_lex(word), CLOSEBRACE);
    word=read_word();
  }

  private boolean isEndBlock() {
    return class_lex(word)==CLOSEBRACE;
  }

  // Value type ------------------------------------------------------------------

  private double parseDouble() throws IOException {
    CHECK_LEX(class_lex(word),NUMBER);
    double ret = 0.0;
    try {
      ret = Double.parseDouble(word);
    } catch (NumberFormatException e) {
      throw new IOException("Invalid number at line " + StartLine);
    }
    word=read_word();
    return ret;
  }

  private String parseSettingsSection() throws IOException {

    StringBuffer ret = new StringBuffer();    
    CHECK_LEX(class_lex(word),OPENBRACE);
    
    jump_space();
    
    while(CurrentChar != '}') {
      while (CurrentChar != 0 && CurrentChar != '\n') {
        ret.append(CurrentChar);
        read_char();
      }
      ret.append("\n");
      if (CurrentChar == 0) {
        IOException e = new IOException("String too long at line " + StartLine);
        throw e;
      }
      jump_space();
    }
    word=read_word();
    endBlock();
    
    return ret.toString();
    
  }
  
  private int parseInt() throws IOException {
    CHECK_LEX(class_lex(word),NUMBER);
    int ret = 0;
    try {
      ret = Integer.parseInt(word);
    } catch (NumberFormatException e) {
      throw new IOException("Invalid integer number at line " + StartLine);
    }
    word=read_word();
    return ret;
  }

  private String extractQuote(String s) {
    if(s.charAt(0)=='\"')
      return s.substring(1,s.length()-1);
    else
      return s;
  }

  private String parseString()  throws IOException {
    CHECK_LEX(class_lex(word),STRING);
    String s=extractQuote(word);
    word=read_word();
    return s;
  }

  private boolean parseBoolean() throws IOException {

    CHECK_LEX(class_lex(word),STRING);
    String value=word;
    word=read_word();
    return value.equalsIgnoreCase("true");

  }

  private HdbSigInfo.Interval parseInterval() throws IOException {

    CHECK_LEX(class_lex(word),STRING);
    String value=word;
    word=read_word();
    
    HdbSigInfo.Interval[] allValues = HdbSigInfo.Interval.values();
    boolean found = false;
    int i = 0;
    while(!found && i<allValues.length) {
      found = allValues[i].toString().equalsIgnoreCase(value);
      if(!found) i++;
    }
    if( !found ) 
          throw new IOException("Invalid interval '"+value+"' at line " + StartLine);

    return allValues[i];

  }

  private HdbData.Aggregate parseAggregate() throws IOException {

    CHECK_LEX(class_lex(word),STRING);
    String value=word;
    word=read_word();
    
    HdbData.Aggregate[] allValues = HdbData.Aggregate.values();
    boolean found = false;
    int i = 0;
    while(!found && i<allValues.length) {
      found = allValues[i].toString().equalsIgnoreCase(value);
      if(!found) i++;
    }
    if( !found ) 
          throw new IOException("Invalid aggregate "+value+" at line " + StartLine);

    return allValues[i];

  }

  private String parseProperyName() throws IOException {
    String propName=parseString();
    jumpLexem(COLON);
    return propName;
  }

  private Color parseColor()  throws IOException {

    int red = (int)parseDouble();
    jumpLexem(COMA);
    int green = (int)parseDouble();
    jumpLexem(COMA);
    int blue = (int)parseDouble();

    return new Color(red,green,blue);

  }

  private String parseStringArray() throws IOException {

    ArrayList<String> v = new ArrayList<>();
    boolean end = false;

    while (!end && word!=null) {

      String s=parseString();
      v.add(s);

      end = class_lex(word)!=COMA;
      if(!end)  word = read_word();
    }
    if (word == null) throw new IOException("Unexpected end of file");

    // Build String
    String ret = "";
    for(int i=0;i<v.size();i++) ret += v.get(i) + "\n";
    return ret;

  }

  private ArrayAttributeInfo parseExpandedSection(int idx) throws IOException {

    ArrayAttributeInfo aai = new ArrayAttributeInfo(idx);
    
    // Parse expanded section
    startBlock();
    while(!isEndBlock()) {
      String propName = parseProperyName();
      if ( propName.equals("step") ) {
        aai.step = parseBoolean();
      } else if ( propName.equals("table") ) {
        aai.table = parseBoolean();
      } else if ( propName.equals("selection") ) {
        aai.selection = parseInt();        
      } else if ( propName.equals("dv") ) {
        aai.dvSetting = parseSettingsSection();        
      } else if ( propName.equals("wselection") ) {
        aai.wselection = parseInt();
      } else if ( propName.equals("wdv") ) {
        aai.wdvSetting = parseSettingsSection();        
      } else {
        System.out.println("Warning, unknown expanded property found:" + propName);
        jumpPropertyValue();
      }
    }
    endBlock();
    
    return aai;
      
  }
  
  private AggregateAttributeInfo parseAggregateBody() throws IOException {

    AggregateAttributeInfo aai = new AggregateAttributeInfo();

    // Parse expanded section
    startBlock();
    while(!isEndBlock()) {
      String propName = parseProperyName();
      if ( propName.equals("step") ) {
        aai.step = parseBoolean();
      } else if ( propName.equals("table") ) {
        aai.table = parseBoolean();
      } else if ( propName.equals("selection") ) {
        aai.selection = parseInt();
      } else if ( propName.equals("dv") ) {
        aai.dvSettings = parseSettingsSection();
      } else {
        System.out.println("Warning, unknown aggregate property found:" + propName);
        jumpPropertyValue();
      }
    }
    endBlock();
   
    return aai;
    
  }

  private ArrayList<ArrayAttributeInfo> parseExpanded() throws IOException {
    
    ArrayList<ArrayAttributeInfo> list = new ArrayList<ArrayAttributeInfo>();
    
    startBlock();
    while(!isEndBlock()) {
      
      String idxName = parseString();
      if(!idxName.equalsIgnoreCase("idx"))
        throw new IOException("Unexcpected keyword :" + idxName + " at line " + StartLine);
      int idx = parseInt();      
      ArrayAttributeInfo aai = parseExpandedSection(idx);
      list.add(aai);
      
    }
    endBlock();    
    
    return list;
    
  }

  private AttributeInfo parseSection() throws IOException {

    String sectionName = parseString();

    if (sectionName.equals("Global")) {
      
      // Parse global section
      startBlock();
      while(!isEndBlock()) {
        String propName = parseProperyName();
        if( propName.equals("script") ) {
          scriptName = parseString();
        } else if ( propName.equals("showError") ) {
          showError = parseBoolean();
        } else if ( propName.equals("timeInterval") ) {
          timeInterval = parseInt();
        } else if ( propName.equals("hdbMode") ) {
          hdbMode = parseInt();
        } else if ( propName.equals("chart") ) {         
          chartSettings = parseSettingsSection();
        } else if ( propName.equals("xaxis") ) {         
          xSettings = parseSettingsSection();
        } else if ( propName.equals("y1axis") ) {         
          y1Settings = parseSettingsSection();
        } else if ( propName.equals("y2axis") ) {         
          y2Settings = parseSettingsSection();
        } else {
          System.out.println("Warning, unknown global property found:" + propName);
          jumpPropertyValue();
        }
      }
      endBlock();
      

    } else if (sectionName.equals("Attribute")) {
      
      // Parse attribute info
      AttributeInfo ai = new AttributeInfo();
      
      startBlock();
      while(!isEndBlock()) {
        String propName = parseProperyName();
        if( propName.equals("host") ) {
          ai.host = parseString();
        } else if ( propName.equals("name") ) {
          ai.name = parseString();
        } else if ( propName.equals("interval") ) {
          ai.interval = parseInterval();
        } else if ( propName.equals("step") ) {
          ai.step = parseBoolean();
        } else if ( propName.equals("table") ) {
          ai.table = parseBoolean();
        } else if ( propName.equals("selection") ) {
          ai.selection = parseInt();
        } else if ( propName.equals("dv") ) {          
          ai.setDataViewSettings(parseSettingsSection());
        } else if ( propName.equals("wselection") ) {
          ai.wselection = parseInt();
        } else if ( propName.equals("wdv") ) {
          ai.setWriteDataViewSettings(parseSettingsSection());
        } else if ( propName.equals("expanded") ) {
          ai.arrAttInfos = parseExpanded();
        } else if ( propName.equals("aggregate") ) {
          if(!ai.isAggregate()) {
            throw new IOException("Aggregates not allowed for RAW interval at line " + StartLine);
          }
          HdbData.Aggregate agg = parseAggregate();
          ai.addAggregate(agg, parseAggregateBody());
        } else {
          System.out.println("Warning, unknown attribute property found:" + propName);
          jumpPropertyValue();
        }
      }
      endBlock();
      
      return ai;
    
    } else {
      
      throw new IOException("Unexcpected keyword :" + sectionName + " at line " + StartLine);
      
    }
    
    return null;

  }

  private String parseParamString() throws IOException {

    ArrayList<String> v = new ArrayList<>();
    boolean end = false;
    int lex;

    while (!end && word!=null) {

      // Get the string array
      // (interpret number as string in param list)
      // (interpret kw as string in param list)
      lex = class_lex(word);

      if (lex != STRING && lex != NUMBER)
        throw new IOException("Error at line " + StartLine + ", '" + lexical_word[NUMBER] + "' or '" + lexical_word[STRING] + "' expected");

      v.add(extractQuote(word));
      word = read_word();

      end = class_lex(word)!=COMA;
      if(!end)  word = read_word();

    }
    if (word == null) throw new IOException("Unexpected end of file");

    // Build String
    String ret = "";
    for(int i=0;i<v.size();i++) {
      ret += v.get(i);
      if(i<v.size()-1) ret += "\n";
    }
    return ret;

  }

  /**
   * Parse a HDB file
   * @throws IOException In case of failure
   */
  public ArrayList<AttributeInfo> parseFile() throws IOException {

    boolean eof = false;
    int lex;
    ArrayList<AttributeInfo> objects = new ArrayList<AttributeInfo>();

    /* CHECK BEGINING OF FILE  */
    word = read_word();
    if (word == null) throw new IOException("File empty !");
    if (!word.equalsIgnoreCase("HDBfile")) throw new IOException("Invalid header !");

    jumpLexem(STRING);        // jdfile keyword
    version = parseString();  // Release number
    jumpLexem(OPENBRACE);
    lex = class_lex(word);

    /* PARSE */
    while (!eof) {
      switch (lex) {
        case STRING:
          AttributeInfo ai = parseSection();
          if (ai != null) objects.add(ai);
          break;
        case CLOSEBRACE:
          break;
        default:
          throw new IOException("Invalid syntyax at line " + StartLine + ": 'Global','Attribute' or '}' expected.");
      }
      lex = class_lex(word);
      eof = ((word == null) || (lex==CLOSEBRACE));
    }

    if(word == null)
      throw new IOException("Unexpected end of file at line " + StartLine + "." );

    // Check the last '}'
    CHECK_LEX(class_lex(word), CLOSEBRACE);

    return objects;

  }
  
}
