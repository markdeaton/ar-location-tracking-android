package com.esri.apl.device_location_tracker.service;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;

import com.esri.apl.device_location_tracker.MainActivity;
import com.esri.apl.device_location_tracker.R;
import com.microsoft.windowsazure.notifications.NotificationsHandler;

import java.util.concurrent.atomic.AtomicInteger;

public class AzureNotificationsHandler extends NotificationsHandler {
  private static final String TAG = "NotificationHandler";

  @Override
  public void onReceive(Context context, Bundle bundle) {
    String sLocationPayload = bundle.getString("message");

    // Notify the main activity of updated information via broadcast
    Intent intent = new Intent();
    intent.setAction(context.getString(R.string.act_location_update_available));
    intent.putExtra(context.getString(R.string.extra_notification_update_data), sLocationPayload);
    // Send only to local process
    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
  }

  private static final AtomicInteger notificationId = new AtomicInteger(Integer.MIN_VALUE);
  public static void createAndroidNotification(Context ctx, String msg, String title, Integer msgId, String groupId) {

    Intent intent = new Intent(ctx, MainActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

    NotificationManager nmgr = (NotificationManager)
            ctx.getSystemService(Context.NOTIFICATION_SERVICE);

    PendingIntent contentIntent = PendingIntent.getActivity(ctx, 0,
            intent, PendingIntent.FLAG_ONE_SHOT);

    String sChannelId = ctx.getString(R.string.default_notification_channel_id);

    int iMsgId = msgId != null ? msgId :  notificationId.getAndIncrement();
    String sGroupId = groupId != null ?
            groupId : ctx.getString(R.string.default_notification_group_id);

    NotificationCompat.Builder mBuilder =
        new NotificationCompat.Builder(ctx, null) //ctx.getString(R.string.default_notification_channel_id))
                .setSmallIcon(R.drawable.ic_users_update)
                .setContentTitle(title)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(msg))
                .setChannelId(sChannelId)
                .setGroup(sGroupId)
                .setContentText(msg);

    mBuilder.setContentIntent(contentIntent);
    nmgr.notify(iMsgId, mBuilder.build());
  }
}
