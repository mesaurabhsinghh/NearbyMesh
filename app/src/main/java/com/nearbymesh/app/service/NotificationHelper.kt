package com.nearbymesh.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.nearbymesh.app.R
import com.nearbymesh.app.ui.MainActivity

object NotificationHelper {

    const val CHANNEL_MESSAGES_ID = "nearbymesh_incoming_messages"
    const val KEY_TEXT_REPLY = "key_direct_reply_text"
    const val EXTRA_PEER_ID = "extra_notification_peer_id"
    const val EXTRA_PEER_NAME = "extra_notification_peer_name"

    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // 1. High-Priority Heads-Up Channel for Incoming Messages & Direct Reply
            val messageChannel = NotificationChannel(
                CHANNEL_MESSAGES_ID,
                "Nearby Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for incoming offline peer messages with inline reply"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 100, 200)
                setShowBadge(true)
            }
            manager.createNotificationChannel(messageChannel)
        }
    }

    /**
     * Shows a Heads-Up message alert with native Direct Reply (RemoteInput)
     */
    fun showIncomingMessageNotification(
        context: Context,
        peerId: String,
        senderName: String,
        messageText: String
    ) {
        initChannels(context)

        // 1. Tap action: opens ChatDetailScreen in MainActivity
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_PEER_ID, peerId)
            putExtra(EXTRA_PEER_NAME, senderName)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            peerId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Direct Reply Action with RemoteInput
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Reply to $senderName...")
            .build()

        val replyIntent = Intent(context, NotificationReplyReceiver::class.java).apply {
            putExtra(EXTRA_PEER_ID, peerId)
            putExtra(EXTRA_PEER_NAME, senderName)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            peerId.hashCode() + 1,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            "Reply",
            replyPendingIntent
        ).addRemoteInput(remoteInput)
         .setAllowGeneratedReplies(true)
         .build()

        // 3. Heads-up styled notification
        val notificationId = peerId.hashCode()
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .addAction(replyAction)
            .setVibrate(longArrayOf(0, 150, 100, 200))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission not granted
        }
    }

    fun cancelNotification(context: Context, peerId: String) {
        try {
            NotificationManagerCompat.from(context).cancel(peerId.hashCode())
        } catch (_: Exception) {}
    }
}
