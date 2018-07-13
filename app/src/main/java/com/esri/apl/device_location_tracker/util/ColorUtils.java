package com.esri.apl.device_location_tracker.util;

import android.graphics.Color;
import android.support.annotation.ColorInt;
import android.util.Log;

public class ColorUtils {
  private static final String TAG = "ColorUtils";

  public static String intToString(int color) {
    return String.format("#%06X", 0xFFFFFF & color);
  }

  public static int stringToInt(String color) {
    // Default if received color is invalid
    @ColorInt int iColor = Color.GRAY;
    try {
      iColor = Color.parseColor(color);
    } catch (IllegalArgumentException e) {
      Log.w(TAG, "Invalid color received via notification: " + color);
    }
    return iColor;
  }
  public static int inverseColor(int color) {
    return Color.rgb(255-Color.red(color), 255-Color.green(color), 255-Color.blue(color));
  }

}
