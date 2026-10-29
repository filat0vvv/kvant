// SPDX-License-Identifier: GPL-2.0-or-later
package com.kvant.messenger.history;

import android.text.TextUtils;
import com.radolyn.ayugram.AyuConfig;
import com.radolyn.ayugram.AyuConstants;
import com.radolyn.ayugram.database.entities.AyuMessageBase;
import com.radolyn.ayugram.messages.AyuSavePreferences;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;

/** Local history snapshots. No server, decompiled code, or external sync is needed. */
public final class AyuMessageUtils {
    private AyuMessageUtils() {}

    public static byte[] serialize(TLRPC.Message message) {
        SerializedData data = new SerializedData();
        try {
            message.serializeToStream(data);
            return data.toByteArray();
        } finally { data.cleanup(); }
    }

    public static void capture(TLRPC.Message message, AyuMessageBase row, long userId,
                               long dialogId, int topicId, int messageId, int caughtAt,
                               boolean formatting, boolean reactions) {
        // Work on a copy: TL serialization recalculates flags on its receiver.
        TLRPC.Message copy = decode(serialize(message));
        if (!formatting) {
            copy.entities.clear();
            copy.flags &= ~128;
        }
        if (!reactions) {
            copy.reactions = null;
            copy.flags &= ~1048576;
        }
        row.serializedMessage = serialize(copy);
        row.userId = userId;
        row.dialogId = dialogId;
        row.topicId = topicId;
        row.messageId = messageId;
        row.groupedId = copy.grouped_id;
        row.peerId = MessageObject.getPeerId(copy.peer_id);
        row.fromId = MessageObject.getPeerId(copy.from_id);
        row.date = copy.date;
        row.editDate = copy.edit_date;
        row.entityCreateDate = caughtAt;
        row.text = copy.message == null ? "" : copy.message;
        row.flags = copy.flags;
        row.views = copy.views;
        if (formatting && !copy.entities.isEmpty()) {
            SerializedData entities = new SerializedData();
            try {
                entities.writeInt32(copy.entities.size());
                for (TLRPC.MessageEntity entity : copy.entities) entity.serializeToStream(entities);
                row.textEntities = entities.toByteArray();
            } finally { entities.cleanup(); }
        }
    }

    public static void map(AyuSavePreferences prefs, AyuMessageBase row) {
        capture(prefs.getMessage(), row, prefs.getUserId(), prefs.getDialogId(),
                prefs.getTopicId(), prefs.getMessageId(), prefs.getRequestCatchTime(),
                AyuConfig.saveFormatting, AyuConfig.saveReactions);
    }

