/**
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.esri.apl.device_location_tracker;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.InflateException;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.esri.apl.device_location_tracker.service.AzureNotifHubUpdateFCMTokenSvc;
import com.esri.apl.device_location_tracker.service.AzureNotificationsHandler;
import com.esri.arcgisruntime.ArcGISRuntimeException;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.geometry.GeometryEngine;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.PointBuilder;
import com.esri.arcgisruntime.layers.ArcGISSceneLayer;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISScene;
import com.esri.arcgisruntime.mapping.view.Camera;
import com.esri.arcgisruntime.mapping.view.FirstPersonCameraController;
import com.esri.arcgisruntime.mapping.view.SceneView;
import com.esri.arcgisruntime.security.AuthenticationManager;
import com.esri.arcgisruntime.security.DefaultAuthenticationChallengeHandler;
import com.esri.arcgisruntime.security.UserCredential;
import com.google.android.gms.tasks.Task;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.sceneform.ArSceneView;
import com.microsoft.windowsazure.notifications.NotificationsManager;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import utils.ARUtils;
import utils.MessageUtils;
import utils.PayloadParseException;
import utils.TextUtils;

public class MainActivity extends AppCompatActivity {

  private static final String TAG = "MainActivity";
  private static final int REQ_CAMERA_READFILES = 0;

//  private android.hardware.Camera mCamera;
//  private CameraPreview mPreview;

  private Button btnSendLocation;
  private SceneView mSceneView;
  private ArSceneView mArSceneView;

  private UserCredential mMe;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    AuthenticationManager.setAuthenticationChallengeHandler(new DefaultAuthenticationChallengeHandler(this));
    mMe = new UserCredential(getString(R.string.agol_username), getString(R.string.agol_userpw));

//    ArcGISRuntimeEnvironment.setLicense(getString(R.string.license_string_std));

    setSupportActionBar(findViewById(R.id.toolbar));

    mSceneView = findViewById(R.id.scene_view);
    mSceneView.setAttributionTextVisible(false);
    // Enable AR for scene view.
    mSceneView.setARModeEnabled(true);

    mArSceneView = findViewById(R.id.ar_view);

    // Azure hub stuff
    NotificationsManager.handleNotifications(this, getString(R.string.sender_id), AzureNotificationsHandler.class);
    // Start IntentService to register this application with FCM.
    Intent intent = new Intent(this, AzureNotifHubUpdateFCMTokenSvc.class);
    startService(intent);

    // Camera Preview
    checkForPermissions();

    // Notifications
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      // Create channel to show notifications.
      String channelId = getString(R.string.default_notification_channel_id);
      String channelName = getString(R.string.default_notification_channel_name);
      NotificationManager notificationManager =
          getSystemService(NotificationManager.class);
      notificationManager.createNotificationChannel(new NotificationChannel(channelId,
          channelName, NotificationManager.IMPORTANCE_LOW));
    }

    // If a notification message is tapped, any data accompanying the notification
    // message is available in the intent extras. In this sample the launcher
    // intent is fired when the notification is tapped, so any accompanying data would
    // be handled here. If you want a different intent fired, set the click_action
    // field of the notification message to the desired intent. The launcher intent
    // is used when no click_action is specified.
    //
    // Handle possible data accompanying notification message.
    // [START handle_data_extras]
    if (getIntent().getExtras() != null) {
      for (String key : getIntent().getExtras().keySet()) {
        Object value = getIntent().getExtras().get(key);
        Log.d(TAG, "Key: " + key + " Value: " + value);
      }
    }
    // [END handle_data_extras]

//         [START subscribe_topics]
//    FirebaseMessaging.getInstance().subscribeToTopic(getString(R.string.notification_topic));
//         [END subscribe_topics]

//    btnSendLocation = (Button)findViewById(R.id.btnSendLocation);
//    btnSendLocation.setOnClickListener(sendLocation);
  }

  @Override
  protected void onStart() {
    super.onStart();
    // Register listener for other-actor-position updates from the notification receiver service
    IntentFilter iflt = new IntentFilter();
    iflt.addAction(getString(R.string.act_location_update_available));
    LocalBroadcastManager.getInstance(this).registerReceiver(mLocationsReceiver, iflt);
  }

  @Override
  protected void onStop() {
    super.onStop();
    // Stop listening for chart data
    LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocationsReceiver);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    try {
      getMenuInflater().inflate(R.menu.toolbar_menu, menu);
//      mTBItems = menu;
      return true;
    } catch (InflateException e) {
      Log.e(TAG, "Couldn't inflate toolbar", e);
      return false;
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    return super.onOptionsItemSelected(item);
  }


  private View.OnClickListener sendLocation = new View.OnClickListener() {
    @Override
    public void onClick(View view) {
      Location loc = new Location("");
      loc.setLongitude(-118);
      loc.setLatitude(34);
      loc.setAltitude(350);

/*      updateLocation(loc).addOnCompleteListener(new OnCompleteListener<String>() {
        @Override
        public void onComplete(@NonNull Task<String> task) {
          if (!task.isSuccessful()) {
            Exception e = task.getException();
            if (e instanceof FirebaseFunctionsException) {
              FirebaseFunctionsException ffe = (FirebaseFunctionsException) e;
              FirebaseFunctionsException.Code code = ffe.getCode();
              Object details = ffe.getDetails();
              String sDetails = details != null ? details.toString() : "";
              Log.e(TAG, sDetails, ffe);
            }
          } else {
            Log.d(TAG, "task successful");
          }
        }
      });*/
    }
  };

  /** Receive a local broadcast from push message handler */
  private BroadcastReceiver mLocationsReceiver = new BroadcastReceiver() {
    private Pattern ptnTypeField = Pattern.compile(getString(R.string.regex_find_type),
            Pattern.CASE_INSENSITIVE);
    private Pattern ptnUserField = Pattern.compile(getString(R.string.regex_find_user),
            Pattern.CASE_INSENSITIVE);
    private Pattern ptnColorField = Pattern.compile(getString(R.string.regex_find_color),
            Pattern.CASE_INSENSITIVE);
    private Pattern ptnCameraField = Pattern.compile(getString(R.string.regex_find_camera),
            Pattern.CASE_INSENSITIVE);

    @Override
    public void onReceive(Context context, Intent intent) {
      String sType, sUser, sColor, sLocation;

      String sLocationPayload = intent.getStringExtra(getString(R.string.extra_location_update_data));
      if (sLocationPayload.length() <= 0) return;

      // Parse out the results
      try {
        sType = TextUtils.matchedGroupVal(ptnTypeField, sLocationPayload);

        // Handle differently, depending on message type
        if (sType.toUpperCase().equals(getString(R.string.typeval_initial_hello).toUpperCase())) {
          sUser = TextUtils.matchedGroupVal(ptnUserField, sLocationPayload);
          sColor = TextUtils.matchedGroupVal(ptnColorField, sLocationPayload);

        } else if (sType.toUpperCase().equals(getString(R.string.typeval_color).toUpperCase())) {
          sUser = TextUtils.matchedGroupVal(ptnUserField, sLocationPayload);
          sColor = TextUtils.matchedGroupVal(ptnColorField, sLocationPayload);

        } else if (sType.toUpperCase().equals(getString(R.string.typeval_location).toUpperCase())) {
          sUser = TextUtils.matchedGroupVal(ptnUserField, sLocationPayload);
          sLocation = TextUtils.matchedGroupVal(ptnCameraField, sLocationPayload);

        }
      } catch (PayloadParseException exc) {
        MessageUtils.showToast(context, exc.getMessage());
        return;
      }
    }
  };

    private Task<String> updateLocation(Location loc) {
    Map<String, Object> data = new HashMap<>();
    data.put("title", "title");
    data.put("x", loc.getLongitude());
    data.put("y", loc.getLatitude());
    data.put("z", loc.getAltitude());

    return null;
 /*  return  mFunctions.getHttpsCallable(getString(R.string.function_update_location)).call(data)
       .continueWith(new Continuation<HttpsCallableResult, String>() {
         @Override
         public String then(@NonNull Task<HttpsCallableResult> task) throws Exception {
           String result = (String) task.getResult().getData();
           return result;
         }
       });
  }*/
  }
  //Setup the Scene for Augmented Reality
  // Called as part of onCreate()
  private void setUpARScene() {
    // Create scene without a basemap.  Background for scene content provided by device camera.
    mSceneView.setScene(new ArcGISScene());

    // Add San Diego scene layer.  This operational data will render on a video feed (eg from the device camera).
    String sPkgPath;
    sPkgPath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        getString(R.string.scene_package_filename)).getAbsolutePath();

