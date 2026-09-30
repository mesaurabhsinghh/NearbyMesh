package com.nearbymesh.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.nearbymesh.app.R
import com.nearbymesh.app.core.MeshCoordinator

class NotificationReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)?.toString()?.trim() ?: return
        val peerId = intent.getStringExtra(NotificationHelper.EXTRA_PEER_ID) ?: return
        val peerName = intent.getStringExtra(NotificationHelper.EXTRA_PEER_NAME) ?: "Peer"

        if (replyText.isNotBlank()) {
            val coordinator = MeshCoordinator.getInstance(context)
            coordinator.sendRichMessage(recipientNodeId = peerId, text = replyText)

            // Update notification to confirm reply sent
            val updatedNotification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_MESSAGES_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(peerName)
                .setContentText("✓ You: $replyText")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setTimeoutAfter(3000)
                .build()

            try {
                NotificationManagerCompat.from(context).notify(peerId.hashCode(), updatedNotification)
            } catch (_: SecurityException) {}
        }
    }
}
