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
import org.netbeans.api.debugger.DebuggerManagerAdapter;
import org.netbeans.api.debugger.Session;
import org.netbeans.api.debugger.Watch;
import org.netbeans.api.debugger.jpda.CallStackFrame;
import org.netbeans.api.debugger.jpda.ExceptionBreakpoint;
import org.netbeans.api.debugger.jpda.Field;
import org.netbeans.api.debugger.jpda.JPDAArrayType;
import org.netbeans.api.debugger.jpda.JPDABreakpoint;
import org.netbeans.api.debugger.jpda.JPDAClassType;
import org.netbeans.api.debugger.jpda.JPDADebugger;
import org.netbeans.api.debugger.jpda.JPDAThread;
import org.netbeans.api.debugger.jpda.LineBreakpoint;
import org.netbeans.api.debugger.jpda.LocalVariable;
import org.netbeans.api.debugger.jpda.ObjectVariable;
import org.netbeans.api.debugger.jpda.This;
import org.netbeans.api.debugger.jpda.ThreadsCollector;
import org.netbeans.api.debugger.jpda.Variable;
import org.netbeans.api.debugger.jpda.event.JPDABreakpointEvent;
import org.netbeans.api.debugger.jpda.event.JPDABreakpointListener;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

public class NbDebugService {

    private static final Logger LOG = Logger.getLogger(NbDebugService.class.getName());
    private static final NbDebugService INSTANCE = new NbDebugService();

    private volatile Map<String, Object> lastException = null;

    public static NbDebugService getInstance() {
        return INSTANCE;
    }

    private NbDebugService() {
        initDebuggerListeners();
    }

    private void initDebuggerListeners() {
        try {
            DebuggerManager.getDebuggerManager().addDebuggerListener(new DebuggerManagerAdapter() {
                @Override
                public void breakpointAdded(Breakpoint bp) {
                    attachBreakpointListener(bp);
                }
            });
            for (Breakpoint bp : DebuggerManager.getDebuggerManager().getBreakpoints()) {
                attachBreakpointListener(bp);
            }
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Falha ao registrar listeners de depuração", ex);
        }
    }

    private void attachBreakpointListener(Breakpoint bp) {
        if (bp instanceof JPDABreakpoint) {
            ((JPDABreakpoint) bp).addJPDABreakpointListener(new JPDABreakpointListener() {
                @Override
                public void breakpointReached(JPDABreakpointEvent event) {
                    handleBreakpointReached(event);
                }
            });
        }
    }

    private void handleBreakpointReached(JPDABreakpointEvent event) {
        try {
            JPDABreakpoint bp = (JPDABreakpoint) event.getSource();
            boolean isExceptionBp = (bp instanceof ExceptionBreakpoint);
            Variable var = event.getVariable();
            if (isExceptionBp || var != null) {
                recordException(var, event.getThread(), isExceptionBp ? ((ExceptionBreakpoint) bp).getExceptionClassName() : null);
            }
        } catch (Exception ex) {
            LOG.log(Level.FINE, "Erro ao registrar exceção no breakpoint listener", ex);
        }
    }

