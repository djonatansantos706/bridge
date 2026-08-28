package com.merito.agynb;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.debugger.ActionsManager;
import org.netbeans.api.debugger.Breakpoint;
import org.netbeans.api.debugger.DebuggerEngine;
import org.netbeans.api.debugger.DebuggerManager;
import org.netbeans.api.debugger.Session;
import org.netbeans.api.debugger.jpda.CallStackFrame;
import org.netbeans.api.debugger.jpda.Field;
import org.netbeans.api.debugger.jpda.JPDABreakpoint;
import org.netbeans.api.debugger.jpda.JPDADebugger;
import org.netbeans.api.debugger.jpda.JPDAThread;
import org.netbeans.api.debugger.jpda.LineBreakpoint;
import org.netbeans.api.debugger.jpda.LocalVariable;
import org.netbeans.api.debugger.jpda.ObjectVariable;
import org.netbeans.api.debugger.jpda.This;
import org.netbeans.api.debugger.jpda.ThreadsCollector;
import org.netbeans.api.debugger.jpda.Variable;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

public class NbDebugService {

    private static final Logger LOG = Logger.getLogger(NbDebugService.class.getName());
    private static final NbDebugService INSTANCE = new NbDebugService();

    public static NbDebugService getInstance() {
        return INSTANCE;
    }

    private NbDebugService() {
    }

    public JPDADebugger getCurrentDebugger() {
        DebuggerEngine engine = DebuggerManager.getDebuggerManager().getCurrentEngine();
        if (engine != null) {
            JPDADebugger debugger = engine.lookupFirst(null, JPDADebugger.class);
            if (debugger != null) {
                return debugger;
            }
        }
        for (Session session : DebuggerManager.getDebuggerManager().getSessions()) {
            DebuggerEngine eng = session.getCurrentEngine();
            if (eng != null) {
                JPDADebugger dbg = eng.lookupFirst(null, JPDADebugger.class);
                if (dbg != null) {
                    return dbg;
                }
            }
        }
        return null;
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> map = new HashMap<>();
        JPDADebugger debugger = getCurrentDebugger();
        boolean active = (debugger != null);
        map.put("active", active);

        if (!active) {
            map.put("state", "INACTIVE");
            map.put("breakpointsCount", DebuggerManager.getDebuggerManager().getBreakpoints().length);
            return map;
        }

        int state = debugger.getState();
        String stateStr = "UNKNOWN";
        switch (state) {
            case JPDADebugger.STATE_RUNNING:
                stateStr = "RUNNING";
                break;
            case JPDADebugger.STATE_STOPPED:
                stateStr = "STOPPED";
                break;
            case JPDADebugger.STATE_DISCONNECTED:
                stateStr = "DISCONNECTED";
                break;
            case JPDADebugger.STATE_STARTING:
                stateStr = "STARTING";
                break;
        }
        map.put("state", stateStr);

        JPDAThread currentThread = debugger.getCurrentThread();
        if (currentThread != null) {
            map.put("currentThread", currentThread.getName());
            map.put("isSuspended", currentThread.isSuspended());
        } else {
            map.put("currentThread", null);
            map.put("isSuspended", false);
        }

        map.put("breakpointsCount", DebuggerManager.getDebuggerManager().getBreakpoints().length);
        return map;
    }

    public Map<String, Object> setBreakpoint(String filePath, int lineNumber, String condition) throws Exception {
        File file = new File(filePath).getCanonicalFile();
        if (!file.exists()) {
            throw new IllegalArgumentException("Arquivo não encontrado no disco: " + filePath);
        }
        FileObject fo = FileUtil.toFileObject(file);
        if (fo == null) {
            throw new IllegalArgumentException("FileObject não encontrado pelo NetBeans para: " + filePath);
        }

        URL url = fo.toURL();
        LineBreakpoint bp = LineBreakpoint.create(url.toExternalForm(), lineNumber);
        if (condition != null && !condition.trim().isEmpty()) {
            bp.setCondition(condition.trim());
        }

        DebuggerManager.getDebuggerManager().addBreakpoint(bp);

        Map<String, Object> res = new HashMap<>();
        res.put("ok", true);
        res.put("file", file.getAbsolutePath());
        res.put("line", lineNumber);
        res.put("condition", bp.getCondition());
        res.put("enabled", bp.isEnabled());
        res.put("validity", bp.getValidity().toString());
        res.put("breakpointId", Integer.toHexString(bp.hashCode()));
        return res;
    }

