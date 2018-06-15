package com.esri.apl.device_location_tracker;

import android.support.annotation.Nullable;
import android.util.Log;

import com.esri.arcgisruntime.mapping.view.DeviceMotionDataSource;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Quaternion;

public class ARCoreSource extends DeviceMotionDataSource {
  private static final String TAG = "ARCoreSource";

  private static final Quaternion ROTATION_COMPENSATION =
          new Quaternion((float)Math.sin(45 / (180 / Math.PI)), 0, 0, (float)Math.cos(45 / (180 / Math.PI)));

  private Scene mArScene;
  private Session mArSession;
  private SceneUpdateCallable mSceneUpdateCallback;

  /**
   *
   * @param arScene Android ArCore Scene.
   * @param arSession Android ArCore Session.
   * @param camStart Initial geographic position of the viewer.
   * @param sceneUpdateCallback Callback object for frame-update messages and errors.
   */
  ARCoreSource(
          Scene arScene,
          Session arSession,
          @Nullable com.esri.arcgisruntime.mapping.view.Camera camStart,
          @Nullable SceneUpdateCallable sceneUpdateCallback) {
    this.mArScene = arScene;
    this.mArSession = arSession;
    if (camStart != null) moveInitialPosition(
        camStart.getLocation().getX(),
        camStart.getLocation().getY(),
        camStart.getLocation().getZ());
//    calcOrientationCorrection();

    this.mSceneUpdateCallback = sceneUpdateCallback;
  }

  @Override
  public void startAll() {
    if (mArSession != null) {
      try {
        // Compensate for user holding device upright, rather than flat on a horizontal surface.
        // Taken from Xamarin iOS ARKit motion data source code.
        mArSession.resume();
        mArScene.setOnUpdateListener(new Scene.OnUpdateListener() {
          @Override
          public void onUpdate(FrameTime frameTime) {
//            if (mArSceneView.getArFrame().getLocation().getTrackingState() != TrackingState.TRACKING) return;
            Frame frame = null;
            try {
              frame = mArSession.update();
            } catch (CameraNotAvailableException e) {
              e.printStackTrace();
            }
            if (frame.getCamera().getTrackingState() != TrackingState.TRACKING) {
              Log.d(TAG, "Not tracking frame");
              return;
            }

            Pose pose = frame.getCamera().getPose();
            float[] aryRot = pose.getRotationQuaternion();
            Quaternion qRotRaw = new Quaternion(aryRot[0], aryRot[1], aryRot[2], aryRot[3]);
            // Order of multiplier and multiplicand is very important here!
            Quaternion qRot = Quaternion.multiply(ROTATION_COMPENSATION, qRotRaw);
            setRelativePosition(pose.tx(), -pose.tz(), pose.ty(),
                    qRot.x, qRot.y, qRot.z, qRot.w, true
            );

            // Allow the caller to do something with the updated scene if it wants to
            if (mSceneUpdateCallback != null)
              mSceneUpdateCallback.onSceneUpdate(mArScene, mArSession, frame, frameTime);
          }
        });
      } catch (CameraNotAvailableException e) {
        if (mSceneUpdateCallback != null) mSceneUpdateCallback.onSceneError(e);
      }
    }
  }

  @Override
  public void stopAll() {
    if (mArSession != null) mArSession.pause();
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
