package com.merito.agynb;

import org.openide.modules.OnStart;

@OnStart
public class StartServer implements Runnable {
    @Override
    public void run() {
        AgyBridgeServer.getInstance().start();
    }
}
