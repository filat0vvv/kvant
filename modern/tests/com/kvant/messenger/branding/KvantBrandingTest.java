package com.kvant.messenger.branding;

import org.junit.Test;
import org.telegram.messenger.R;
import static org.junit.Assert.*;

public class KvantBrandingTest {
    @Test public void resourceIdCallsAndCloudKeysUseTheSameOwnBrand() {
        assertEquals("kvantGram",KvantBranding.get(null,R.string.AppName,"ru"));
        assertEquals("kvantGram",KvantBranding.get("NagramX",0,"en"));
        assertEquals("Очистить историю kvantGram",KvantBranding.get(null,R.string.ClearMessageDatabase,"ru_RU"));
        assertTrue(KvantBranding.get("BackupSettings",0,"ru").startsWith("KvantSync"));
        assertNull(KvantBranding.get("Send",0,"en"));
    }
}