    public Map<String, Object> removeBreakpoint(String breakpointId, String filePath, Integer lineNumber) throws Exception {
        Breakpoint[] breakpoints = DebuggerManager.getDebuggerManager().getBreakpoints();
        List<Breakpoint> toRemove = new ArrayList<>();

        for (Breakpoint bp : breakpoints) {
            String id = Integer.toHexString(bp.hashCode());
            if (breakpointId != null && breakpointId.equalsIgnoreCase(id)) {
                toRemove.add(bp);
            } else if (bp instanceof LineBreakpoint && filePath != null && lineNumber != null) {
                LineBreakpoint lb = (LineBreakpoint) bp;
                String urlStr = lb.getURL();
                if (urlStr != null && (urlStr.contains(filePath) || urlStr.endsWith(new File(filePath).getName()))) {
                    if (lb.getLineNumber() == lineNumber) {
                        toRemove.add(bp);
                    }
                }
            }
        }

        if (toRemove.isEmpty()) {
            Map<String, Object> res = new HashMap<>();
            res.put("ok", false);
            res.put("error", "Nenhum breakpoint correspondente encontrado para remover.");
            return res;
        }

        for (Breakpoint bp : toRemove) {
            DebuggerManager.getDebuggerManager().removeBreakpoint(bp);
        }

        Map<String, Object> res = new HashMap<>();
        res.put("ok", true);
        res.put("removedCount", toRemove.size());
        res.put("message", toRemove.size() + " breakpoint(s) removido(s).");
        return res;
    }

