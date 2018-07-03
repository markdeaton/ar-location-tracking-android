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
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.InflateException;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import com.esri.apl.device_location_tracker.exception.PayloadParseException;
import com.esri.apl.device_location_tracker.exception.UserException;
import com.esri.apl.device_location_tracker.service.AzureNotifHubUpdateFCMTokenSvc;
import com.esri.apl.device_location_tracker.service.AzureNotificationsHandler;
import com.esri.apl.device_location_tracker.util.ARUtils;
import com.esri.apl.device_location_tracker.util.ColorUtils;
import com.esri.apl.device_location_tracker.util.MessageUtils;
import com.esri.apl.device_location_tracker.util.TextUtils;
import com.esri.apl.device_location_tracker.viewmodel.AllOtherUsersViewModel;
import com.esri.apl.device_location_tracker.viewmodel.MeViewModel;
import com.esri.arcgisruntime.ArcGISRuntimeException;
import com.esri.arcgisruntime.layers.ArcGISSceneLayer;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISScene;
import com.esri.arcgisruntime.mapping.view.Camera;
import com.esri.arcgisruntime.mapping.view.FirstPersonCameraController;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.LayerSceneProperties;
import com.esri.arcgisruntime.mapping.view.SceneView;
import com.esri.arcgisruntime.security.AuthenticationManager;
import com.esri.arcgisruntime.security.DefaultAuthenticationChallengeHandler;
import com.esri.arcgisruntime.security.UserCredential;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Scene;
import com.madrapps.pikolo.HSLColorPicker;
import com.madrapps.pikolo.listeners.OnColorSelectionListener;
import com.microsoft.windowsazure.notifications.NotificationsManager;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.LENGTH_SHORT;

/**
 *  This activity basically does two things:
 *  <ol>
 *  <li>Receive and display other users' locations, involving:
 *      <ul>
*        <li>Azure/Firebase notification receiver</li>
*        <li>Local broadcast receiver rebroadcasts notification data to this activity</li>
 *      </ul>
 *  </li>
 *  <li>Detect and broadcast this user's location; services involved include:
 *      <ul>
 *        <li>ARCore position sensing</li>
 *        <li>Timer to limit update frequency</li>
 *        <li>Azure/Firebase notification sent as the above conditions dictate</li>
 *      </ul>
 *  </li>
 *  </ol>
 *
 *  <table>
 <tr>
 <th>Tracking Switch</th>
 <th>Activity State</th>
 <th>Broadcast Action</th>
 <th>Listen Action</th>
 </tr>
 <tr>
 <td>off -> on</td>
 <td>resumed</td>
 <td>send hello, start timer</td>
 <td>start listening</td>
 </tr>
 <tr>
 <td>off -> on</td>
 <td>paused</td>
 <td><i>not possible</i></td>
 <td></td>
 </tr>
 <tr>
 <td>on -> off</td>
 <td>resumed</td>
 <td>send goodbye, stop timer</td>
 <td>stop listening</td>
 </tr>
 <tr>
 <td>on -> off</td>
 <td>paused</td>
 <td><i>not possible</i></td>
 <td></td>
 </tr>
 <tr>
 <td>on</td>
 <td>paused -> resumed</td>
 <td>start timer</td>
 <td>start listening</td>
 </tr>
 <tr>
 <td>on</td>
 <td>resumed -> paused</td>
 <td>stop timer</td>
 <td>stop listening</td>
 </tr>
 <tr>
 <td>off</td>
 <td>paused -> resumed</td>
 <td>nothing</td>
 <td>nothing</td>
 </tr>
 <tr>
 <td>off</td>
 <td>resumed -> paused</td>
 <td>nothing</td>
 <td>nothing</td>
 </tr>
 </table>
 */
public class MainActivity extends AppCompatActivity implements SceneUpdateCallable {

