package com.esri.apl.device_location_tracker.viewmodel;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;

import com.esri.apl.device_location_tracker.R;
import com.esri.apl.device_location_tracker.exception.UserException;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.symbology.SceneSymbol;
import com.esri.arcgisruntime.symbology.SimpleMarkerSceneSymbol;

public class UserViewModel extends AndroidViewModel {
  private final static String TAG = "UserViewModel";
  private final static int SYMBOL_SIZE = 14;

  public GraphicsOverlay getGraphicsOverlay() {
    return _graphics;
  }

  /** Graphics overlay for displaying participating users */
  private GraphicsOverlay _graphics = new GraphicsOverlay();

  public UserViewModel(@NonNull Application application) {
    super(application);
  }

  public void createUser(@NonNull String userid, @ColorInt int color) throws UserException {
    if (userIndex(userid) > -1) throw new UserException("User " + userid + " already exists.");

    Graphic g = new Graphic();
    g.setSymbol(createSymbol(color));
    g.getAttributes().put(getApplication().getString(R.string.attr_userid), userid);
    g.setVisible(false);

    _graphics.getGraphics().add(g);
  }
  public void updateUserColor(@NonNull String userid, @ColorInt int color) throws UserException {
    int iUserIndex = userIndex(userid);
    if (iUserIndex <= -1) throw new UserException("User " + userid + " does not exist.");

    // todo verify that this will change color on screen
    SimpleMarkerSceneSymbol sym =
            (SimpleMarkerSceneSymbol) _graphics.getGraphics().get(iUserIndex).getSymbol();
    sym.setColor(color);
  }
  public void updateUserLocation(@NonNull String userid, double x, double y, double z, double heading,
                                 double pitch, double roll) throws UserException {
    int iUserIndex = userIndex(userid);
    if (iUserIndex <= -1) throw new UserException("User " + userid + " does not exist.");

    Graphic g = _graphics.getGraphics().get(iUserIndex);
    Point pt = new Point(x, y, z);
    g.setGeometry(pt);
    SimpleMarkerSceneSymbol sym = (SimpleMarkerSceneSymbol) g.getSymbol();
    sym.setHeading(heading); sym.setPitch(pitch); sym.setRoll(roll);

    g.setVisible(true);
  }

  /**
   *
   * @param userid The name or ID of the user we're looking for
   * @return Index in {@link #_graphics} graphics overlay of the found user (-1 if not found)
   */
  private int userIndex(@NonNull String userid) {
    int iUserIdx = -1;
    for (int iUser = 0; iUser < _graphics.getGraphics().size(); iUser++) {
      if (_graphics.getGraphics().get(iUser).getAttributes().containsKey(userid)) {
        iUserIdx = iUser;
        break;
      }
    }
    return iUserIdx;
  }

  private SimpleMarkerSceneSymbol createSymbol(@ColorInt int color) {
    SimpleMarkerSceneSymbol sym = new SimpleMarkerSceneSymbol(
            SimpleMarkerSceneSymbol.Style.CONE, color,
            SYMBOL_SIZE, SYMBOL_SIZE, SYMBOL_SIZE, SceneSymbol.AnchorPosition.CENTER);
    return sym;
  }
}
