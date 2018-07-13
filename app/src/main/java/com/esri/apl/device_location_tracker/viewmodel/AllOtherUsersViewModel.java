package com.esri.apl.device_location_tracker.viewmodel;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.graphics.Color;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.esri.apl.device_location_tracker.R;
import com.esri.apl.device_location_tracker.util.ColorUtils;
import com.esri.apl.device_location_tracker.util.MessageUtils;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.PointBuilder;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.symbology.SceneSymbol;
import com.esri.arcgisruntime.symbology.SimpleMarkerSceneSymbol;
import com.esri.arcgisruntime.symbology.TextSymbol;

public class AllOtherUsersViewModel extends AndroidViewModel {
  private final static String TAG = "UserViewModel";
  private final static int SYMBOL_HEIGHT = 150;
  private final static int SYMBOL_WIDTH = 125;

  public GraphicsOverlay getGraphicsOverlay() {
    return _graphics;
  }

  /** Graphics overlay for displaying participating users */
  private GraphicsOverlay _graphics = new GraphicsOverlay();

  public AllOtherUsersViewModel(@NonNull Application application) { super(application); }

  /** Create a hidden user graphic for the given userId and location */

  private Graphic newHiddenUserGraphic(@NonNull String userId,
                                       @Nullable @ColorInt Integer color,
                                       @Nullable Point ptUserLoc) {
    Graphic gUser = new Graphic(); gUser.setVisible(false);

    String attrId = getApplication().getString(R.string.attr_userid);
    gUser.getAttributes().put(attrId, userId);

    if (color != null) {
      SimpleMarkerSceneSymbol sym = createMarkerSymbol(color);
      gUser.setSymbol(sym);
    }
    if (ptUserLoc != null) {
      gUser.setGeometry(ptUserLoc);
    }

    return gUser;
  }

  /** Create a user label graphic, if needed, and add it to the graphics layer.
   * Assumes that the supplied user graphic has a location.
   * @param gUser Graphic denoting the user's position, heading, attitude, etc.
   */
  private void showUserLabelGraphic(Graphic gUser) {
    String attrId = getApplication().getString(R.string.attr_userid);

    String sUserId = gUser.getAttributes().get(attrId).toString();
    Graphic gLabel = findUserLabelGraphic(sUserId);

    if (gLabel == null) gLabel = new Graphic();
    else _graphics.getGraphics().remove(gLabel);

    int color = ((SimpleMarkerSceneSymbol)gUser.getSymbol()).getColor();
    TextSymbol symText = createTextSymbol(sUserId, color);
    gLabel.setSymbol(symText);

    gLabel.getAttributes().put(attrId, sUserId);

    Point ptUser = (Point) gUser.getGeometry();
    PointBuilder pbLbl = new PointBuilder(ptUser);
    pbLbl.setZ(ptUser.getZ() + (SYMBOL_HEIGHT));
    gLabel.setGeometry(pbLbl.toGeometry());

    _graphics.getGraphics().add(gLabel);
  }

  private void hideUserLabelGraphic(Graphic gUser) {
    String attrId = getApplication().getString(R.string.attr_userid);
    String sUserId = gUser.getAttributes().get(attrId).toString();

    Graphic gLabel = findUserLabelGraphic(sUserId);
    if (gLabel != null) _graphics.getGraphics().remove(gLabel);
  }
  public void updateUserGraphicColor(@NonNull String userId, @ColorInt int color) {
    Graphic gUser = findUserLocationGraphic(userId);
    if (gUser == null) { // User graphic doesn't exist yet
      Log.d(TAG, "User " + userId + " doesn't exist; creating graphic");
      // Create a hidden graphic
      gUser = newHiddenUserGraphic(userId, null, null);
      _graphics.getGraphics().add(gUser);
    }

    // Update the color
    if (gUser.getSymbol() == null) gUser.setSymbol(createMarkerSymbol(color));
    else ((SimpleMarkerSceneSymbol)gUser.getSymbol()).setColor(color);

    if (gUser.getGeometry() != null) {
      // User exists with geometry and color, so show the graphic
      gUser.setVisible(true);
      showUserLabelGraphic(gUser);
    }
  }

