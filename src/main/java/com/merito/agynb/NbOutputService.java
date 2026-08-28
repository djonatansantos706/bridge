package com.merito.agynb;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.openide.util.Lookup;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

public class NbOutputService {

    private static final Logger LOG = Logger.getLogger(NbOutputService.class.getName());
    private static final NbOutputService INSTANCE = new NbOutputService();

    public static NbOutputService getInstance() {
        return INSTANCE;
    }

    private NbOutputService() {
    }

    public List<Map<String, Object>> listTabs() {
        List<Map<String, Object>> tabs = new ArrayList<>();

        // Método 1: Introspecção via Controller do NetBeans Output2
        try {
            ClassLoader cl = Lookup.getDefault().lookup(ClassLoader.class);
            if (cl == null) cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = NbOutputService.class.getClassLoader();

            Class<?> controllerClass = cl.loadClass("org.netbeans.core.output2.Controller");
            Method getDefaultMethod = controllerClass.getMethod("getDefault");
            Object controller = getDefaultMethod.invoke(null);

            if (controller != null) {
                try {
                    Method getRecentMethod = controllerClass.getMethod("getRecent");
                    Object[] recent = (Object[]) getRecentMethod.invoke(controller);
                    if (recent != null) {
                        for (Object nbio : recent) {
                            if (nbio != null) {
                                Method getNameMethod = nbio.getClass().getMethod("getName");
                                String name = (String) getNameMethod.invoke(nbio);
                                Map<String, Object> tabInfo = new HashMap<>();
                                tabInfo.put("name", name);
                                tabInfo.put("type", "NbIO");
                                tabs.add(tabInfo);
                            }
                        }
                    }
                } catch (Throwable t) {
                    LOG.log(Level.FINE, "getRecent não disponível no Controller", t);
                }
            }
        } catch (Throwable t) {
            LOG.log(Level.FINE, "Controller output2 não acessível por reflexão direta", t);
        }

        // Método 2: Inspeção de TopComponents abertos no WindowManager
        try {
            for (TopComponent tc : WindowManager.getDefault().getRegistry().getOpened()) {
                String tcId = tc.getClass().getName();
                String displayName = tc.getDisplayName();
                if (tcId.contains("output") || tcId.contains("Output") || "output".equalsIgnoreCase(tc.getName())) {
                    boolean alreadyExists = false;
                    for (Map<String, Object> m : tabs) {
                        if (displayName != null && displayName.equals(m.get("name"))) {
                            alreadyExists = true;
                            break;
                        }
                    }
                    if (!alreadyExists) {
                        Map<String, Object> tabInfo = new HashMap<>();
                        tabInfo.put("name", displayName != null ? displayName : tc.getName());
                        tabInfo.put("componentClass", tcId);
                        tabs.add(tabInfo);
                    }
                }
            }
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "Erro ao listar TopComponents de Output", t);
        }

