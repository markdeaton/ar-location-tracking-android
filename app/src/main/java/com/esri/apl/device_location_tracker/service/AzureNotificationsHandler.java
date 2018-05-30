package com.esri.apl.device_location_tracker.service;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import com.esri.apl.device_location_tracker.R;
import com.microsoft.windowsazure.notifications.NotificationsHandler;

public class AzureNotificationsHandler extends NotificationsHandler {
  public static final int NOTIFICATION_ID = 1;
  Context mCtx;

  @Override
  public void onReceive(Context context, Bundle bundle) {
    mCtx = context;
    String sLocationPayload = bundle.getString("message");
    Intent intent = new Intent();
    intent.setAction(context.getString(R.string.act_location_update_available));
    intent.putExtra(context.getString(R.string.extra_location_update_data), sLocationPayload);
    // Send only to local process
    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

//    sendNotification(mCtx, sLocationPayload);
  }

/*  private static void sendNotification(Context ctx, String msg) {

    Intent intent = new Intent(ctx, MainActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

   NotificationManager nmgr = (NotificationManager)
        ctx.getSystemService(Context.NOTIFICATION_SERVICE);

    PendingIntent contentIntent = PendingIntent.getActivity(ctx, 0,
        intent, PendingIntent.FLAG_ONE_SHOT);

    Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    NotificationCompat.Builder mBuilder =
        new NotificationCompat.Builder(ctx, ctx.getString(R.string.default_notification_channel_id))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Location Update")
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText(msg))
            .setSound(defaultSoundUri)
            .setContentText(msg);

    mBuilder.setContentIntent(contentIntent);
    nmgr.notify(NOTIFICATION_ID, mBuilder.build());
  }*/
}
