package com.merito.agynb;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.Diagnostic;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.Task;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

public class NbDiagnosticsService {

    private static final Logger LOG = Logger.getLogger(NbDiagnosticsService.class.getName());
    private static final NbDiagnosticsService INSTANCE = new NbDiagnosticsService();

    public static NbDiagnosticsService getInstance() {
        return INSTANCE;
    }

    private NbDiagnosticsService() {
    }

    @SuppressWarnings("rawtypes")
    public Map<String, Object> getDiagnostics(String filePath) throws Exception {
        File file = new File(filePath).getCanonicalFile();
        if (!file.exists()) {
            throw new FileNotFoundException("Arquivo não encontrado: " + filePath);
        }
        FileObject fo = FileUtil.toFileObject(file);
        if (fo == null) {
            throw new FileNotFoundException("FileObject não encontrado pelo NetBeans para: " + filePath);
        }

        JavaSource js = JavaSource.forFileObject(fo);
        if (js == null) {
            Map<String, Object> res = new HashMap<>();
            res.put("ok", false);
            res.put("error", "Arquivo não é um arquivo fonte Java reconhecido pelo NetBeans: " + filePath);
            return res;
        }

        AtomicReference<List<Map<String, Object>>> diagListRef = new AtomicReference<>(new ArrayList<>());
        AtomicReference<String> errorRef = new AtomicReference<>(null);

        js.runUserActionTask(new Task<CompilationController>() {
            @Override
            public void run(CompilationController cc) throws Exception {
                try {
                    cc.toPhase(Phase.RESOLVED);
                    List<Diagnostic> diagnostics = cc.getDiagnostics();
                    List<Map<String, Object>> list = new ArrayList<>();
                    if (diagnostics != null) {
                        for (Diagnostic d : diagnostics) {
                            Map<String, Object> dm = new HashMap<>();
                            dm.put("kind", d.getKind() != null ? d.getKind().name() : "UNKNOWN");
                            dm.put("line", d.getLineNumber());
                            dm.put("column", d.getColumnNumber());
                            dm.put("startPosition", d.getStartPosition());
                            dm.put("endPosition", d.getEndPosition());
                            dm.put("message", d.getMessage(null));
                            dm.put("code", d.getCode());
                            list.add(dm);
                        }
                    }
                    diagListRef.set(list);
                } catch (Exception ex) {
                    errorRef.set(ex.getMessage());
                }
            }
        }, true);

        if (errorRef.get() != null) {
            Map<String, Object> res = new HashMap<>();
            res.put("ok", false);
            res.put("error", "Erro ao resolver AST Java: " + errorRef.get());
            return res;
        }

        Map<String, Object> res = new HashMap<>();
        res.put("ok", true);
        res.put("file", file.getAbsolutePath());
        res.put("diagnosticsCount", diagListRef.get().size());
        res.put("diagnostics", diagListRef.get());
        return res;
    }

    public Map<String, Object> getAstStructure(String filePath, int detailLevel) throws Exception {
        File file = new File(filePath).getCanonicalFile();
        if (!file.exists()) {
            throw new FileNotFoundException("Arquivo não encontrado: " + filePath);
        }
        FileObject fo = FileUtil.toFileObject(file);
        if (fo == null) {
            throw new FileNotFoundException("FileObject não encontrado pelo NetBeans para: " + filePath);
        }

        JavaSource js = JavaSource.forFileObject(fo);
        if (js == null) {
            Map<String, Object> res = new HashMap<>();
            res.put("ok", false);
            res.put("error", "Arquivo não é um código fonte Java com classpath no NetBeans: " + filePath);
            return res;
        }

        Map<String, Object> structure = new HashMap<>();
        AtomicReference<String> errorRef = new AtomicReference<>(null);

        js.runUserActionTask(new Task<CompilationController>() {
            @Override
            public void run(CompilationController cc) throws Exception {
                try {
                    cc.toPhase(Phase.ELEMENTS_RESOLVED);
                    CompilationUnitTree cu = cc.getCompilationUnit();
                    if (cu == null) {
                        errorRef.set("CompilationUnitTree nulo para o arquivo.");
                        return;
                    }

                    structure.put("package", cu.getPackageName() != null ? cu.getPackageName().toString() : "");

                    List<String> imports = new ArrayList<>();
                    for (ImportTree imp : cu.getImports()) {
                        imports.add(imp.getQualifiedIdentifier().toString());
                    }
                    structure.put("imports", imports);

                    List<Map<String, Object>> types = new ArrayList<>();
                    for (Tree t : cu.getTypeDecls()) {
                        if (t instanceof ClassTree) {
                            types.add(extractClassInfo((ClassTree) t, detailLevel));
                        }
                    }
                    structure.put("types", types);

                } catch (Exception ex) {
                    errorRef.set(ex.getMessage());
                }
            }
        }, true);

        if (errorRef.get() != null) {
            Map<String, Object> res = new HashMap<>();
            res.put("ok", false);
            res.put("error", errorRef.get());
            return res;
        }

        Map<String, Object> res = new HashMap<>();
        res.put("ok", true);
        res.put("file", file.getAbsolutePath());
        res.put("structure", structure);
        return res;
    }

    private Map<String, Object> extractClassInfo(ClassTree ct, int detailLevel) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", ct.getSimpleName().toString());
        m.put("kind", ct.getKind().name());

        if (ct.getExtendsClause() != null) {
            m.put("extends", ct.getExtendsClause().toString());
        }

        List<String> implementsList = new ArrayList<>();
        for (Tree imp : ct.getImplementsClause()) {
            implementsList.add(imp.toString());
        }
        if (!implementsList.isEmpty()) {
            m.put("implements", implementsList);
        }

        List<String> annotations = new ArrayList<>();
        if (ct.getModifiers() != null) {
            for (AnnotationTree at : ct.getModifiers().getAnnotations()) {
                annotations.add(at.getAnnotationType().toString());
            }
        }
        if (!annotations.isEmpty()) {
            m.put("annotations", annotations);
        }

        List<Map<String, Object>> methods = new ArrayList<>();
        List<Map<String, Object>> fields = new ArrayList<>();
        List<Map<String, Object>> innerClasses = new ArrayList<>();

        for (Tree member : ct.getMembers()) {
            if (member instanceof MethodTree) {
                MethodTree mt = (MethodTree) member;
                Map<String, Object> mm = new HashMap<>();
                mm.put("name", mt.getName().toString());
                mm.put("returnType", mt.getReturnType() != null ? mt.getReturnType().toString() : "void");
                
                List<Map<String, String>> params = new ArrayList<>();
                for (VariableTree param : mt.getParameters()) {
                    Map<String, String> p = new HashMap<>();
                    p.put("name", param.getName().toString());
                    p.put("type", param.getType() != null ? param.getType().toString() : "");
                    params.add(p);
                }
                mm.put("parameters", params);
                methods.add(mm);

            } else if (member instanceof VariableTree) {
                VariableTree vt = (VariableTree) member;
                Map<String, Object> fm = new HashMap<>();
                fm.put("name", vt.getName().toString());
                fm.put("type", vt.getType() != null ? vt.getType().toString() : "");
                fields.add(fm);

            } else if (member instanceof ClassTree) {
                innerClasses.add(extractClassInfo((ClassTree) member, detailLevel));
            }
        }

        m.put("methods", methods);
        m.put("fields", fields);
        if (!innerClasses.isEmpty()) {
            m.put("innerClasses", innerClasses);
        }

        return m;
    }
}