        return tabs;
    }

    public Map<String, Object> getTabLines(String tabName, int sinceLine, int maxLines) throws Exception {
        int limit = (maxLines > 0) ? maxLines : 500;
        int start = Math.max(0, sinceLine);

        // Tentar ler via Storage / NbIO do org.netbeans.core.output2
        try {
            ClassLoader cl = Lookup.getDefault().lookup(ClassLoader.class);
            if (cl == null) cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = NbOutputService.class.getClassLoader();

            Class<?> controllerClass = cl.loadClass("org.netbeans.core.output2.Controller");
            Method getDefaultMethod = controllerClass.getMethod("getDefault");
            Object controller = getDefaultMethod.invoke(null);

            if (controller != null) {
                Method getRecentMethod = controllerClass.getMethod("getRecent");
                Object[] recent = (Object[]) getRecentMethod.invoke(controller);
                if (recent != null) {
                    for (Object nbio : recent) {
                        if (nbio != null) {
                            Method getNameMethod = nbio.getClass().getMethod("getName");
                            String name = (String) getNameMethod.invoke(nbio);
                            if (tabName == null || tabName.isEmpty() || tabName.equalsIgnoreCase(name)) {
                                return extractLinesFromNbIO(nbio, start, limit);
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOG.log(Level.FINE, "Falha na extração via NbIO", t);
        }

        // Fallback: Procurar em TopComponents abertos
        for (TopComponent tc : WindowManager.getDefault().getRegistry().getOpened()) {
            String displayName = tc.getDisplayName();
            if (tabName == null || tabName.isEmpty() || (displayName != null && displayName.equalsIgnoreCase(tabName)) || tc.getClass().getName().contains("output")) {
                JTextComponent textComp = findTextComponent(tc);
                if (textComp != null) {
                    Document doc = textComp.getDocument();
                    String fullText = doc.getText(0, doc.getLength());
                    String[] allLines = fullText.split("\\r?\\n");
                    List<String> resultLines = new ArrayList<>();
                    int total = allLines.length;
                    for (int i = start; i < total && resultLines.size() < limit; i++) {
                        resultLines.add(allLines[i]);
                    }
                    Map<String, Object> res = new HashMap<>();
                    res.put("ok", true);
                    res.put("tab", displayName != null ? displayName : tc.getName());
                    res.put("totalLines", total);
                    res.put("startLine", start);
                    res.put("returnedLines", resultLines.size());
                    res.put("hasMore", (start + resultLines.size()) < total);
                    res.put("lines", resultLines);
                    return res;
                }
            }
        }

        Map<String, Object> res = new HashMap<>();
        res.put("ok", false);
        res.put("error", "Aba de saída não encontrada: " + tabName);
        return res;
    }

    private Map<String, Object> extractLinesFromNbIO(Object nbio, int startLine, int limit) throws Exception {
        Method getOutMethod = nbio.getClass().getMethod("getOut");
        Object out = getOutMethod.invoke(nbio);
        Method getStorageMethod = out.getClass().getMethod("getStorage");
        Object storage = getStorageMethod.invoke(out);

        Method getLineCountMethod = storage.getClass().getMethod("getLineCount");
        int lineCount = (Integer) getLineCountMethod.invoke(storage);

        Method getLineMethod = storage.getClass().getMethod("getLine", int.class);
        List<String> lines = new ArrayList<>();

        for (int i = startLine; i < lineCount && lines.size() < limit; i++) {
            Object lineObj = getLineMethod.invoke(storage, i);
            lines.add(lineObj != null ? lineObj.toString() : "");
        }

        Method getNameMethod = nbio.getClass().getMethod("getName");
        String name = (String) getNameMethod.invoke(nbio);

        Map<String, Object> res = new HashMap<>();
        res.put("ok", true);
        res.put("tab", name);
        res.put("totalLines", lineCount);
        res.put("startLine", startLine);
        res.put("returnedLines", lines.size());
        res.put("hasMore", (startLine + lines.size()) < lineCount);
        res.put("lines", lines);
        return res;
    }

    public Map<String, Object> clearTab(String tabName) throws Exception {
        if (tabName != null && !tabName.isEmpty()) {
            InputOutput io = IOProvider.getDefault().getIO(tabName, false);
            if (io != null) {
                io.getOut().reset();
                Map<String, Object> res = new HashMap<>();
                res.put("ok", true);
                res.put("message", "Aba de saída \"" + tabName + "\" limpa.");
                return res;
            }
        }
        Map<String, Object> res = new HashMap<>();
        res.put("ok", false);
        res.put("error", "Aba de saída não encontrada para limpeza: " + tabName);
        return res;
    }

    private JTextComponent findTextComponent(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof JTextComponent) {
                return (JTextComponent) c;
            }
            if (c instanceof Container) {
                JTextComponent found = findTextComponent((Container) c);
                if (found != null) return found;
            }
        }
        return null;
    }
}
