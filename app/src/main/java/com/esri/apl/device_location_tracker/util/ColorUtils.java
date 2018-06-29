package com.esri.apl.device_location_tracker.util;

import android.graphics.Color;

public class ColorUtils {
  public static String intToString(int color) {
    return String.format("#%06X", 0xFFFFFF & color);
  }
  public static int stringToInt(String color) {
    return Color.parseColor(color);
  }
}