//    sPkgPath = getString(R.string.scene_layer_url_yosemite);
    ArcGISSceneLayer lyrScene = new ArcGISSceneLayer(sPkgPath);
    lyrScene.setCredential(mMe);

    mSceneView.getScene().getOperationalLayers().add(lyrScene);
    lyrScene.addDoneLoadingListener(() -> {
      if (lyrScene.getLoadStatus() == LoadStatus.LOADED) {
        Log.d(TAG, "Layer loaded");

        Envelope env = lyrScene.getFullExtent();
        Point ptEnvCenter = env.getCenter();
        ListenableFuture<Double> lf = mSceneView.getScene().getBaseSurface().getElevationAsync(ptEnvCenter);
        lf.addDoneListener(() -> {
          Double elev = 3000d;
          PointBuilder pb = new PointBuilder((Point) GeometryEngine.project(
                  ptEnvCenter, mSceneView.getSpatialReference()));

          pb.setZ(elev);
          Log.d(TAG, "Elevation set at " + elev);
          Point ptCenter = pb.toGeometry();
          // add a camera and initial camera position
          completeSetup(new Camera(ptCenter, 0, 0, 0));
        });
      } else {
        ArcGISRuntimeException e = lyrScene.getLoadError();
        String sErr = e.getLocalizedMessage();
        if (lyrScene.getLoadError().getCause() != null) sErr += "; " +
            lyrScene.getLoadError().getCause().getLocalizedMessage();
        Log.d(TAG, "Layer not loaded: " + sErr);
        ARUtils.displayError(this, sErr, e);
      }
    });
  }

  private void completeSetup(Camera camStart) {
    final int TRANSLATION_FACTOR = /*1000;*/ /*7*/5000;
    // Scene camera controlled by sensors
    FirstPersonCameraController fpcController = new FirstPersonCameraController();
    if (camStart != null) fpcController.setInitialPosition(camStart);

    fpcController.setTranslationFactor(TRANSLATION_FACTOR);

    ARCoreSource arSource = new ARCoreSource(mArSceneView, camStart);
    fpcController.setDeviceMotionDataSource(arSource);


    fpcController.setFramerate(FirstPersonCameraController.FirstPersonFramerate.BALANCED);
    mSceneView.setCameraController(fpcController);

    arSource.startAll();
  }

  @Override
  protected void onPause(){
    mSceneView.pause();
    mArSceneView.pause();
//    releaseCamera();
    super.onPause();
  }

  private boolean installRequested;
    @Override
  protected void onResume(){
    super.onResume();

    if (mArSceneView.getSession() == null) {
      // If the session wasn't created yet, don't resume rendering.
      // This can happen if ARCore needs to be updated or permissions are not granted yet.
      try {
        Session session = ARUtils.createArSession(this, installRequested);
        if (session == null) {
          installRequested = ARUtils.hasCameraPermission(this);
          return;
        } else {
          mArSceneView.setupSession(session);
        }
      } catch (UnavailableException e) {
        ARUtils.handleSessionException(this, e);
      }
    }

    mSceneView.resume();
    try {
      mArSceneView.resume();
    } catch (CameraNotAvailableException e) {
      MessageUtils.showToast(this, "The camera cannot be acquired. " + e.getLocalizedMessage());
    }
  }

  @Override
  protected void onDestroy() {
    mSceneView.dispose();
    mArSceneView.destroy();
    super.onDestroy();
  }
