package com.hedefit.app;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;
import com.hedefit.app.localai.HedefitLocalAiPlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Cihaz üstü AI köprüsü. Eklenti npm paketi değil, uygulamanın kendi
        // kaynağı olduğu için capacitor.plugins.json'a girmez; burada elle
        // kaydedilir (Capacitor'ın yerel eklenti yöntemi).
        registerPlugin(HedefitLocalAiPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
