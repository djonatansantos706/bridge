package com.merito.agynb;

import org.openide.modules.OnStop;

@OnStop
public class StopServer implements Runnable {
    @Override
    public void run() {
        AgyBridgeServer.getInstance().stop();
    }
}