  private static final String TAG = "MainActivity";
  private static final int REQ_CAMERA_READFILES = 0;
  private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
  private static final long UPDATE_PERIOD_MS = 5000;
  private final int TRANSLATION_FACTOR = 2000;

  private SceneView mSceneView;
  private ArSceneView mArSceneView;
  private TextView mTxtLocX, mTxtLocY, mTxtLocZ;
  private ViewGroup mLytLocationVals;

  private MenuItem mMniLocationValsVisibility, mMniUserName;

  private AllOtherUsersViewModel mOtherUsersViewModel;
  private MeViewModel mMeViewModel;
  private UserCredential mMyAGOLCredential;

  /** Timer to send location updates */
  Timer mTimerSendUpdates;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_main);

    mOtherUsersViewModel = ViewModelProviders.of(this).get(AllOtherUsersViewModel.class);
    mMeViewModel = ViewModelProviders.of(this).get(MeViewModel.class);
    mMeViewModel.getLocationValsVisibility().observe(this, mOnLocationValsVisibilityChanged);

    AuthenticationManager.setAuthenticationChallengeHandler(new DefaultAuthenticationChallengeHandler(this));
    mMyAGOLCredential = new UserCredential(getString(R.string.agol_username), getString(R.string.agol_userpw));

    mTxtLocX = findViewById(R.id.txtX); mTxtLocY = findViewById(R.id.txtY); mTxtLocZ = findViewById(R.id.txtZ);
    mLytLocationVals = findViewById(R.id.lytLocationVals);

//    ArcGISRuntimeEnvironment.setLicense(getString(R.string.license_string_std));

    setSupportActionBar(findViewById(R.id.toolbar));

    mSceneView = findViewById(R.id.scene_view);
    mSceneView.setAttributionTextVisible(false);

    GraphicsOverlay ovl = mOtherUsersViewModel.getGraphicsOverlay();
    ovl.getSceneProperties().setSurfacePlacement(LayerSceneProperties.SurfacePlacement.ABSOLUTE);
    mSceneView.getGraphicsOverlays().add(ovl);

    // Enable AR for scene view.
    mSceneView.setARModeEnabled(true);

    mArSceneView = findViewById(R.id.ar_view);

    // Start IntentService to register this application with FCM.
    if (checkPlayServices()) {
      Intent intent = new Intent(this, AzureNotifHubUpdateFCMTokenSvc.class);
      startService(intent);
    }

    // Camera Preview
    checkForPermissions();

    // Notification channel
    // TODO move this to notifications handler ?
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
/*    if (getIntent().getExtras() != null) {
      for (String key : getIntent().getExtras().keySet()) {
        Object value = getIntent().getExtras().get(key);
        Log.d(TAG, "Key: " + key + " Value: " + value);
      }
    }*/
    // [END handle_data_extras]

//         [START subscribe_topics]
//    FirebaseMessaging.getInstance().subscribeToTopic(getString(R.string.notification_topic));
//         [END subscribe_topics]

