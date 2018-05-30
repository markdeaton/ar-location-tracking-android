package com.esri.apl.device_location_tracker.graphicdisplay;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import java.io.IOException;

/**
 * Created by adri4054 on 2/14/18.
 */

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
  private SurfaceHolder mHolder;
  private Camera mCamera;
  private Camera.CameraInfo mCameraInfo;
  private Context mContext;

  public CameraPreview(Context context) {
    super(context);
    mContext = context;
    mCamera = getCameraInstance();

    // Install a SurfaceHolder.Callback so we get notified when the
    // underlying surface is created and destroyed.
    mHolder = getHolder();
    mHolder.addCallback(this);
    // deprecated setting, but required on Android versions prior to 3.0
    mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
  }

  public void surfaceCreated(SurfaceHolder holder) {
    // The Surface has been created, now tell the camera where to draw the preview.
    try {
      mCamera.setPreviewDisplay(holder);
      mCamera.startPreview();
    } catch (IOException e) {
      //surface can't be created
    }
  }

  public void surfaceDestroyed(SurfaceHolder holder) {
    // empty. Take care of releasing the Camera preview in your activity.
  }

  public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    // If your preview can change or rotate, take care of those events here.
    // Make sure to stop the preview before resizing or reformatting it.

    if (mHolder.getSurface() == null){
      // preview surface does not exist
      return;
    }

    // stop preview before making changes
    try {
      mCamera.stopPreview();
    } catch (Exception e){
      // ignore: tried to stop a non-existent preview
    }

//    Camera.Parameters parameters = mCamera.getParameters();
    Display display = ((WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
    int rotation = display.getRotation();
    int degrees = 0;
    switch (rotation) {
      case Surface.ROTATION_0: degrees = 0; break;
      case Surface.ROTATION_90: degrees = 90; break;
      case Surface.ROTATION_180: degrees = 180; break;
      case Surface.ROTATION_270: degrees = 270; break;
    }

    int result = (mCameraInfo.orientation - degrees + 360) % 360;
    mCamera.setDisplayOrientation(result);

//    mCamera.setParameters(parameters);

    // start preview with new settings
    try {
      mCamera.setPreviewDisplay(mHolder);
      mCamera.startPreview();

    } catch (Exception e){
      // cannot start preview with new settings
    }
  }


  /** Check if this device has a camera */
  private boolean checkCameraHardware(Context context) {
    return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
  }

  /** Get the first back-facing instance of the Camera object. */
  private android.hardware.Camera getCameraInstance(){
    android.hardware.Camera c = null;
    try {
      for (int id = 0; id < Camera.getNumberOfCameras(); id++) {
        Camera.CameraInfo infoThis = new Camera.CameraInfo();
        Camera.getCameraInfo(id, infoThis);
        if (infoThis.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
          c = android.hardware.Camera.open(id); // attempt to get a Camera instance
          mCameraInfo = infoThis;
          break;
        }
      }
    }
    catch (Exception e){
      // Camera is not available (in use or does not exist)
    }
    return c; // returns null if camera is unavailable
  }
}