  /** Update the location of the user marker and label. Supplied coords are for the center of the marker.
   *
   * @param userId User's ID
   * @param x Longitude
   * @param y Latitude
   * @param z Altitude
   * @param heading Heading
   * @param pitch Pitch
   * @param roll Roll
   */
  public void updateUserGraphicLocation(@NonNull String userId, double x, double y, double z, double heading,
                                        double pitch, double roll) {
    Graphic gUser = findUserLocationGraphic(userId);
    if (gUser == null) {
      Log.d(TAG, "User " + userId + " doesn't exist; creating graphic");
      // Create a hidden graphic
      gUser = newHiddenUserGraphic(userId, null, null);
      _graphics.getGraphics().add(gUser);
    }

    // Update the location
    Point ptMkr = new Point(x, y, z, SpatialReferences.getWgs84());
    gUser.setGeometry(ptMkr);

    SimpleMarkerSceneSymbol sym = (SimpleMarkerSceneSymbol) gUser.getSymbol();

    if (gUser.getSymbol() == null) {
      // Create default symbol with gray color, until color gets updated
      sym = createMarkerSymbol(Color.GRAY);
    }

    // User exists with symbol and geometry, so show the graphic
    // But first, update heading/pitch/roll
    sym.setHeading(heading);
    sym.setPitch(pitch);
    sym.setRoll(roll);
    gUser.setSymbol(sym);

    gUser.setVisible(true);
    showUserLabelGraphic(gUser);
    // Seeming bug prevents text label graphic from showing up when handled this way.
/*    if (gLbl == null) throw new UserException("User label " + userId + " does not exist.");
    Point ptLbl = new Point(x, y, z + (SYMBOL_HEIGHT));
    gLbl.setGeometry(ptLbl);
    gLbl.setVisible(true);*/

/*    Graphic gLbl = findUserLabelGraphic(userId);
    if (gLbl != null) _graphics.getGraphics().remove(gLbl);
    gLbl = createUserLabelGraphic(gUser, sym.getColor(), x, y, z);
    _graphics.getGraphics().add(gLbl);*/
  }

  public void hideUserGraphic(String userId) {
    Graphic gUser = findUserLocationGraphic(userId);
    if (gUser == null) {
      Log.d(TAG, "User " + userId + " does not exist.");
      return;
    }

    hideUserLabelGraphic(gUser);
    _graphics.getGraphics().remove(gUser);

    MessageUtils.showToast(getApplication(), userId + " has left.", Toast.LENGTH_LONG);
  }

  /**
   *
   * @param userId The name or ID of the user we're looking for
   * @return Scene Marker Graphic in {@link #_graphics} graphics overlay of the found user (null if not found)
   */
  private Graphic findUserLocationGraphic(@NonNull String userId) {
    Graphic gRet = null;

    for (int iUser = 0; iUser < _graphics.getGraphics().size(); iUser++) {
      Graphic g = _graphics.getGraphics().get(iUser);
      String attrName = getApplication().getString(R.string.attr_userid);
      if (g.getSymbol() instanceof SimpleMarkerSceneSymbol
              && g.getAttributes().containsKey(attrName)
              && g.getAttributes().get(attrName).toString().toUpperCase()
              .equals(userId.toUpperCase())) {
        gRet = g;
        break;
      }
    }
    return gRet;
  }

  /**
   *
   * userId: The name or ID of the user we're looking for
   * @return Text Graphic in {@link #_graphics} graphics overlay of the found user (null if not found)
   */
  private Graphic findUserLabelGraphic(@NonNull String userId) {
    Graphic gRet = null;
    String attrName = getApplication().getString(R.string.attr_userid);
    for (int iUser = 0; iUser < _graphics.getGraphics().size(); iUser++) {
      Graphic g = _graphics.getGraphics().get(iUser);
      if (g.getSymbol() instanceof TextSymbol
              && g.getAttributes().containsKey(attrName)
              && g.getAttributes().get(attrName).toString().toUpperCase()
                  .equals(userId.toUpperCase())) {
        gRet = g;
        break;
      }
    }
    return gRet;
  }

  private SimpleMarkerSceneSymbol createMarkerSymbol(@ColorInt int color) {
    SimpleMarkerSceneSymbol sym = new SimpleMarkerSceneSymbol(
            SimpleMarkerSceneSymbol.Style.TETRAHEDRON, color,
            SYMBOL_WIDTH, SYMBOL_HEIGHT, SYMBOL_WIDTH, SceneSymbol.AnchorPosition.CENTER);
    return sym;
  }
  private TextSymbol createTextSymbol(String userid, @ColorInt int color) {
    @ColorInt int outlineColor = ColorUtils.inverseColor(color);
    TextSymbol symText = new TextSymbol(32, userid, color,
            TextSymbol.HorizontalAlignment.CENTER, TextSymbol.VerticalAlignment.MIDDLE);
    symText.setOutlineColor(outlineColor); symText.setOutlineWidth(5f);
    return symText;
  }

  /** User stopped participating in mutual tracking */
  public void stopTracking() {
    getGraphicsOverlay().getGraphics().clear();
  }
}