//    btnSendLocation = (Button)findViewById(R.id.btnSendLocation);
//    btnSendLocation.setOnClickListener(sendLocation);
    ptnTypeField = Pattern.compile(getString(R.string.regex_find_type),
            Pattern.CASE_INSENSITIVE);
    ptnUserField = Pattern.compile(getString(R.string.regex_find_user),
            Pattern.CASE_INSENSITIVE);
    ptnColorField = Pattern.compile(getString(R.string.regex_find_color),
            Pattern.CASE_INSENSITIVE);
    ptnCameraField = Pattern.compile(getString(R.string.regex_find_camera),
            Pattern.CASE_INSENSITIVE);

    // Register listener for other-user location updates from the notification receiver service
    IntentFilter iflt = new IntentFilter();
    iflt.addAction(getString(R.string.act_location_update_available));
    LocalBroadcastManager.getInstance(this).registerReceiver(mPushNotificationsReceiver, iflt);


    // Listen to mMeViewModel.observableError for send-notification exceptions
    mMeViewModel.observableException.observe(this, mOnSendNotificationException);

    // Manual send-updates button
    Button btnLocUpdates = findViewById(R.id.btnSendLocation);
    btnLocUpdates.setOnClickListener((view) -> {
      mMeViewModel.setCamera(mSceneView.getCurrentViewpointCamera());
//      mMeViewModel.setCamera(new Camera(37.746, -119.533, 3000, 0, 0, 0));
    });
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    try {
      getMenuInflater().inflate(R.menu.toolbar_menu, menu);
      MenuItem itmSendLocation = menu.findItem(R.id.mniTrackMe);
      SwitchCompat sliderTrackMe = itmSendLocation.getActionView().findViewById(R.id.sliderTrackMe);
      sliderTrackMe.setChecked(mMeViewModel.isTrackingSwitchChecked());
      sliderTrackMe.setOnCheckedChangeListener(mOnTrackingSwitchChanged);
      mMniLocationValsVisibility = menu.findItem(R.id.mniLocValsVisibility);
      int vis = mMeViewModel.getLocationValsVisibility().getValue();
      mMniLocationValsVisibility.setChecked(vis == View.VISIBLE);

      mMniUserName = menu.findItem(R.id.mniSetUserName);
      return true;
    } catch (InflateException e) {
      Log.e(TAG, "Couldn't inflate toolbar", e);
      return false;
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.mniLocValsVisibility:
        mMeViewModel.toggleLocationValsVisibility();
        return true;
      case R.id.mniSetUserColor:
        View lytColorPicker = getLayoutInflater().inflate(R.layout.color_picker_dialog, null);
        HSLColorPicker colorPicker = lytColorPicker.findViewById(R.id.colorPicker);
        colorPicker.setColor(ColorUtils.stringToInt(mMeViewModel.getColor()));
        final AtomicInteger color = new AtomicInteger();
        colorPicker.setColorSelectionListener(new OnColorSelectionListener() {
          @Override
          public void onColorSelected(int i) {
            color.set(i);
          }

          @Override
          public void onColorSelectionStart(int i) { }

          @Override
          public void onColorSelectionEnd(int i) { }
        });
        AlertDialog.Builder bldUserColor = new AlertDialog.Builder(this)
                .setTitle("New User Color:")
                .setView(lytColorPicker)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        mMeViewModel.setColor(ColorUtils.intToString(color.get())));
        bldUserColor.show();
        return true;
      case R.id.mniSetUserName:
        EditText txtUserId = new EditText(this);
        ViewGroup.LayoutParams lpUserName = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
