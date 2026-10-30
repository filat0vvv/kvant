// SPDX-License-Identifier: GPL-2.0-or-later
package com.kvant.messenger.history;

import com.radolyn.ayugram.database.entities.AyuMessageBase;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Read the full TL snapshots written by kvantGram's original v22 history store. */
public final class LegacySnapshots {
    private LegacySnapshots() {}

    public static boolean restore(AyuMessageBase row, TLRPC.Message target) {
        if (row.serializedMessage == null || row.serializedMessage.length < 4) return false;
        SerializedData data = new SerializedData(row.serializedMessage);
        try {
            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(true), true);
            if (message == null) return false;
            for (Field field : TLRPC.Message.class.getFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers()) &&
                        !field.getName().startsWith("ayu")) field.set(target, field.get(message));
            }
            target.id = row.messageId;
            target.dialog_id = row.dialogId;
            target.attachPath = row.mediaPath == null ? "" : row.mediaPath;
            target.unread = false;
            target.media_unread = false;
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        } finally { data.cleanup(); }
    }

    public static boolean restoreMedia(AyuMessageBase row, TLRPC.Message target) {
        if (row.serializedMessage == null || target.media == null) return false;
        target.attachPath = row.mediaPath == null ? "" : row.mediaPath;
        return true;
    }

    public static File getSavedAttachment(TLRPC.Message message) {
        if (message == null || message.attachPath == null || message.attachPath.isEmpty()) return null;
        try {
            File root = new File(ApplicationLoader.applicationContext.getFilesDir(), "kvant-history").getCanonicalFile();
            File file = new File(message.attachPath).getCanonicalFile();
            return file.isFile() && file.getPath().startsWith(root.getPath() + File.separator) ? file : null;
        } catch (Exception e) { return null; }
    }
}