/*  private void releaseCamera(){
    if (mCamera != null){
      mCamera.release();        // release the camera for other applications
      mCamera = null;
    }
  }*/
  /**
   * Determine if we're able to use the camera
   */
  private void checkForPermissions() {
    // Explicitly check for privilege
    final int permCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
    final int permFiles = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
    String[] permsNeeded = new String[] {
            Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE };

    if (permCamera == PackageManager.PERMISSION_GRANTED &&
            permFiles == PackageManager.PERMISSION_GRANTED)
      setUpARScene();
    else {
      Log.i("MainActivity", "Permissions not granted, asking...");
      ActivityCompat.requestPermissions(this, permsNeeded, REQ_CAMERA_READFILES);
    }
  }
  @Override
  public void onRequestPermissionsResult(int requestCode,
                                         String[] permissions, int[] grantResults) {
    switch (requestCode) {
      case REQ_CAMERA_READFILES: {
        // If request is cancelled, the result arrays are empty.
        boolean allPermsGranted = true;
        for (int iPerm = 0; iPerm < grantResults.length; iPerm++) {
          if (grantResults[iPerm] != PackageManager.PERMISSION_GRANTED) {
            allPermsGranted = false;
            break;
          }
        }
        if (allPermsGranted) {
          Log.i("MainActivity", "Camera permission granted...");
          setUpARScene();
        } else {
          new AlertDialog.Builder(this)
                  .setTitle("Permissions Needed")
                  .setMessage("This app needs camera and file permissions to continue.")
                  .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                      finish();
                    }
                  })
                  .show();
        }
        return;
      }
    }
  }
}
