package com.merito.agynb;

import org.openide.modules.OnStart;
import org.openide.windows.WindowManager;

@OnStart
public class StartServer implements Runnable {
    @Override
    public void run() {
        // Aguarda a UI estar completamente pronta (LAF dark aplicado)
        // antes de iniciar o servidor, evitando que a aba "Antigravity Bridge"
        // no Output Window seja criada com o tema padrão/incorreto.
        WindowManager.getDefault().invokeWhenUIReady(() -> {
            AgyBridgeServer.getInstance().start();
        });
    }
}
