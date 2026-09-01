package com.merito.agynb.core;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;
import org.openide.windows.OutputWriter;

/**
 * Log de auditoria da bridge na janela de Output do NetBeans.
 *
 * Cada requisição atendida vira uma linha na aba "Antigravity Bridge"
 * (Window &gt; Output), respondendo à pergunta central de quem cede a IDE a
 * um agente de IA: "o que ele fez aqui?". A aba nunca é aberta nem
 * selecionada automaticamente — o desenvolvedor consulta quando quiser —
 * e, como é uma aba de output comum, o próprio agente pode lê-la via
 * {@code nb_output_get_text}.
 */
public final class BridgeLog {

    private static final Logger LOG = Logger.getLogger(BridgeLog.class.getName());
    private static final String TAB_NAME = "Antigravity Bridge";
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private BridgeLog() {
    }

    /**
     * Registra uma requisição atendida: endpoint, arquivo alvo (quando
     * houver), resultado e duração.
     */
    public static void request(String endpoint, String file, boolean ok, long elapsedMs) {
        StringBuilder sb = new StringBuilder();
        sb.append(ok ? "ok   " : "ERRO ").append(endpoint).append(" (").append(elapsedMs).append(" ms)");
        if (file != null && !file.trim().isEmpty()) {
            sb.append(" — ").append(file);
        }
        line(sb.toString(), !ok);
    }

    /**
     * Registra um evento de ciclo de vida (bridge iniciada, pausada, retomada).
     */
    public static void event(String message) {
        line(message, false);
    }

    private static synchronized void line(String message, boolean asError) {
        try {
            InputOutput io = IOProvider.getDefault().getIO(TAB_NAME, false);
            OutputWriter writer = asError ? io.getErr() : io.getOut();
            writer.println("[" + LocalTime.now().format(HORA) + "] " + message);
        } catch (Exception ex) {
            // O log de auditoria nunca pode derrubar uma requisição da bridge
            LOG.log(Level.FINE, "Falha ao escrever no Bridge Log", ex);
        }
    }
}