    private static TLRPC.Message decode(byte[] bytes) {
        SerializedData data = new SerializedData(bytes);
        try {
            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(true), true);
            if (message == null) throw new IllegalArgumentException("Invalid history snapshot");
            return message;
        } finally { data.cleanup(); }
    }

    public static TLRPC.Message restore(AyuMessageBase row) {
        TLRPC.Message result;
        if (row.serializedMessage != null && row.serializedMessage.length > 4) {
            result = decode(row.serializedMessage);
        } else {
            // Preserve access to text-only records from a v21 history database.
            result = new TLRPC.TL_message();
            result.message = row.text == null ? "" : row.text;
            result.date = row.date;
            result.media = new TLRPC.TL_messageMediaEmpty();
            result.peer_id = new TLRPC.TL_peerUser();
            result.peer_id.user_id = row.dialogId > 0 ? row.dialogId : row.userId;
        }
        result.id = row.messageId;
        result.dialog_id = row.dialogId;
        result.attachPath = row.mediaPath == null ? "" : row.mediaPath;
        result.unread = false;
        result.media_unread = false;
        return result;
    }

    public static void map(AyuMessageBase row, TLRPC.Message target, int account) {
        TLRPC.Message source = restore(row);
        target.id = source.id; target.dialog_id = source.dialog_id;
        target.from_id = source.from_id; target.peer_id = source.peer_id;
        target.date = source.date; target.message = source.message; target.media = source.media;
        target.flags = source.flags; target.out = source.out; target.unread = false;
        target.mentioned = source.mentioned; target.media_unread = false;
        target.entities = source.entities; target.via_bot_name = source.via_bot_name;
        target.reply_markup = source.reply_markup; target.views = source.views;
        target.forwards = source.forwards; target.replies = source.replies;
        target.edit_date = source.edit_date; target.silent = source.silent; target.post = source.post;
        target.from_scheduled = source.from_scheduled; target.legacy = source.legacy;
        target.edit_hide = source.edit_hide; target.pinned = source.pinned;
        target.fwd_from = source.fwd_from; target.via_bot_id = source.via_bot_id;
        target.reply_to = source.reply_to; target.post_author = source.post_author;
        target.grouped_id = source.grouped_id; target.reactions = source.reactions;
        target.restriction_reason = source.restriction_reason; target.ttl_period = source.ttl_period;
        target.noforwards = source.noforwards; target.ayuNoforwards = source.ayuNoforwards;
        target.topic_start = source.topic_start; target.attachPath = source.attachPath;
        target.random_id = source.random_id; target.ttl = source.ttl;
        target.destroyTime = source.destroyTime; target.layer = source.layer;
        target.seq_in = source.seq_in; target.seq_out = source.seq_out;
        target.isThreadMessage = source.isThreadMessage;
    }

    private static boolean maySaveMedia(AyuSavePreferences prefs) {
        if (!AyuConfig.saveMedia) return false;
        if (prefs.getDialogId() > 0) return AyuConfig.saveMediaInPrivateChats;
        TLRPC.Chat chat = MessagesController.getInstance(prefs.getAccountId()).getChat(-prefs.getDialogId());
        if (chat == null) return false;
        boolean publicChat = !TextUtils.isEmpty(chat.username);
        if (chat.broadcast && !chat.megagroup)
            return publicChat ? AyuConfig.saveMediaInPublicChannels : AyuConfig.saveMediaInPrivateChannels;
        return publicChat ? AyuConfig.saveMediaInPublicGroups : AyuConfig.saveMediaInPrivateGroups;
    }

    public static void mapMedia(AyuSavePreferences prefs, AyuMessageBase row, boolean changed) {
        TLRPC.Message message = prefs.getMessage();
        row.documentType = message.media != null && message.media.photo != null
                ? AyuConstants.DOCUMENT_TYPE_PHOTO
                : message.media != null && message.media.document != null
                    ? AyuConstants.DOCUMENT_TYPE_FILE : AyuConstants.DOCUMENT_TYPE_NONE;
        if (message.media != null && message.media.document != null)
            row.mimeType = message.media.document.mime_type;
        if (!maySaveMedia(prefs)) return;
        try {
            File source = TextUtils.isEmpty(message.attachPath) ? null : new File(message.attachPath);
            FileLoader loader = FileLoader.getInstance(prefs.getAccountId());
            if (source == null || !source.isFile()) source = loader.getPathToMessage(message);
            File directory = new File(ApplicationLoader.applicationContext.getFilesDir(),
                    "kvant-history/" + row.userId + "/" + row.dialogId + "/" + row.messageId);
            String revision = fingerprint(row.serializedMessage);
            row.mediaPath = copyAvailable(source, directory, revision + "-media");
            ArrayList<TLRPC.PhotoSize> thumbs = message.media != null && message.media.photo != null
                    ? message.media.photo.sizes : message.media != null && message.media.document != null
                        ? message.media.document.thumbs : null;
            if (thumbs != null && !thumbs.isEmpty()) {
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(thumbs, 320);
                if (thumb != null) row.hqThumbPath = copyAvailable(loader.getPathToAttach(thumb, true), directory, revision + "-thumb");
            }
            if (row.documentType == AyuConstants.DOCUMENT_TYPE_PHOTO && row.mediaPath != null)
                row.hqThumbPath = row.mediaPath;
        } catch (Exception e) { FileLog.e(e); }
    }

    public static String fingerprint(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder();
        for (byte b : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }

    public static String copyAvailable(File source, File directory, String name) throws IOException {
        if (source == null || !source.isFile() || source.length() == 0) return null;
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create history media directory");
        String extension = source.getName().contains(".") ? source.getName().substring(source.getName().lastIndexOf('.')) : "";
        extension = extension.replaceAll("[^a-zA-Z0-9.]", "");
        File destination = new File(directory, name + extension);
        if (destination.isFile() && destination.length() == source.length()) return destination.getAbsolutePath();
        File temporary = File.createTempFile("copy-", ".part", directory);
        try {
            if (!AndroidUtilities.copyFile(source, temporary)) throw new IOException("History media copy failed");
            if (!temporary.renameTo(destination)) throw new IOException("History media commit failed");
            return destination.getAbsolutePath();
        } finally { if (temporary.exists()) temporary.delete(); }
    }

    public static File getSavedAttachment(TLRPC.Message message) {
        if (message == null || TextUtils.isEmpty(message.attachPath)) return null;
        try {
            File file = new File(message.attachPath).getCanonicalFile();
            String root = new File(ApplicationLoader.applicationContext.getFilesDir(), "kvant-history").getCanonicalPath() + File.separator;
            return file.isFile() && file.getPath().startsWith(root) ? file : null;
        } catch (IOException e) { return null; }
    }

    public static void mapMedia(AyuMessageBase row, TLRPC.Message target) {
        target.attachPath = row.mediaPath == null ? "" : row.mediaPath;
    }
}
