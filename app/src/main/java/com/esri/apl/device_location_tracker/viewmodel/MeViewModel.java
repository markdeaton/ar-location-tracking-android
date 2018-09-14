package com.esri.apl.device_location_tracker.viewmodel;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.util.Base64;
import android.util.Log;
import android.view.View;

import com.esri.apl.device_location_tracker.R;
import com.esri.apl.device_location_tracker.exception.SendPushNotificationException;
import com.esri.apl.device_location_tracker.util.ColorUtils;
import com.esri.apl.device_location_tracker.util.MessageUtils;
import com.esri.arcgisruntime.mapping.view.Camera;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class MeViewModel extends AndroidViewModel {
  private final static String TAG = "MeViewModel";
  private final static String PP_ANDROID = "gcm";
  private final static String PP_IOS = "apple";

  // Property fields (these won't propagate back to the user interface)
  private String _color;
  @NonNull private String _userId;
  private Camera _camera;
  // TODO This probably should propagate to UI as LiveData
  private boolean trackingSwitchChecked = false;

  private MutableLiveData<Integer> _locationValsVisibility = new MutableLiveData<>();
  public MutableLiveData<Throwable> observableException = new MutableLiveData<>();

  public MeViewModel(@NonNull Application application) {
    super(application);

    // Get a random color as the default
    @ColorInt int randomColor = (int) (Math.random() * Math.pow(16, 6));
    String sColor = ColorUtils.intToString(randomColor);
    // Try to retrieve previous values from settings
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(application);
    _color = prefs.getString(application.getString(R.string.pref_me_color), sColor);
    _userId = prefs.getString(application.getString(R.string.pref_me_userid),
            Settings.Secure.getString(getApplication().getContentResolver(), Settings.Secure.ANDROID_ID));
    int locationValsVisibility = prefs.getInt(
            application.getString(R.string.pref_me_location_val_vis), View.GONE);
    _locationValsVisibility.postValue(locationValsVisibility);
  }

  @Override
  protected void onCleared() {
    super.onCleared();

    if (isTrackingSwitchChecked()) {
      sendGoodbye();
      setTrackingSwitchChecked(false);
    }

    saveMyPrefs();
  }

  private void saveMyPrefs() {
    Context app = getApplication();

    SharedPreferences.Editor prefsEd = PreferenceManager.getDefaultSharedPreferences(app).edit();

    prefsEd.putString(app.getString(R.string.pref_me_userid), _userId);
    prefsEd.putString(app.getString(R.string.pref_me_color), _color);
    prefsEd.putInt(app.getString(R.string.pref_me_location_val_vis), _locationValsVisibility.getValue());
    prefsEd.apply();
  }


  public LiveData<Integer> getLocationValsVisibility() {
    return _locationValsVisibility;
  }
  private void setLocationValsVisibility(boolean bVis) {
    int iVis = bVis ? View.VISIBLE : View.GONE;
    _locationValsVisibility.postValue(iVis);
  }
  public void toggleLocationValsVisibility() {
    Integer vis = getLocationValsVisibility().getValue();
    setLocationValsVisibility(!(vis != null && vis == View.VISIBLE));
  }

  public boolean isTrackingSwitchChecked() {
    return trackingSwitchChecked;
  }

  public void setTrackingSwitchChecked(boolean trackingSwitchStatus) {
    // TODO if trackingSwitchChecked becomes LiveData, post an update instead
    if (this.trackingSwitchChecked != trackingSwitchStatus)
      this.trackingSwitchChecked = trackingSwitchStatus;
  }

  /** Color is stored as hex RRGGBB stored WITHOUT an initial hash symbol (#).
   * There are cases when the hash is needed for interfacing with Android system; this assists that. */
  public String getColorStandardString() {
    return "#" + _color;
  }

  /** Color is hex RRGGBB stored WITHOUT an initial hash symbol (#) */
  public String getColor() {
    return _color;
  }

  /** Color is hex RRGGBB stored WITHOUT an initial hash symbol (#) */
  public void setColor(String color) {
    if (color.startsWith("#")) this._color = color.substring(1);
    else this._color = color;
    if (isTrackingSwitchChecked()) sendColorUpdate();
  }

  @NonNull public String getUserId() {
    return _userId;
  }

  public void setUserId(@NonNull String userId) {
    this._userId = userId;
  }

  public void setCamera(Camera camera) {
    this._camera = camera;
    if (isTrackingSwitchChecked()) sendLocationUpdate();
  }

  public void sendHello() {
    String message = getApplication().getString(R.string.update_hello, _userId, _color);
    sendPushNotifications(message, null);
  }
  public void sendColorUpdate() {
    String message = getApplication().getString(R.string.update_color, _userId, _color);
    sendPushNotifications(message, null);
  }
  private void sendLocationUpdate() {
    String message = getApplication().getString(R.string.update_location,
            _userId,
            _camera.getLocation().getX(), _camera.getLocation().getY(), _camera.getLocation().getZ(),
            _camera.getHeading(), _camera.getPitch(), _camera.getRoll());

    sendPushNotifications(message, "Location");
  }
  public void sendGoodbye() {
    String message = getApplication().getString(R.string.update_goodbye, _userId);
    sendPushNotifications(message, null);
  }

  /** Send a push notification to other app users, via Azure Notification Hub.
   *
   * @param sMessage The info update notification text to broadcast to all app users
   * @param sAndroidCollapseKey Collapse key for Android LOCATION messages only
   *    (<a href="https://developers.google.com/cloud-messaging/concept-options#collapsible_and_non-collapsible_messages" target="_blank">More Info</a>)
   */
  private void sendPushNotifications(String sMessage, String sAndroidCollapseKey) {
    String payloadAndroid;
    if (sAndroidCollapseKey == null)
      payloadAndroid = getApplication().getString(R.string.push_msg_android, sMessage); //"{\"data\":{\"message\":\"" + sPayload + "\"}}";
    else
      payloadAndroid = getApplication().getString(R.string.push_msg_android_collapsekey, sAndroidCollapseKey, sMessage);

    sendPushNotification(payloadAndroid, PP_ANDROID);

    final String payloadApple = getApplication().getString(R.string.push_msg_apple, sMessage); //"{\"aps\":{\"alert\":\"" + sPayload + "\"}}";
    sendPushNotification(payloadApple, PP_IOS);

    Log.i(TAG,"Send " + sMessage);
  }

  /** Send an update notification to a single platform (Android or Apple) since they
   * require different formats.
   * @param jsonPayload The entire json to be sent (properly formatted for the specified platform)
   * @param sPlatform The platform to target ("gcm" for Android, "apple" for iOS)
   */
  private void sendPushNotification(String jsonPayload, String sPlatform) {
    new Thread()
    {
      public void run()
      {
        try
        {
          // Based on reference documentation...
          // http://msdn.microsoft.com/library/azure/dn223273.aspx
          ParseConnectionString();
          URL url = new URL(hubEndpoint + getApplication().getString(R.string.hub_name) +
                  "/messages/?api-version=2015-01");

          HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();

          try {
            // POST request
            urlConnection.setDoOutput(true);

            // Authenticate the POST request with the SaS token
            urlConnection.setRequestProperty("Authorization", generateSasToken(url.toString()));

            // Notification format varies depending on push platform
            urlConnection.setRequestProperty("ServiceBusNotification-Format", sPlatform);

            // Include any tags
            // Example below targets 3 specific tags
            // Refer to : https://azure.microsoft.com/en-us/documentation/articles/notification-hubs-routing-tag-expressions/
            // urlConnection.setRequestProperty("ServiceBusNotification-Tags",
            //      "tag1 || tag2 || tag3");

            // Send notification message
            urlConnection.setFixedLengthStreamingMode(jsonPayload.getBytes().length);
            OutputStream bodyStream = new BufferedOutputStream(urlConnection.getOutputStream());
            bodyStream.write(jsonPayload.getBytes());
            bodyStream.close();

            // Get response
            urlConnection.connect();
            int responseCode = urlConnection.getResponseCode();
            if ((responseCode != 200) && (responseCode != 201)) {
              BufferedReader br = new BufferedReader(new InputStreamReader((urlConnection.getErrorStream())));
              String line;
              StringBuilder builder = new StringBuilder("Send Notification returned " +
                      responseCode + " : ")  ;
              while ((line = br.readLine()) != null) {
                builder.append(line);
              }

              SendPushNotificationException exc = new SendPushNotificationException(
                      builder.toString());
              observableException.postValue(exc);
              throw exc;
            }
          } finally {
            urlConnection.disconnect();
          }
        }
        catch(Exception e)
        {
          observableException.postValue(new SendPushNotificationException(
                  "Exception Sending Notification : " + e.getLocalizedMessage()));
        }
      }
    }.start();
  }

  /**
   * Example code from http://msdn.microsoft.com/library/azure/dn495627.aspx to
   * construct a SaS token from the access key to authenticate a request.
   *
   * @param uri The unencoded resource URI string for this operation. The resource
   *            URI is the full URI of the Service Bus resource to which access is
   *            claimed. For example,
   *            "http://<namespace>.servicebus.windows.net/<hubName>"
   */
  private String generateSasToken(String uri) {

    String targetUri;
    String token = null;
    try {
      targetUri = URLEncoder
              .encode(uri.toLowerCase(), "UTF-8")
              .toLowerCase();

      long expiresOnDate = System.currentTimeMillis();
      int expiresInMins = 60; // 1 hour
      expiresOnDate += expiresInMins * 60 * 1000;
      long expires = expiresOnDate / 1000;
      String toSign = targetUri + "\n" + expires;

      // Get an hmac_sha1 key from the raw key bytes
      byte[] keyBytes = hubSasKeyValue.getBytes("UTF-8");
      SecretKeySpec signingKey = new SecretKeySpec(keyBytes, "HmacSHA256");

      // Get an hmac_sha1 Mac instance and initialize with the signing key
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(signingKey);

      // Compute the hmac on input data bytes
      byte[] rawHmac = mac.doFinal(toSign.getBytes("UTF-8"));

      // Using android.util.Base64 for Android Studio instead of
      // Apache commons codec
      String signature = URLEncoder.encode(
              Base64.encodeToString(rawHmac, Base64.NO_WRAP), "UTF-8");

      // Construct authorization string
      token = "SharedAccessSignature sr=" + targetUri + "&sig="
              + signature + "&se=" + expires + "&skn=" + hubSasKeyName;
    } catch (Exception e) {
      MessageUtils.showToast(getApplication(), "Exception Generating SaS : " + e.getMessage());
    }

    return token;
  }


  private String hubEndpoint, hubSasKeyName, hubSasKeyValue;
  /**
   * Example code from http://msdn.microsoft.com/library/azure/dn495627.aspx
   * to parse the connection string so a SaS authentication token can be
   * constructed.
   */
  private void ParseConnectionString()
  {
    String connectionString = getApplication().getString(R.string.hub_full_connection_string);
    String[] parts = connectionString.split(";");
    if (parts.length != 3)
      throw new RuntimeException("Error parsing connection string: " + connectionString);

    for (int i = 0; i < parts.length; i++) {
      if (parts[i].startsWith("Endpoint")) {
        this.hubEndpoint = "https" + parts[i].substring(11);
      } else if (parts[i].startsWith("SharedAccessKeyName")) {
        this.hubSasKeyName = parts[i].substring(20);
      } else if (parts[i].startsWith("SharedAccessKey")) {
        this.hubSasKeyValue = parts[i].substring(16);
      }
    }
  }


}
