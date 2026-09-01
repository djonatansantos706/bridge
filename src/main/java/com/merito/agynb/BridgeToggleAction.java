package com.merito.agynb;

import com.merito.agynb.core.BridgeConstants;
import com.merito.agynb.core.BridgeNotifier;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

/**
 * Kill switch da bridge: pausa ou retoma o servidor HTTP com um clique,
 * sem precisar desinstalar o plugin nem fechar o NetBeans. Com a bridge
 * pausada, nenhum agente de IA consegue ler ou alterar nada na IDE.
 */
@ActionID(
    category = "Tools",
    id = "com.merito.agynb.BridgeToggleAction"
)
@ActionRegistration(
    displayName = "#CTL_BridgeToggleAction"
)
@ActionReference(path = "Menu/Tools", position = 1510)
@Messages("CTL_BridgeToggleAction=Pausar/Retomar Antigravity Bridge")
public final class BridgeToggleAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        AgyBridgeServer server = AgyBridgeServer.getInstance();
        if (server.isRunning()) {
            server.stop();
            BridgeNotifier.lifecycle("Bridge PAUSADA",
                    "Agentes de IA não conseguem mais acessar a IDE. Retome em Tools > Pausar/Retomar Antigravity Bridge.");
        } else {
            server.start();
            if (server.isRunning()) {
                BridgeNotifier.lifecycle("Bridge retomada",
                        "Ativa novamente na porta " + BridgeConstants.DEFAULT_PORT + ".");
            }
        }
    }
}
