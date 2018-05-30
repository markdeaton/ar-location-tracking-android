package com.esri.apl.device_location_tracker.service;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.esri.apl.device_location_tracker.R;
import com.google.firebase.iid.FirebaseInstanceId;
import com.microsoft.windowsazure.messaging.NotificationHub;

public class AzureNotifHubUpdateFCMTokenSvc extends IntentService {

  private static final String TAG = "RegIntentService";

  private NotificationHub hub;

  public AzureNotifHubUpdateFCMTokenSvc() {
    super(TAG);
  }

  @Override
  protected void onHandleIntent(Intent intent) {

    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    String regID;

    try {
      String FCM_token = FirebaseInstanceId.getInstance().getToken();
      Log.d(TAG, "FCM Registration Token: " + FCM_token);

      // Storing the registration ID that indicates whether the generated token has been
      // sent to your server. If it is not stored, send the token to your server,
      // otherwise your server should have already received the token.
      if (((regID=sharedPreferences.getString("registrationID", null)) == null)){

        NotificationHub hub = new NotificationHub(getString(R.string.hub_name),
            getString(R.string.hub_listen_connection_string), this);
        Log.d(TAG, "Attempting a new registration with NH using FCM token : " + FCM_token);
        regID = hub.register(FCM_token).getRegistrationId();

        // If you want to use tags...
        // Refer to : https://azure.microsoft.com/en-us/documentation/articles/notification-hubs-routing-tag-expressions/
        // regID = hub.register(token, "tag1,tag2").getRegistrationId();

        Log.d(TAG, "New NH Registration Successfully - RegId : " + regID);

        sharedPreferences.edit().putString("registrationID", regID ).apply();
        sharedPreferences.edit().putString("FCMtoken", FCM_token ).apply();
      }

      // Check if the token may have been compromised and needs refreshing.
      else if (!sharedPreferences.getString("FCMtoken", "").equals(FCM_token)) {

        NotificationHub hub = new NotificationHub(getString(R.string.hub_name),
            getString(R.string.hub_listen_connection_string), this);
        Log.d(TAG, "NH Registration refreshing with token : " + FCM_token);
        regID = hub.register(FCM_token).getRegistrationId();

        // If you want to use tags...
        // Refer to : https://azure.microsoft.com/en-us/documentation/articles/notification-hubs-routing-tag-expressions/
        // regID = hub.register(token, "tag1,tag2").getRegistrationId();

        Log.d(TAG, "New NH Registration Successfully - RegId : " + regID);

        sharedPreferences.edit().putString("registrationID", regID ).apply();
        sharedPreferences.edit().putString("FCMtoken", FCM_token ).apply();
      }

      else {
        Log.d(TAG, "Previously Registered Successfully - RegId : " + regID);
      }
    } catch (Exception e) {
      Log.e(TAG, "Failed to complete registration", e);
      // If an exception happens while fetching the new token or updating our registration data
      // on a third-party server, this ensures that we'll attempt the update at a later time.
    }
  }
}