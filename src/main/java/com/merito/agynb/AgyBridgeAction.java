package com.merito.agynb;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

@ActionID(
    category = "Tools",
    id = "com.merito.agynb.AgyBridgeAction"
)
@ActionRegistration(
    displayName = "#CTL_AgyBridgeAction"
)
@ActionReference(path = "Menu/Tools", position = 1500)
@Messages("CTL_AgyBridgeAction=Status do Antigravity Bridge")
public final class AgyBridgeAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        boolean running = AgyBridgeServer.getInstance().isRunning();
        if (!running) {
            AgyBridgeServer.getInstance().start();
            running = AgyBridgeServer.getInstance().isRunning();
        }

        String msg = running
            ? "Antigravity NetBeans Bridge está ATIVO na porta " + AgyBridgeServer.getInstance().getPort() + ".\n\nEdições enviadas pelo Antigravity serão aplicadas diretamente na memória do editor, preservando o histórico local (* não salvo) e o encoding do projeto."
            : "Antigravity NetBeans Bridge está INATIVO.";

        NotifyDescriptor nd = new NotifyDescriptor.Message(msg, NotifyDescriptor.INFORMATION_MESSAGE);
        DialogDisplayer.getDefault().notify(nd);
    }
}