    public void recordException(Variable var, JPDAThread thread, String hintClassName) {
        Map<String, Object> exMap = new HashMap<>();
        exMap.put("hasException", true);
        exMap.put("timestamp", System.currentTimeMillis());

        String exClass = hintClassName;
        String message = null;
        String toStringVal = null;

        if (var != null) {
            if (exClass == null || exClass.isEmpty()) {
                exClass = var.getType();
            }
            message = var.getValue();
            if (var instanceof ObjectVariable) {
                ObjectVariable ov = (ObjectVariable) var;
                try {
                    toStringVal = ov.getToStringValue();
                } catch (Exception ignored) {
                }
                try {
                    Field msgField = ov.getField("detailMessage");
                    if (msgField != null && msgField.getValue() != null) {
                        message = msgField.getValue();
                    }
                } catch (Exception ignored) {
                }
            }
        }

        exMap.put("exceptionClass", exClass != null ? exClass : "java.lang.Throwable");
        exMap.put("message", message != null ? message : (toStringVal != null ? toStringVal : ""));
        if (toStringVal != null) {
            exMap.put("toString", toStringVal);
        }

        if (thread != null) {
            exMap.put("threadName", thread.getName());
            List<Map<String, Object>> stackList = new ArrayList<>();
            try {
                CallStackFrame[] frames = thread.getCallStack();
                int idx = 0;
                for (CallStackFrame f : frames) {
                    Map<String, Object> fm = new HashMap<>();
                    fm.put("index", idx++);
                    fm.put("className", f.getClassName());
                    fm.put("methodName", f.getMethodName());
                    try {
                        fm.put("sourceName", f.getSourceName(null));
                    } catch (Exception ignored) {
                    }
                    try {
                        fm.put("sourcePath", f.getSourcePath(null));
                    } catch (Exception ignored) {
                    }
                    fm.put("lineNumber", f.getLineNumber(null));
                    stackList.add(fm);
                }
            } catch (Exception ex) {
                exMap.put("stackError", ex.getMessage());
            }
            exMap.put("stackTrace", stackList);
        }

        this.lastException = exMap;
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
            map.put("watchesCount", DebuggerManager.getDebuggerManager().getWatches().length);
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
        map.put("watchesCount", DebuggerManager.getDebuggerManager().getWatches().length);
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

            if (v instanceof ObjectVariable) {
                ObjectVariable ov = (ObjectVariable) v;
                try {
                    String strVal = ov.getToStringValue();
                    if (strVal != null && !strVal.equals(value)) {
                        vm.put("toString", strVal);
                    }
                } catch (Exception ignored) {
                }

                if (currentDepth < maxDepth) {
                    if (!visited.add(ov)) {
                        vm.put("circularReference", true);
                        return vm;
                    }

                    boolean isArray = (type != null && type.endsWith("[]"));
                    JPDAClassType classType = null;
                    try {
                        classType = ov.getClassType();
                    } catch (Exception ignored) {
                    }

                    // 1. Formatação de Arrays
                    if (isArray) {
                        vm.put("collectionType", "ARRAY");
                        int count = ov.getFieldsCount();
                        vm.put("length", count);
                        List<Map<String, Object>> itemsList = new ArrayList<>();
                        try {
                            Field[] fields = ov.getFields(0, Math.min(count, 50));
                            if (fields != null) {
                                for (int i = 0; i < fields.length; i++) {
                                    itemsList.add(serializeVariable("[" + i + "]", fields[i], currentDepth + 1, maxDepth, visited));
                                }
                            }
                        } catch (Exception ex) {
                            vm.put("itemError", ex.getMessage());
                        }
                        vm.put("items", itemsList);
                        return vm;
                    }

                    // 2. Formatação de Listas e Coleções (java.util.List, java.util.Set, java.util.Collection)
                    boolean isList = false;
                    boolean isSet = false;
                    boolean isCollection = false;
                    if (classType != null) {
                        try {
                            isList = classType.isInstanceOf("java.util.List");
                            isSet = !isList && classType.isInstanceOf("java.util.Set");
                            isCollection = isList || isSet || classType.isInstanceOf("java.util.Collection");
                        } catch (Exception ignored) {
                        }
                    }
                    if (!isCollection && type != null) {
                        if (type.contains("List")) {
                            isList = true;
                            isCollection = true;
                        } else if (type.contains("Set")) {
                            isSet = true;
                            isCollection = true;
                        }
                    }

                    if (isCollection) {
                        vm.put("collectionType", isList ? "LIST" : (isSet ? "SET" : "COLLECTION"));
                        boolean handled = false;
                        try {
                            Variable sizeVar = ov.invokeMethod("size", "()I", new Variable[0]);
                            int size = 0;
                            if (sizeVar != null && sizeVar.getValue() != null) {
                                try {
                                    size = Integer.parseInt(sizeVar.getValue());
                                } catch (NumberFormatException ignored) {
                                }
                            }
                            vm.put("size", size);

                            Variable arrayVar = ov.invokeMethod("toArray", "()[Ljava/lang/Object;", new Variable[0]);
                            if (arrayVar instanceof ObjectVariable) {
                                ObjectVariable arrOv = (ObjectVariable) arrayVar;
                                int arrLen = arrOv.getFieldsCount();
                                int fetchLimit = Math.min(arrLen, Math.min(size > 0 ? size : 50, 50));
                                Field[] items = arrOv.getFields(0, fetchLimit);
                                List<Map<String, Object>> itemsList = new ArrayList<>();
                                if (items != null) {
                                    for (int i = 0; i < items.length; i++) {
                                        itemsList.add(serializeVariable("[" + i + "]", items[i], currentDepth + 1, maxDepth, visited));
                                    }
                                }
                                vm.put("items", itemsList);
                                handled = true;
                            }
                        } catch (Exception ex) {
                            LOG.log(Level.FINE, "Falha ao invocar toArray na coleção", ex);
                        }

                        if (handled) {
                            return vm;
                        }
                    }

                    // 3. Formatação de Mapas (java.util.Map)
                    boolean isMap = false;
                    if (classType != null) {
                        try {
                            isMap = classType.isInstanceOf("java.util.Map");
                        } catch (Exception ignored) {
                        }
                    }
                    if (!isMap && type != null && type.contains("Map")) {
                        isMap = true;
                    }

                    if (isMap) {
                        vm.put("collectionType", "MAP");
                        boolean handled = false;
                        try {
                            Variable sizeVar = ov.invokeMethod("size", "()I", new Variable[0]);
                            int size = 0;
                            if (sizeVar != null && sizeVar.getValue() != null) {
                                try {
                                    size = Integer.parseInt(sizeVar.getValue());
                                } catch (NumberFormatException ignored) {
                                }
                            }
                            vm.put("size", size);

                            Variable entrySetVar = ov.invokeMethod("entrySet", "()Ljava/util/Set;", new Variable[0]);
                            if (entrySetVar instanceof ObjectVariable) {
                                Variable arrayVar = ((ObjectVariable) entrySetVar).invokeMethod("toArray", "()[Ljava/lang/Object;", new Variable[0]);
                                if (arrayVar instanceof ObjectVariable) {
                                    ObjectVariable arrOv = (ObjectVariable) arrayVar;
                                    int arrLen = arrOv.getFieldsCount();
                                    int fetchLimit = Math.min(arrLen, Math.min(size > 0 ? size : 50, 50));
                                    Field[] entries = arrOv.getFields(0, fetchLimit);
                                    List<Map<String, Object>> entriesList = new ArrayList<>();
                                    if (entries != null) {
                                        for (int i = 0; i < entries.length; i++) {
                                            Field entryField = entries[i];
                                            Map<String, Object> entryMap = new HashMap<>();
                                            entryMap.put("index", i);
                                            if (entryField instanceof ObjectVariable) {
                                                ObjectVariable entryOv = (ObjectVariable) entryField;
                                                try {
                                                    Variable keyVar = entryOv.invokeMethod("getKey", "()Ljava/lang/Object;", new Variable[0]);
                                                    entryMap.put("key", serializeVariable("key", keyVar, currentDepth + 1, maxDepth, visited));
                                                } catch (Exception e) {
                                                    entryMap.put("key", entryOv.getValue());
                                                }
                                                try {
                                                    Variable valVar = entryOv.invokeMethod("getValue", "()Ljava/lang/Object;", new Variable[0]);
                                                    entryMap.put("value", serializeVariable("value", valVar, currentDepth + 1, maxDepth, visited));
                                                } catch (Exception e) {
                                                    entryMap.put("value", entryOv.getValue());
                                                }
                                            } else {
                                                entryMap.put("entry", serializeVariable("[" + i + "]", entryField, currentDepth + 1, maxDepth, visited));
                                            }
                                            entriesList.add(entryMap);
                                        }
                                    }
                                    vm.put("entries", entriesList);
                                    handled = true;
                                }
                            }
                        } catch (Exception ex) {
                            LOG.log(Level.FINE, "Falha ao formatar mapa", ex);
                        }

                        if (handled) {
                            return vm;
                        }
                    }

                    // 4. Objetos padrão: campos de instância
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

    // --- Gerenciamento de Watches (TASK-09) ---

    public Map<String, Object> addWatch(String expression) throws Exception {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("Expressão da watch não pode ser vazia.");
        }
        String expr = expression.trim();
        Watch watch = DebuggerManager.getDebuggerManager().createWatch(expr);

        Map<String, Object> res = new HashMap<>();
        res.put("ok", true);
        res.put("id", Integer.toHexString(watch.hashCode()));
        res.put("expression", watch.getExpression());
        res.put("enabled", watch.isEnabled());

        JPDADebugger debugger = getCurrentDebugger();
        if (debugger != null && debugger.getState() == JPDADebugger.STATE_STOPPED) {
            try {
                Variable v = debugger.evaluate(expr);
                if (v != null) {
                    res.put("type", v.getType());
                    res.put("value", v.getValue());
                    if (v instanceof ObjectVariable) {
                        try {
                            res.put("toString", ((ObjectVariable) v).getToStringValue());
                        } catch (Exception ignored) {
                        }
                    }
                } else {
                    res.put("type", "void");
                    res.put("value", "null");
                }
            } catch (Exception ex) {
                res.put("type", "error");
                res.put("value", ex.getMessage());
                res.put("error", ex.getMessage());
            }
        }
        return res;
    }

    public List<Map<String, Object>> listWatches() {
        Watch[] watches = DebuggerManager.getDebuggerManager().getWatches();
        List<Map<String, Object>> list = new ArrayList<>();
        JPDADebugger debugger = getCurrentDebugger();
        boolean canEvaluate = (debugger != null && debugger.getState() == JPDADebugger.STATE_STOPPED);

        for (Watch w : watches) {
            Map<String, Object> m = new HashMap<>();
            String id = Integer.toHexString(w.hashCode());
            String expr = w.getExpression();
            m.put("id", id);
            m.put("expression", expr);
            m.put("enabled", w.isEnabled());

            if (canEvaluate && expr != null && !expr.trim().isEmpty()) {
                try {
                    Variable v = debugger.evaluate(expr);
                    if (v != null) {
                        m.put("type", v.getType());
                        m.put("value", v.getValue());
                        if (v instanceof ObjectVariable) {
                            try {
                                m.put("toString", ((ObjectVariable) v).getToStringValue());
                            } catch (Exception ignored) {
                            }
                        }
                    } else {
                        m.put("type", "void");
                        m.put("value", "null");
                    }
                } catch (Exception ex) {
                    m.put("type", "error");
                    m.put("value", ex.getMessage());
                    m.put("error", ex.getMessage());
                }
            } else {
                if (debugger == null) {
                    m.put("type", "N/A");
                    m.put("value", "Debugger inativo");
                } else if (debugger.getState() != JPDADebugger.STATE_STOPPED) {
                    m.put("type", "N/A");
                    m.put("value", "Debugger em execução (não suspenso)");
                } else {
                    m.put("type", "N/A");
                    m.put("value", "Não avaliado");
                }
            }
            list.add(m);
        }
        return list;
    }

    public Map<String, Object> removeWatch(String expressionOrId) throws Exception {
        if (expressionOrId == null || expressionOrId.trim().isEmpty()) {
            throw new IllegalArgumentException("Identificador ou expressão da watch é obrigatório.");
        }
        String target = expressionOrId.trim();
        Watch[] watches = DebuggerManager.getDebuggerManager().getWatches();
        List<Watch> toRemove = new ArrayList<>();

        for (Watch w : watches) {
            String id = Integer.toHexString(w.hashCode());
            if (target.equalsIgnoreCase(id) || target.equals(w.getExpression()) || target.equalsIgnoreCase("all")) {
                toRemove.add(w);
            }
        }

        if (toRemove.isEmpty()) {
            Map<String, Object> res = new HashMap<>();
            res.put("ok", false);
            res.put("error", "Nenhuma watch correspondente encontrada para: " + expressionOrId);
            return res;
        }

        for (Watch w : toRemove) {
            w.remove();
        }

        Map<String, Object> res = new HashMap<>();
        res.put("ok", true);
        res.put("removedCount", toRemove.size());
        res.put("message", toRemove.size() + " watch(es) removida(s).");
        return res;
    }

    // --- Captura de Exceção JPDA (TASK-10) ---

    public Map<String, Object> getLastException() {
        if (lastException != null) {
            Map<String, Object> res = new HashMap<>(lastException);
            res.put("ok", true);
            return res;
        }

        JPDADebugger debugger = getCurrentDebugger();
        if (debugger != null) {
            JPDAThread thread = debugger.getCurrentThread();
            if (thread != null && thread.isSuspended()) {
                JPDABreakpoint bp = thread.getCurrentBreakpoint();
                if (bp instanceof ExceptionBreakpoint) {
                    ExceptionBreakpoint ebp = (ExceptionBreakpoint) bp;
                    Map<String, Object> res = new HashMap<>();
                    res.put("ok", true);
                    res.put("hasException", true);
                    res.put("exceptionClass", ebp.getExceptionClassName());
                    res.put("threadName", thread.getName());
                    res.put("message", "Interrompido por ExceptionBreakpoint: " + ebp.getExceptionClassName());
                    List<Map<String, Object>> stackList = new ArrayList<>();
                    try {
                        CallStackFrame[] frames = thread.getCallStack();
                        int idx = 0;
                        for (CallStackFrame f : frames) {
                            Map<String, Object> fm = new HashMap<>();
                            fm.put("index", idx++);
                            fm.put("className", f.getClassName());
                            fm.put("methodName", f.getMethodName());
                            try {
                                fm.put("sourceName", f.getSourceName(null));
                            } catch (Exception ignored) {
                            }
                            try {
                                fm.put("sourcePath", f.getSourcePath(null));
                            } catch (Exception ignored) {
                            }
                            fm.put("lineNumber", f.getLineNumber(null));
                            stackList.add(fm);
                        }
                    } catch (Exception ignored) {
                    }
                    res.put("stackTrace", stackList);
                    return res;
                }
            }
        }

        Map<String, Object> res = new HashMap<>();
        res.put("ok", true);
        res.put("hasException", false);
        res.put("message", "Nenhuma exceção capturada recentemente.");
        return res;
    }
}
