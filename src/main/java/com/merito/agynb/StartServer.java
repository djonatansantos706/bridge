package com.merito.agynb;

import org.openide.modules.OnStart;
import org.openide.util.RequestProcessor;
import org.openide.windows.WindowManager;

@OnStart
public class StartServer implements Runnable {

    @Override
    public void run() {
        // @OnStart já roda em background thread — correto.
        // Aguarda a UI estar completamente pronta (LAF dark aplicado) via EDT,
        // e em seguida devolve o start() para uma background thread via
        // RequestProcessor, evitando bloquear a EDT com I/O (socket + token)
        // e garantindo que a aba do BridgeLog herde o tema correto.
        WindowManager.getDefault().invokeWhenUIReady(() ->
            RequestProcessor.getDefault().post(() ->
                AgyBridgeServer.getInstance().start()
            )
        );
    }
}
