package com.nearbymesh.app.core.models

import com.nearbymesh.app.ui.theme.AppThemeMode
import java.util.UUID

enum class FileCategory(val label: String) {
    FILES("Files"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    APPS("Apps"),
    FOLDERS("Folders")
}

data class UserProfile(
    val displayName: String = "Saurabh",
    val handle: String = "@saurabh_device",
    val statusMessage: String = "Stay connected, offline",
    val avatarId: Int = 0,
    val profileImageUri: String? = null,
    val isVisibilityEnabled: Boolean = true,
    val isMeshRelayEnabled: Boolean = true,
    val isDiscoveryEnabled: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.DARK
)

data class RealFileItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sizeBytes: Long,
    val mimeType: String = "application/octet-stream",
    val category: FileCategory = FileCategory.FILES,
    val uriString: String? = null,
    val localPath: String? = null,
    var progress: Float = 0f,
    var status: String = "Waiting"
) {
    val formattedSize: String
        get() {
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> "%.1f GB".format(gb)
                mb >= 1.0 -> "%.1f MB".format(mb)
                kb >= 1.0 -> "%.1f KB".format(kb)
                else -> "$sizeBytes B"
            }
        }
}

enum class MessageContentType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    LOCATION,
    FILE
}

enum class MessageStatus {
    QUEUED,
    SENT,
    DELIVERED_VIA_RELAY,
    READ
}

data class IncomingMessageAlert(
    val messageId: String,
    val senderNodeId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class RichChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderNodeId: String,
    val senderName: String,
    val recipientNodeId: String,
    val text: String,
    val isFromMe: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val contentType: MessageContentType = MessageContentType.TEXT,
    val mediaUri: String? = null,
    val audioDurationSeconds: Int = 0,
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val locationTitle: String? = null,
    val fileName: String? = null,
    val fileSizeFormatted: String? = null,
    val hopCount: Int = 0,
    val hopPath: List<String> = emptyList(),
    val status: MessageStatus = MessageStatus.SENT,
    val isDelivered: Boolean = true
)
