package org.apache.cordova.firebase;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.RemoteInput;
import android.util.Log;
import android.app.Notification;
import android.text.TextUtils;
import android.content.ContentResolver;
import android.graphics.Color;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import java.util.Map;
import java.util.Random;

public class FirebasePluginMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FirebasePlugin";
    private static final String FILE_NAME="notificationMapping.json";
    private static final String NOTIFICATION_REPLY = "NotificationReply";

    private static final int REQUEST_CODE_HELP = 101;
    private static final String VNC_PEER_JID = "vncPeerJid";
    private static final String  NOTIFY_ID = "id";

    private String getStringResource(String name) {
        return this.getString(
                this.getResources().getIdentifier(
                        name, "string", this.getPackageName()
                )
        );
    }

    /**
     * Called when message is received.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {

        File file = new File(this.getFilesDir(), FILE_NAME);

        FileReader fileReader = null;
        FileWriter fileWriter = null;
        BufferedReader bufferedReader = null;
        BufferedWriter bufferedWriter = null;

        String response = null;

        if (!file.exists()) {
            try {
                file.createNewFile();
                fileWriter = new FileWriter(file.getAbsoluteFile());
                bufferedWriter = new BufferedWriter(fileWriter);
                bufferedWriter.write("{}");
                bufferedWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }


        }


        // [START_EXCLUDE]
        // There are two types of messages data messages and notification messages. Data messages are handled
        // here in onMessageReceived whether the app is in the foreground or background. Data messages are the type
        // traditionally used with GCM. Notification messages are only received here in onMessageReceived when the app
        // is in the foreground. When the app is in the background an automatically generated notification is displayed.
        // When the user taps on the notification they are returned to the app. Messages containing both notification
        // and data payloads are treated as notification messages. The Firebase console always sends notification
        // messages. For more see: https://firebase.google.com/docs/cloud-messaging/concept-options
        // [END_EXCLUDE]

        // Pass the message to the receiver manager so any registered receivers can decide to handle it
        boolean wasHandled = FirebasePluginMessageReceiverManager.onMessageReceived(remoteMessage);
        if (wasHandled) {
            Log.d(TAG, "Message was handled by a registered receiver");

            // Don't process the message in this method.
            return;
        }

        // TODO(developer): Handle FCM messages here.
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        String title = "";
        String text = "";
        String id = "";
        String sound = "";
        String lights = "";
        Map<String, String> data = remoteMessage.getData();

        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            text = remoteMessage.getNotification().getBody();
            id = remoteMessage.getMessageId();
        } else if (data != null) {
            title = data.get("n_t");
            text = data.get("n_b");
            id = data.get("id");
            sound = data.get("sound");
            lights = data.get("lights"); //String containing hex ARGB color, miliseconds on, miliseconds off, example: '#FFFF00FF,1000,3000'

            if (TextUtils.isEmpty(text)) {
                text = data.get("body");
            }
        }

        if (TextUtils.isEmpty(id)) {
            Random rand = new Random();
            int n = rand.nextInt(50) + 1;
            id = Integer.toString(n);
        }

        // TODO: Add option to developer to configure if show notification when app on foreground
        if (!TextUtils.isEmpty(text) || !TextUtils.isEmpty(title) || (data != null && !data.isEmpty())) {
            boolean showNotification = (FirebasePlugin.inBackground() || !FirebasePlugin.hasNotificationsCallback()) && (!TextUtils.isEmpty(text) || !TextUtils.isEmpty(title));
            sendNotification(id, title, text, data, showNotification, sound, lights);
        }

        try {

            StringBuffer output = new StringBuffer();
            fileReader = new FileReader(file.getAbsolutePath());
            bufferedReader = new BufferedReader(fileReader);

            String line = "";

            while ((line = bufferedReader.readLine()) != null) {
                output.append(line + "\n");
            }

            response = output.toString();

            bufferedReader.close();

            JSONObject messageDetails = new JSONObject(response);
            Boolean isUserExisting = messageDetails.has(title);

            if (isUserExisting) {
                JSONArray userMessages = (JSONArray) messageDetails.get(title);
                userMessages.put(id);
            } else {
                JSONArray newUserMessages = new JSONArray();
                newUserMessages.put(id);
                messageDetails.put(title, newUserMessages);
            }

            fileWriter = new FileWriter(file.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fileWriter);
            bw.write(messageDetails.toString());
            bw.close();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void sendNotification(String id, String title, String messageBody, Map<String, String> data, boolean showNotification, String sound, String lights) {
        Bundle bundle = new Bundle();
        if (data != null) {
            bundle.putString(VNC_PEER_JID, title);
            bundle.putString("vncEventType", "chat");
            bundle.putInt(NOTIFY_ID, Integer.parseInt(id));
        }
        PendingIntent replyPendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            replyPendingIntent = PendingIntent.getBroadcast(
                    getApplicationContext(),
                    REQUEST_CODE_HELP,
                    new Intent(this, NotificationReceiver.class)
                            .setAction(NOTIFICATION_REPLY)
                            .putExtra(VNC_PEER_JID, title)
                            .putExtra(NOTIFY_ID,id),
                    PendingIntent.FLAG_UPDATE_CURRENT
            );

        } else {
            replyPendingIntent = PendingIntent.getActivity(getApplicationContext(),
                    REQUEST_CODE_HELP,
                    new Intent(this, ReplyActivity.class)
                            .setAction(NOTIFICATION_REPLY)
                            .putExtra(VNC_PEER_JID, title)
                            .putExtra(NOTIFY_ID,id),
                    PendingIntent.FLAG_UPDATE_CURRENT);
        }


        NotificationCompat.Action action = new NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_revert, "Reply", replyPendingIntent)
                .addRemoteInput(new RemoteInput.Builder("Reply")
                        .setLabel("Type your message").build())
                .setAllowGeneratedReplies(true)
                .build();



        if (showNotification) {
            Intent intent = new Intent(this, OnNotificationOpenReceiver.class);
            intent.putExtras(bundle);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, Integer.parseInt(id), intent, PendingIntent.FLAG_UPDATE_CURRENT);

            String channelId = this.getStringResource("default_notification_channel_id");
            String channelName = this.getStringResource("default_notification_channel_name");
            Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId);
            notificationBuilder
                    .setContentTitle(title)
                    .setContentText(messageBody)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(messageBody))
                    .setAutoCancel(true)
                    .setShowWhen(true)
                    .setContentIntent(pendingIntent)
                   // .addAction(action)
                    .setSound(defaultSoundUri)
                    .setGroup(title)
                    .setPriority(NotificationCompat.PRIORITY_MAX);


            int resID = getResources().getIdentifier("logo", "drawable", getPackageName());
            if (resID != 0) {
                notificationBuilder.setSmallIcon(resID);
            } else {
                notificationBuilder.setSmallIcon(getApplicationInfo().icon);
            }

            if (sound != null) {
                Log.d(TAG, "sound before path is: " + sound);
                Uri soundPath = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + getPackageName() + "/raw/" + sound);
                Log.d(TAG, "Parsed sound is: " + soundPath.toString());
                notificationBuilder.setSound(soundPath);
            } else {
                Log.d(TAG, "Sound was null ");
            }

            if (lights != null) {
                try {
                    String[] lightsComponents = lights.replaceAll("\\s", "").split(",");
                    if (lightsComponents.length == 3) {
                        int lightArgb = Color.parseColor(lightsComponents[0]);
                        int lightOnMs = Integer.parseInt(lightsComponents[1]);
                        int lightOffMs = Integer.parseInt(lightsComponents[2]);

                        notificationBuilder.setLights(lightArgb, lightOnMs, lightOffMs);
                    }
                } catch (Exception e) {
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                int accentID = getResources().getIdentifier("accent", "color", getPackageName());
                notificationBuilder.setColor(getResources().getColor(accentID, null));

            }

            Notification notification = notificationBuilder.build();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                int iconID = android.R.id.icon;
                int notiID = getResources().getIdentifier("icon" +
                        "", "mipmap", getPackageName());
                if (notification.contentView != null) {
                    notification.contentView.setImageViewResource(iconID, notiID);
                }
            }
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

           //  Since android Oreo notification channel is needed.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
                notificationManager.createNotificationChannel(channel);
            }

            notificationManager.notify(Integer.parseInt(id), notification);


        }
    }

}