    public List<Map<String, Object>> listBreakpoints() {
        Breakpoint[] breakpoints = DebuggerManager.getDebuggerManager().getBreakpoints();
        List<Map<String, Object>> list = new ArrayList<>();

        for (Breakpoint bp : breakpoints) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", Integer.toHexString(bp.hashCode()));
            m.put("enabled", bp.isEnabled());
            m.put("group", bp.getGroupName());

            if (bp instanceof LineBreakpoint) {
                LineBreakpoint lb = (LineBreakpoint) bp;
                m.put("type", "LINE");
                m.put("url", lb.getURL());
                m.put("line", lb.getLineNumber());
                m.put("condition", lb.getCondition());
                m.put("validity", lb.getValidity().toString());
            } else if (bp instanceof JPDABreakpoint) {
                m.put("type", "JPDA_OTHER");
                m.put("validity", ((JPDABreakpoint) bp).getValidity().toString());
            } else {
                m.put("type", bp.getClass().getSimpleName());
            }
            list.add(m);
        }
        return list;
    }

    public Map<String, Object> control(String action) throws Exception {
        JPDADebugger debugger = getCurrentDebugger();
        if (debugger == null) {
            throw new IllegalStateException("Nenhuma sessão ativa de depuração JPDA encontrada no NetBeans.");
        }

        DebuggerEngine engine = DebuggerManager.getDebuggerManager().getCurrentEngine();
        ActionsManager actionsManager = (engine != null) ? engine.getActionsManager() : DebuggerManager.getDebuggerManager().getActionsManager();

        String act = (action != null) ? action.trim().toLowerCase() : "";
        Map<String, Object> res = new HashMap<>();
        res.put("action", act);

        switch (act) {
            case "step_into":
            case "stepinto":
            case "into":
                actionsManager.doAction(ActionsManager.ACTION_STEP_INTO);
                res.put("ok", true);
                res.put("message", "Step Into disparado.");
                break;
            case "step_over":
            case "stepover":
            case "over":
                actionsManager.doAction(ActionsManager.ACTION_STEP_OVER);
                res.put("ok", true);
                res.put("message", "Step Over disparado.");
                break;
            case "step_out":
            case "stepout":
            case "out":
                actionsManager.doAction(ActionsManager.ACTION_STEP_OUT);
                res.put("ok", true);
                res.put("message", "Step Out disparado.");
                break;
            case "continue":
            case "resume":
                actionsManager.doAction(ActionsManager.ACTION_CONTINUE);
                res.put("ok", true);
                res.put("message", "Continue / Resume disparado.");
                break;
            case "pause":
            case "suspend":
                actionsManager.doAction(ActionsManager.ACTION_PAUSE);
                res.put("ok", true);
                res.put("message", "Pause disparado.");
                break;
            case "stop":
            case "kill":
            case "finish":
                actionsManager.doAction(ActionsManager.ACTION_KILL);
                res.put("ok", true);
                res.put("message", "Kill / Stop disparado.");
                break;
            default:
                res.put("ok", false);
                res.put("error", "Ação de controle desconhecida: '" + action + "'. Use: step_into, step_over, step_out, continue, pause, stop.");
                break;
        }

        return res;
    }

    public Map<String, Object> getCallStack(String threadName) throws Exception {
        JPDADebugger debugger = getCurrentDebugger();
        if (debugger == null) {
            throw new IllegalStateException("Nenhuma sessão ativa de depuração JPDA.");
        }

        JPDAThread targetThread = null;
        ThreadsCollector collector = debugger.getThreadsCollector();
        List<JPDAThread> allThreads = (collector != null) ? collector.getAllThreads() : new ArrayList<>();

        if (threadName != null && !threadName.trim().isEmpty()) {
            for (JPDAThread t : allThreads) {
                if (threadName.equalsIgnoreCase(t.getName())) {
                    targetThread = t;
                    break;
                }
            }
        }

        if (targetThread == null) {
            targetThread = debugger.getCurrentThread();
        }

        if (targetThread == null && !allThreads.isEmpty()) {
            targetThread = allThreads.get(0);
        }

        Map<String, Object> res = new HashMap<>();
        if (targetThread == null) {
            res.put("ok", false);
            res.put("error", "Nenhuma thread ativa encontrada.");
            return res;
        }

        res.put("ok", true);
        res.put("threadName", targetThread.getName());
        res.put("isSuspended", targetThread.isSuspended());

        List<Map<String, Object>> framesList = new ArrayList<>();
        try {
            CallStackFrame[] frames = targetThread.getCallStack();
            int idx = 0;
            for (CallStackFrame f : frames) {
                Map<String, Object> fm = new HashMap<>();
                fm.put("index", idx++);
                fm.put("methodName", f.getMethodName());
                fm.put("className", f.getClassName());
                try {
                    fm.put("sourceName", f.getSourceName(null));
                } catch (Exception ignored) {
                }
                try {
                    fm.put("sourcePath", f.getSourcePath(null));
                } catch (Exception ignored) {
                }
                fm.put("lineNumber", f.getLineNumber(null));
                fm.put("isObsolete", f.isObsolete());
                framesList.add(fm);
            }
        } catch (Exception ex) {
            res.put("frameError", ex.getMessage());
        }

        res.put("frames", framesList);
        res.put("framesCount", framesList.size());
        return res;
    }

    public Map<String, Object> getVariables(int frameIndex, int maxDepth) throws Exception {
        JPDADebugger debugger = getCurrentDebugger();
        if (debugger == null) {
            throw new IllegalStateException("Nenhuma sessão ativa de depuração JPDA.");
        }

        JPDAThread thread = debugger.getCurrentThread();
        if (thread == null) {
            throw new IllegalStateException("Nenhuma thread atual selecionada.");
        }

        CallStackFrame[] frames = thread.getCallStack();
        if (frameIndex < 0 || frameIndex >= frames.length) {
            throw new IllegalArgumentException("Índice de frame inválido: " + frameIndex + " (total de frames: " + frames.length + ")");
        }

        CallStackFrame frame = frames[frameIndex];
        Map<String, Object> res = new HashMap<>();
        res.put("ok", true);
        res.put("frameIndex", frameIndex);
        res.put("method", frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber(null));

        List<Map<String, Object>> varsList = new ArrayList<>();
        Set<Object> visited = new HashSet<>();

        try {
            LocalVariable[] locals = frame.getLocalVariables();
            if (locals != null) {
                for (LocalVariable lv : locals) {
                    varsList.add(serializeVariable(lv.getName(), lv, 0, Math.max(1, maxDepth), visited));
                }
            }
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Erro ao obter variáveis locais", ex);
        }

        try {
            This thisVar = frame.getThisVariable();
            if (thisVar != null) {
                varsList.add(serializeVariable("this", thisVar, 0, Math.max(1, maxDepth), visited));
            }
        } catch (Exception ex) {
            LOG.log(Level.FINE, "Não foi possível obter this", ex);
        }

        res.put("variables", varsList);
        return res;
    }

    private Map<String, Object> serializeVariable(String name, Variable v, int currentDepth, int maxDepth, Set<Object> visited) {
        Map<String, Object> vm = new HashMap<>();
        vm.put("name", name);
        if (v == null) {
            vm.put("type", "null");
            vm.put("value", "null");
            return vm;
        }

        try {
            String type = v.getType();
            String value = v.getValue();
            vm.put("type", type != null ? type : "unknown");
            vm.put("value", value != null ? value : "null");

            if (currentDepth < maxDepth && (v instanceof ObjectVariable)) {
                ObjectVariable ov = (ObjectVariable) v;
                if (!visited.add(ov)) {
                    vm.put("circularReference", true);
                    return vm;
                }

                List<Map<String, Object>> fieldsList = new ArrayList<>();
                try {
                    Field[] fields = ov.getFields(0, 50);
                    if (fields != null) {
                        for (Field f : fields) {
                            fieldsList.add(serializeVariable(f.getName(), f, currentDepth + 1, maxDepth, visited));
                        }
                    }
                } catch (Exception ignored) {
                }

                if (!fieldsList.isEmpty()) {
                    vm.put("fields", fieldsList);
                }
            }
        } catch (Exception ex) {
            vm.put("error", ex.getMessage());
        }

        return vm;
    }

    public Map<String, Object> evaluate(String expression, Integer frameIndex) throws Exception {
        JPDADebugger debugger = getCurrentDebugger();
        if (debugger == null) {
            throw new IllegalStateException("Nenhuma sessão ativa de depuração JPDA.");
        }

        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("Expressão de avaliação não informada.");
        }

        JPDAThread thread = debugger.getCurrentThread();
        if (thread != null && frameIndex != null) {
            CallStackFrame[] frames = thread.getCallStack();
            if (frameIndex >= 0 && frameIndex < frames.length) {
                frames[frameIndex].makeCurrent();
            }
        }

        Variable resultVar = debugger.evaluate(expression);

        Map<String, Object> res = new HashMap<>();
        res.put("ok", true);
        res.put("expression", expression);
        if (resultVar != null) {
            res.put("type", resultVar.getType());
            res.put("value", resultVar.getValue());
            if (resultVar instanceof ObjectVariable) {
                ObjectVariable ov = (ObjectVariable) resultVar;
                try {
                    res.put("toString", ov.getToStringValue());
                } catch (Exception ignored) {
                }
            }
        } else {
            res.put("type", "void");
            res.put("value", "null");
        }

        return res;
    }
}
