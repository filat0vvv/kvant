// SPDX-License-Identifier: GPL-2.0-or-later
package com.kvant.messenger.history;

import android.content.Context;
import android.content.SharedPreferences;
import tw.nekomimi.nekogram.NekoConfig;
import xyz.nextalone.nagram.NaConfig;

/** Retain the user's ghost/history choices when moving from the original client. */
public final class SettingsUpgrade {
    public static void migrate(Context context) {
        SharedPreferences marker=context.getSharedPreferences("kvant-upgrade",Context.MODE_PRIVATE);
        if (marker.getBoolean("modern-settings-v1",false)) return;
        SharedPreferences old=context.getSharedPreferences("ayuconfig",Context.MODE_PRIVATE);
        if (!old.getAll().isEmpty()) {
            boolean read=old.getBoolean("sendReadPackets",true);
            NekoConfig.sendReadMessagePackets.setConfigBool(read);
            NekoConfig.sendReadStoriesPackets.setConfigBool(read);
            NekoConfig.sendOnlinePackets.setConfigBool(old.getBoolean("sendOnlinePackets",true));
            NekoConfig.sendUploadProgress.setConfigBool(old.getBoolean("sendUploadProgress",true));
            NekoConfig.sendOfflinePacketAfterOnline.setConfigBool(old.getBoolean("sendOfflinePacketAfterOnline",false));
            NekoConfig.markReadAfterSend.setConfigBool(old.getBoolean("markReadAfterSend",false));
            NaConfig.INSTANCE.getEnableSaveDeletedMessages().setConfigBool(old.getBoolean("saveDeletedMessages",true));
            NaConfig.INSTANCE.getEnableSaveEditsHistory().setConfigBool(old.getBoolean("saveMessagesHistory",true));
            NaConfig.INSTANCE.getMessageSavingSaveMedia().setConfigBool(old.getBoolean("saveMedia",true));
            NaConfig.INSTANCE.getSaveMediaInPrivateChats().setConfigBool(old.getBoolean("saveMediaInPrivateChats",true));
            NaConfig.INSTANCE.getSaveMediaInPrivateChannels().setConfigBool(old.getBoolean("saveMediaInPrivateChannels",true));
            NaConfig.INSTANCE.getSaveMediaInPrivateGroups().setConfigBool(old.getBoolean("saveMediaInPrivateGroups",true));
            NaConfig.INSTANCE.getSaveMediaInPublicChannels().setConfigBool(old.getBoolean("saveMediaInPublicChannels",false));
            NaConfig.INSTANCE.getSaveMediaInPublicGroups().setConfigBool(old.getBoolean("saveMediaInPublicGroups",false));
            NaConfig.INSTANCE.getSaveDeletedMessageForBot().setConfigBool(old.getBoolean("saveForBots",true));
        }
        NaConfig.INSTANCE.getAutoUpdateChannel().setConfigInt(0);
        marker.edit().putBoolean("modern-settings-v1",true).apply();
    }
}
