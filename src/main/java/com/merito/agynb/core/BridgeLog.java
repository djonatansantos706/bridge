package com.merito.agynb.core;

import java.awt.Color;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.UIManager;
import org.openide.windows.IOColors;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;
import org.openide.windows.OutputWriter;
import org.openide.windows.WindowManager;

/**
 * Log de auditoria da bridge na janela de Output do NetBeans.
 *
 * Cada requisição atendida vira uma linha na aba "Antigravity Bridge"
 * (Window &gt; Output). A aba nunca é aberta nem selecionada
 * automaticamente — o desenvolvedor consulta quando quiser.
 *
 * <p>Toda operação de Output Window é postada na EDT via
 * {@code WindowManager.invokeWhenUIReady} para evitar repaints fora da
 * EDT que corrompem o fundo das outras abas. Além disso, as cores dark
 * são aplicadas explicitamente via UIManager e IOColors para garantir
 * o tema correto independente da ordem de inicialização dos módulos.</p>
 */
public final class BridgeLog {

    private static final Logger LOG = Logger.getLogger(BridgeLog.class.getName());
    private static final String TAB_NAME = "Antigravity Bridge";
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Indica se as cores dark já foram aplicadas ao UIManager. */
    private static volatile boolean darkApplied = false;

    private BridgeLog() {
    }

    /**
     * Registra uma requisição atendida.
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
     * Registra um evento de ciclo de vida.
     */
    public static void event(String message) {
        line(message, false);
    }

    /**
     * Posta a escrita na EDT via invokeWhenUIReady e aplica cores dark
     * explícitas para garantir tema correto independente de LAF/ordem de módulos.
     */
    private static void line(final String message, final boolean asError) {
        final String timestamp = "[" + LocalTime.now().format(HORA) + "] ";
        WindowManager.getDefault().invokeWhenUIReady(() -> {
            try {
                applyDarkColorsIfNeeded();
                InputOutput io = IOProvider.getDefault().getIO(TAB_NAME, false);
                applyDarkColorsToIO(io);
                OutputWriter writer = asError ? io.getErr() : io.getOut();
                writer.println(timestamp + message);
            } catch (Exception ex) {
                LOG.log(Level.FINE, "Falha ao escrever no Bridge Log", ex);
            }
        });
    }

    /**
     * Define as chaves UIManager usadas pelo Output Window para cores dark,
     * caso ainda não estejam num valor escuro. Executado na EDT.
     */
    private static void applyDarkColorsIfNeeded() {
        if (darkApplied) {
            return;
        }
        Color bg = UIManager.getColor("nb.output.background");
        // Se o fundo não está definido ou é claro (luminosidade > 128), força dark
        boolean isLight = bg == null
                || (bg.getRed() * 299 + bg.getGreen() * 587 + bg.getBlue() * 114) / 1000 > 128;
        if (isLight) {
            UIManager.put("nb.output.background",                  new Color(0x1E1E1E));
            UIManager.put("nb.output.foreground",                  new Color(0xABABAB));
            UIManager.put("nb.output.err.foreground",              new Color(0xFF6B6B));
            UIManager.put("nb.output.hyperlink.foreground",        new Color(0x6897BB));
            UIManager.put("nb.output.hyperlink-visited.foreground",new Color(0x9876AA));
            UIManager.put("nb.output.link.foreground",             new Color(0x6897BB));
        }
        darkApplied = true;
    }

    /**
     * Aplica cores dark diretamente no InputOutput via IOColors API.
     */
    private static void applyDarkColorsToIO(InputOutput io) {
        try {
            if (IOColors.isSupported(io)) {
                IOColors.setColor(io, IOColors.OutputType.OUTPUT,      new Color(0xABABAB));
                IOColors.setColor(io, IOColors.OutputType.ERROR,       new Color(0xFF6B6B));
                IOColors.setColor(io, IOColors.OutputType.INPUT,       new Color(0x6A8759));
                IOColors.setColor(io, IOColors.OutputType.LOG_DEBUG,   new Color(0x808080));
                IOColors.setColor(io, IOColors.OutputType.LOG_WARNING, new Color(0xBBB529));
                IOColors.setColor(io, IOColors.OutputType.LOG_FAILURE, new Color(0xFF6B6B));
                IOColors.setColor(io, IOColors.OutputType.LOG_SUCCESS, new Color(0x6A8759));
            }
        } catch (Exception ex) {
            LOG.log(Level.FINE, "IOColors não suportado", ex);
        }
    }
}
