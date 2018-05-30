package com.esri.apl.device_location_tracker;

import android.support.annotation.Nullable;

import com.esri.arcgisruntime.mapping.view.DeviceMotionDataSource;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;

import utils.ARUtils;

public class ARCoreSource extends DeviceMotionDataSource {
  private static final String TAG = "ARCoreSource";

  private ArSceneView mArSceneView;
//  private Quaternion mOrientationCorrection;

  /**
   *
   * @param arSceneView Android ArCore ArSceneView widget already inflated.
   * @param camStart Initial geographic position of the viewer
   */
  ARCoreSource(ArSceneView arSceneView, @Nullable com.esri.arcgisruntime.mapping.view.Camera camStart) {
    this.mArSceneView = arSceneView;
    if (camStart != null) moveInitialPosition(
        camStart.getLocation().getX(),
        camStart.getLocation().getY(),
        camStart.getLocation().getZ());
//    calcOrientationCorrection();
  }


  @Override
  public void startAll() {
    if (mArSceneView.getSession() != null) {
      try {
        // Compensate for user holding device upright, rather than flat on a horizontal surface.
        // Taken from Xamarin iOS ARKit motion data source code.
        mArSceneView.getSession().resume();
        mArSceneView.getScene().setOnUpdateListener(mOnUpdate);
      } catch (CameraNotAvailableException e) {
        ARUtils.displayError(mArSceneView.getContext(), "Could not acquire camera", e);
      }
    }
  }

  private static final Quaternion ROTATION_ADJUSTMENT =
          new Quaternion(
                  (float)Math.sin(45 / (180 / Math.PI)),
                  0, 0,
                  (float)Math.cos(45 / (180 / Math.PI)));

  /** Adjustments before displaying the ArcGIS Scene */
  private Scene.OnUpdateListener mOnUpdate = new Scene.OnUpdateListener() {
    @Override
    public void onUpdate(FrameTime frameTime) {
      if (mArSceneView.getArFrame().getCamera().getTrackingState() != TrackingState.TRACKING) return;

      Vector3 v3Pos = mArSceneView.getScene().getCamera().getWorldPosition();

      Quaternion qRotRaw = mArSceneView.getScene().getCamera().getWorldRotation();
      Quaternion qRot = Quaternion.multiply(ROTATION_ADJUSTMENT, qRotRaw);
      setRelativePosition(v3Pos.x, v3Pos.y, v3Pos.z,
              qRot.x, qRot.y, qRot.z, qRot.w, true
      );
    }
  };

  @Override
  public void stopAll() {
    if (mArSceneView.getSession() != null) mArSceneView.getSession().pause();
  }

  // Get a correction factor for device based on current screen orientation
 /* private void calcOrientationCorrection() {
    Display display = ((WindowManager) mArSceneView.getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
    int rotation = display.getRotation();
    switch (rotation) {
      case Surface.ROTATION_0:
        mOrientationCorrection = new Quaternion(0, 0, (float)Math.sqrt(0.5), (float)Math.sqrt(0.5));
        break;
      case Surface.ROTATION_90:
        mOrientationCorrection = new Quaternion(0, 0, 0, 1);
        break;
      case Surface.ROTATION_180:
        mOrientationCorrection = new Quaternion(0, 0, -(float)Math.sqrt(0.5), (float)Math.sqrt(0.5));
        break;
      case Surface.ROTATION_270:
        mOrientationCorrection = new Quaternion(0, 0, 1, 0);
        break;
    }
  }*/
}
