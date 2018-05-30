package utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextUtils {
  /** Return first instance of a matched regex capture group in the supplied text */
  public static String matchedGroupVal(Pattern ptn, String sInText) throws PayloadParseException {
    Matcher mchTypeField = ptn.matcher(sInText);
    if (!mchTypeField.find()) { // No type field in payload
      PayloadParseException exc = new PayloadParseException(
              "Could not find match [" + ptn.pattern() + "] in input text");
      throw exc;
    } else
      return mchTypeField.group();
  }
}
