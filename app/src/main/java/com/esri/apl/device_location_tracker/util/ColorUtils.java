package com.esri.apl.device_location_tracker.util;

import android.graphics.Color;

public class ColorUtils {
  public static String intToString(int color) {
    return String.format("#%06X", 0xFFFFFF & color);
  }
  public static int stringToInt(String color) {
    return Color.parseColor(color);
  }
  public static int inverseColor(int color) {
    return Color.rgb(255-Color.red(color), 255-Color.green(color), 255-Color.blue(color));
  }

}
