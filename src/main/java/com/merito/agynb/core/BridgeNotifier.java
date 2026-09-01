package com.merito.agynb.core;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Icon;
import org.openide.awt.NotificationDisplayer;
import org.openide.util.ImageUtilities;

/**
 * Notificações persistentes da bridge no sininho do NetBeans.
 *
 * O {@code StatusDisplayer} (rodapé) some em segundos; se o desenvolvedor
 * estiver olhando outra janela, a ação do agente passa despercebida. As
 * notificações ficam registradas na área de notificações até serem
 * dispensadas, com prioridade calibrada para não virar ruído:
 *
 * - alterações de buffer (não salvas, reversíveis com Ctrl+Z) usam
 *   {@code SILENT}: entram no histórico do sininho sem balão pop-up;
 * - operações que tocam o disco ou descartam trabalho (save, revert) e o
 *   pausar/retomar manual usam {@code NORMAL}: mostram o balão.
 */
public final class BridgeNotifier {

    private static final Logger LOG = Logger.getLogger(BridgeNotifier.class.getName());
    private static final Icon ICON = ImageUtilities.loadImageIcon("com/merito/agynb/bridge16.png", false);

    private BridgeNotifier() {
    }

    /**
     * Alteração em memória (buffer não salvo): registro silencioso no sininho.
     */
    public static void bufferChanged(String fileName, String details) {
        notify("Bridge: buffer alterado — " + fileName, details, NotificationDisplayer.Priority.SILENT);
    }

    /**
     * Operação que tocou o disco ou descartou alterações: balão + registro.
     */
    public static void diskChanged(String title, String details) {
        notify(title, details, NotificationDisplayer.Priority.NORMAL);
    }

    /**
     * Evento de ciclo de vida disparado pelo desenvolvedor (pausar/retomar).
     */
    public static void lifecycle(String title, String details) {
        notify(title, details, NotificationDisplayer.Priority.NORMAL);
    }

    private static void notify(String title, String details, NotificationDisplayer.Priority priority) {
        try {
            NotificationDisplayer.getDefault().notify(title, ICON, details, null, priority);
        } catch (Exception ex) {
            // Notificação nunca pode derrubar a operação que a originou
            LOG.log(Level.FINE, "Falha ao publicar notificação da bridge", ex);
        }
    }
}
