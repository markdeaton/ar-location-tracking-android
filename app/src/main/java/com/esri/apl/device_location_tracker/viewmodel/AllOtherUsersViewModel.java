package com.esri.apl.device_location_tracker.viewmodel;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.esri.apl.device_location_tracker.R;
import com.esri.apl.device_location_tracker.exception.UserException;
import com.esri.apl.device_location_tracker.util.ColorUtils;
import com.esri.apl.device_location_tracker.util.MessageUtils;
import com.esri.arcgisruntime.geometry.Point;
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

  /** Create a user graphic and label for the given userId */
  public void createUserGraphics(@NonNull String userid, @ColorInt int color) throws UserException {
    Graphic gMkr = findUserLocationGraphic(userid);
    String attrId = getApplication().getString(R.string.attr_userid);
    if (gMkr != null) throw new UserException("User " + userid + " already exists.");
    gMkr = new Graphic();
    gMkr.setSymbol(createMarkerSymbol(color));
    gMkr.getAttributes().put(attrId, userid);
    gMkr.setVisible(false);

    _graphics.getGraphics().add(gMkr);

    // Note: a seeming bug prevents text symbols from showing up when done here.
    // Delete and re-add them in location update. (Color update seems to work fine.)
/*    Graphic gText = findUserLabelGraphic(userid);
    if (gText != null) throw new UserException("User label " + userid + " already exists.");
    gText = new Graphic();
    TextSymbol symText = createTextSymbol(userid, color);
    gText.setSymbol(symText);
    gText.getAttributes().put(attrId, userid);
    gText.setVisible(true);

    _graphics.getGraphics().add(gText);*/
  }

  private Graphic createUserLabelGraphic(String userid, @ColorInt int color,
                                         double x, double y, double z) {
    Graphic gText = new Graphic();
    String attrId = getApplication().getString(R.string.attr_userid);
    TextSymbol symText = createTextSymbol(userid, color);
    gText.setSymbol(symText);
    gText.getAttributes().put(attrId, userid);
    Point ptLbl = new Point(x, y, z + (SYMBOL_HEIGHT));
    gText.setGeometry(ptLbl);

    return gText;
  }

  public void updateUserGraphicsColor(@NonNull String userid, @ColorInt int color) throws UserException {
    Graphic gMkr = findUserLocationGraphic(userid); Graphic gLbl = findUserLabelGraphic(userid);
    if (gMkr == null || gLbl == null) {
      Log.d(TAG, "User " + userid + " doesn't exist; creating graphic");
      createUserGraphics(userid, color);
    } else {
      // todo verify that this will change color on screen
      SimpleMarkerSceneSymbol symMkr = (SimpleMarkerSceneSymbol)gMkr.getSymbol();
      symMkr.setColor(color);
      TextSymbol symLbl = (TextSymbol) gLbl.getSymbol();
      symLbl.setColor(color);
      symLbl.setOutlineColor(ColorUtils.inverseColor(color));
    }
  }

  /** Update the location of the user marker and label. Supplied coords are for the center of the marker.
   *
   * @param userid User's ID
   * @param x Longitude
   * @param y Latitude
   * @param z Altitude
   * @param heading Heading
   * @param pitch Pitch
   * @param roll Roll
   * @throws UserException
   */
  public void updateUserGraphicsLocation(@NonNull String userid, double x, double y, double z, double heading,
                                         double pitch, double roll) throws UserException {
    Graphic gMkr = findUserLocationGraphic(userid);
    if (gMkr == null) throw new UserException("User " + userid + " does not exist.");
    Point ptMkr = new Point(x, y, z);
    gMkr.setGeometry(ptMkr);
    SimpleMarkerSceneSymbol sym = (SimpleMarkerSceneSymbol) gMkr.getSymbol();
    sym.setHeading(heading); sym.setPitch(pitch); sym.setRoll(roll);
    gMkr.setVisible(true);

    Graphic gLbl = findUserLabelGraphic(userid);
    // Seeming bug prevents text label graphic from showing up when handled this way.
/*    if (gLbl == null) throw new UserException("User label " + userid + " does not exist.");
    Point ptLbl = new Point(x, y, z + (SYMBOL_HEIGHT));
    gLbl.setGeometry(ptLbl);
    gLbl.setVisible(true);*/
    if (gLbl != null) _graphics.getGraphics().remove(gLbl);
    gLbl = createUserLabelGraphic(userid, sym.getColor(), x, y, z);
    _graphics.getGraphics().add(gLbl);
  }

  public void removeUserGraphics(String userid) throws UserException {
    Graphic gMkr = findUserLocationGraphic(userid);
    if (gMkr == null) throw new UserException("User " + userid + " does not exist.");

    _graphics.getGraphics().remove(gMkr);

    Graphic gLbl = findUserLabelGraphic(userid);
    if (gLbl == null) throw new UserException("User label " + userid + " does not exist.");

    _graphics.getGraphics().remove(gLbl);

    MessageUtils.showToast(getApplication(), userid + " has left.", Toast.LENGTH_LONG);
  }

  /**
   *
   * @param userid The name or ID of the user we're looking for
   * @return Scene Marker Graphic in {@link #_graphics} graphics overlay of the found user (null if not found)
   */
  private Graphic findUserLocationGraphic(@NonNull String userid) {
    Graphic gRet = null;

    for (int iUser = 0; iUser < _graphics.getGraphics().size(); iUser++) {
      Graphic g = _graphics.getGraphics().get(iUser);
      String attrName = getApplication().getString(R.string.attr_userid);
      if (g.getSymbol() instanceof SimpleMarkerSceneSymbol
              && g.getAttributes().containsKey(attrName)
              && g.getAttributes().get(attrName).toString().toUpperCase()
              .equals(userid.toUpperCase())) {
        gRet = g;
        break;
      }
    }
    return gRet;
  }

  /**
   *
   * @param userid The name or ID of the user we're looking for
   * @return Text Graphic in {@link #_graphics} graphics overlay of the found user (null if not found)
   */
  private Graphic findUserLabelGraphic(@NonNull String userid) {
    Graphic gRet = null;
    String attrName = getApplication().getString(R.string.attr_userid);
    for (int iUser = 0; iUser < _graphics.getGraphics().size(); iUser++) {
      Graphic g = _graphics.getGraphics().get(iUser);
      if (g.getSymbol() instanceof TextSymbol
              && g.getAttributes().containsKey(attrName)
              && g.getAttributes().get(attrName).toString().toUpperCase()
                  .equals(userid.toUpperCase())) {
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