/*        ViewGroup.MarginLayoutParams mlp = new ViewGroup.MarginLayoutParams(lpUserName);
        int margin = Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()));
        mlp.setMargins(margin, 0, margin, 0);*/
        txtUserId.requestLayout();
        txtUserId.setLayoutParams(lpUserName);
        txtUserId.setText(mMeViewModel.getUserId());

        AlertDialog.Builder bldUserName = new AlertDialog.Builder(this)
                .setTitle("New User ID:")
                .setView(txtUserId)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                  String sUserId = txtUserId.getText().toString();
                  if (sUserId.length() > 0) mMeViewModel.setUserId(sUserId);
                });
        AlertDialog dlgColor = bldUserName.create();
        dlgColor.getWindow().setLayout(480, 600);
        dlgColor.show();
        return true;

      default: return super.onOptionsItemSelected(item);
    }
  }

  CompoundButton.OnCheckedChangeListener mOnTrackingSwitchChanged =
    new CompoundButton.OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) mMeViewModel.sendHello();
        else mMeViewModel.sendGoodbye();

        mMeViewModel.setTrackingSwitchChecked(isChecked);
        setTrackingTimerEnabled(isChecked);

        // Disallow changing username while participating in tracking
        mMniUserName.setEnabled(!isChecked);

        mOtherUsersViewModel.stopTracking();
      }
    };

  private Pattern ptnTypeField;
  private Pattern ptnUserField;
  private Pattern ptnColorField;
  private Pattern ptnCameraField;
  /** Receive a local broadcast from push message handler */
  private BroadcastReceiver mPushNotificationsReceiver = new BroadcastReceiver() {
    static final int NOTIFICATION_ID_DEFAULT = 1;

    @Override
    public void onReceive(Context ctx, Intent intent) {
      String sType = null, sUser, sColor, sLocation;

      String sLocationPayload = intent.getStringExtra(getString(R.string.extra_notification_update_data));

      if (sLocationPayload.length() <= 0) return;

      // Parse out the results
      try {
        sUser = TextUtils.matchedGroupVal(ptnUserField, sLocationPayload);
        sType = TextUtils.matchedGroupVal(ptnTypeField, sLocationPayload);

        // Ignore notifications if they're about ourselves
        if (sUser.equals(mMeViewModel.getUserId())) return;

        // Don't process the message if we're not tracking
        if (mMeViewModel.isTrackingSwitchChecked()) {
          // Handle differently, depending on message type
          if (sType.toUpperCase().equals(getString(R.string.updatetype_hello).toUpperCase())) {
            // HELLO
            sColor = TextUtils.matchedGroupVal(ptnColorField, sLocationPayload);
            int iColor = ColorUtils.stringToInt(sColor);
            mOtherUsersViewModel.createUserGraphics(sUser, iColor);
            // Respond with color update; a way of saying Hello back
            mMeViewModel.sendColorUpdate();
          } else if (sType.toUpperCase().equals(getString(R.string.updatetype_goodbye).toUpperCase())) {
            // GOODBYE
            mOtherUsersViewModel.removeUserGraphics(sUser);
          } else if (sType.toUpperCase().equals(getString(R.string.updatetype_color).toUpperCase())) {
            // COLOR
            sColor = TextUtils.matchedGroupVal(ptnColorField, sLocationPayload);
            int iColor = ColorUtils.stringToInt(sColor);
            mOtherUsersViewModel.updateUserGraphicsColor(sUser, iColor);
          } else if (sType.toUpperCase().equals(getString(R.string.updatetype_location).toUpperCase())) {
            // LOCATION UPDATE
            sLocation = TextUtils.matchedGroupVal(ptnCameraField, sLocationPayload);
            // Location is 6 comma-separated values: x,y,z,heading,pitch,roll
            String[] aryLocVals = sLocation.split(",");
            int aryLen = aryLocVals.length;
            if (aryLen < 6) throw new PayloadParseException(
                    "Location has " + aryLen + " values instead of 6");
            double x = Double.parseDouble(aryLocVals[0]);
            double y = Double.parseDouble(aryLocVals[1]);
            double z = Double.parseDouble(aryLocVals[2]);
            double heading = Double.parseDouble(aryLocVals[3]);
            double pitch = Double.parseDouble(aryLocVals[4]);
            double roll = Double.parseDouble(aryLocVals[5]);
            mOtherUsersViewModel.updateUserGraphicsLocation(sUser, x, y, z, heading, pitch, roll);
          }
        }

        // Create notification on local device
        int msgId = NOTIFICATION_ID_DEFAULT;
        String groupId = getString(R.string.default_notification_group_id);
        if (sType == null || sType.length() == 0) {
          // Use defaults already set
        } else if (sType.toUpperCase().equals(ctx.getString(R.string.updatetype_hello).toUpperCase())) {
          msgId = R.string.update_hello;
          groupId = getString(R.string.hello_notification_group_id);
        } else if (sType.toUpperCase().equals(ctx.getString(R.string.updatetype_goodbye).toUpperCase())) {
          msgId = R.string.update_goodbye;
          groupId = getString(R.string.goodbye_notification_group_id);
        } else if (sType.toUpperCase().equals(ctx.getString(R.string.updatetype_color).toUpperCase())) {
          msgId = R.string.update_color;
          groupId = getString(R.string.color_notification_group_id);
        } else if (sType.toUpperCase().equals(ctx.getString(R.string.updatetype_location).toUpperCase())) {
          msgId = R.string.update_location;
          groupId = getString(R.string.location_notification_group_id);
        }
        AzureNotificationsHandler.createAndroidNotification(ctx, sLocationPayload, null, null);      } catch (PayloadParseException exc) {
        MessageUtils.showToast(ctx,
                "Notification error: unexpected message.\n" + sLocationPayload + "\n\n" + exc.getLocalizedMessage(), LENGTH_LONG);
      } catch (UserException e) {
        MessageUtils.showToast(MainActivity.this, e.getLocalizedMessage(), LENGTH_SHORT);
      } finally {
        // Put notification code here if you also want notifications from the local device
      }
    }
  };

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
    lyrScene.setCredential(mMyAGOLCredential);

    mSceneView.getScene().getOperationalLayers().add(lyrScene);
    lyrScene.addDoneLoadingListener(() -> {
      if (lyrScene.getLoadStatus() == LoadStatus.LOADED) {
        Log.d(TAG, "Layer loaded");

        double x = Double.parseDouble(getString(R.string.startloc_x));
        double y = Double.parseDouble(getString(R.string.startloc_y));
        double z = Double.parseDouble(getString(R.string.startloc_z));

        // add a camera and initial camera position
        Camera mCamStart = new Camera(y, x, z, 0, 0, 0);

        // Scene camera controlled by sensors
        FirstPersonCameraController fpcController = new FirstPersonCameraController();
        fpcController.setInitialPosition(mCamStart);
        fpcController.setTranslationFactor(TRANSLATION_FACTOR);

        ARCoreSource arSource = new ARCoreSource(
                mArSceneView.getScene(), mArSceneView.getSession(), mCamStart, MainActivity.this);
        fpcController.setDeviceMotionDataSource(arSource);
        fpcController.setFramerate(FirstPersonCameraController.FirstPersonFramerate.BALANCED);
        mSceneView.setCameraController(fpcController);

        arSource.startAll();
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

  @Override
  protected void onPause(){

    mSceneView.pause();
    mArSceneView.pause();

    // Stop listening and broadcasting if switch is on
    if (mMeViewModel.isTrackingSwitchChecked()) {
      setTrackingTimerEnabled(false);
      // Azure hub - this means no need to stop listening to local broadcasts, since
      // only Azure notifications prompt local broadcasts in this app
      NotificationsManager.stopHandlingNotifications(this);
    }

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

    try {
      mArSceneView.resume();
    } catch (CameraNotAvailableException e) {
      MessageUtils.showToast(this, "The camera cannot be acquired. " + e.getLocalizedMessage());
    }

    mSceneView.resume();

    // Start listening and broadcasting if switch is on
    if (mMeViewModel.isTrackingSwitchChecked()) {
      setTrackingTimerEnabled(true);
      NotificationsManager.handleNotifications(this, getString(R.string.sender_id), AzureNotificationsHandler.class);
    }
  }

  @Override
  protected void onDestroy() {
    // Stop listening for other user location data
    LocalBroadcastManager.getInstance(this).unregisterReceiver(mPushNotificationsReceiver);

    mSceneView.dispose();
    mArSceneView.destroy();

//    mMeViewModel.saveMyPrefs(); // Done by the ViewModel

    super.onDestroy();
  }
/*  private void releaseCamera(){
    if (mCamera != null){
      mCamera.release();        // release the camera for other applications
      mCamera = null;
    }
  }*/
  /**
   * Check the device to make sure it has the Google Play Services APK. If
   * it doesn't, display a dialog that allows users to download the APK from
   * the Google Play Store or enable it in the device's system settings.
   */
  private boolean checkPlayServices() {
    GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
    int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
    if (resultCode != ConnectionResult.SUCCESS) {
      if (apiAvailability.isUserResolvableError(resultCode)) {
        apiAvailability.getErrorDialog(this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST)
                .show();
      } else {
        Log.i(TAG, "This device is not supported by Google Play Services.");
        MessageUtils.showToast(this, "This device is not supported by Google Play Services.");
        finish();
      }
      return false;
    }
    return true;
  }

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

  private Observer<Throwable> mOnSendNotificationException = new Observer<Throwable>() {
    @Override
    public void onChanged(@Nullable Throwable throwable) {
      MessageUtils.showToast(MainActivity.this, throwable.getLocalizedMessage(), LENGTH_LONG);
    }
  };

  private Observer<Integer> mOnLocationValsVisibilityChanged = new Observer<Integer>() {
    @Override
    public void onChanged(@Nullable Integer vis) {
      assert vis != null;
      if (mLytLocationVals != null) mLytLocationVals.setVisibility(vis);
      if (mMniLocationValsVisibility != null)
        mMniLocationValsVisibility.setChecked(vis == View.VISIBLE);
    }
  };

  @Override
  public void onSceneUpdate(Scene scene, Session session, Frame frame, FrameTime frameTime) {
/*    // Send a location-update notification if...
    // 1. This is a new ARCore frame
    // 2. ARCore is currently tracking
    // 3. The update timer has signaled that it's okay to send another update
    if ((mLastFrameTime == null || (frameTime.getStartSeconds() > mLastFrameTime))
        && mIsTimeToUpdateLocation
        && frame.getCamera().getTrackingState() == TrackingState.TRACKING) {
      Pose pose = frame.getCamera().getPose();

      Point ptUpdated = new Point(mStartPtWM.getX() + (pose.tx() * TRANSLATION_FACTOR),
              mStartPtWM.getY() + (pose.ty() * TRANSLATION_FACTOR),
              mStartPtWM.getZ() + (pose.tz() * TRANSLATION_FACTOR),
              SpatialReferences.getWebMercator());
      Camera cam = new Camera(
              ptUpdated,
              mSceneView.getCurrentViewpointCamera().getHeading(),
              mSceneView.getCurrentViewpointCamera().getPitch(),
              mSceneView.getCurrentViewpointCamera().getRoll()
      );
      mMeViewModel.setCamera(cam);

      mIsTimeToUpdateLocation = false;
    }
    // Make a note of the last time a frame was evaluated
    mLastFrameTime = frameTime.getStartSeconds();*/

    // Just update the location bar, if it's visible
    if (frame.getCamera().getTrackingState() == TrackingState.TRACKING
        && mLytLocationVals.getVisibility() == View.VISIBLE) {
      Camera cam = mSceneView.getCurrentViewpointCamera();
      mTxtLocX.setText(getString(R.string.loc_x, cam.getLocation().getX()));
      mTxtLocY.setText(getString(R.string.loc_y, cam.getLocation().getY()));
      mTxtLocZ.setText(getString(R.string.loc_z, cam.getLocation().getZ()));
    }
  }

  @Override
  public void onSceneError(Throwable e) {
    Log.w(TAG, "Error on frame update", e);
//    MessageUtils.showToast(MainActivity.this, e.getLocalizedMessage(), LENGTH_LONG);
  }

  private void setTrackingTimerEnabled(boolean bEnableTimer) {
    try {
      if (bEnableTimer) { // Start the location-update timer
        mTimerSendUpdates = new Timer();
        mTimerSendUpdates.schedule(new TTUpdateLocation(), 0, UPDATE_PERIOD_MS);
      } else
        mTimerSendUpdates.cancel();
    } catch (Exception e) {
      Log.e(TAG, "Error with the tracking timer", e);
    }
  }

  private class TTUpdateLocation extends TimerTask {
    @Override
    public void run() {
//      mMeViewModel.setCamera(mSceneView.getCurrentViewpointCamera());
    }
  }
}
