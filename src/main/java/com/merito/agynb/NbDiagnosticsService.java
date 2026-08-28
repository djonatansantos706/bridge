package com.merito.agynb;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import org.netbeans.api.java.source.ClassIndex;
import org.netbeans.api.java.source.ClassIndex.SearchKind;
import org.netbeans.api.java.source.ClassIndex.SearchScope;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.SourceUtils;
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

    // ==========================================
    // TASK-06: Resolução e Goto Definition
    // ==========================================

    public Map<String, Object> gotoDefinition(String filePath, int line, int column, String symbolName) throws Exception {
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

        AtomicReference<Map<String, Object>> resultRef = new AtomicReference<>(null);
        AtomicReference<String> errorRef = new AtomicReference<>(null);

        js.runUserActionTask(new Task<CompilationController>() {
            @Override
            public void run(CompilationController cc) throws Exception {
                try {
                    cc.toPhase(Phase.RESOLVED);
                    CompilationUnitTree cu = cc.getCompilationUnit();
                    if (cu == null) {
                        errorRef.set("CompilationUnitTree nulo para o arquivo.");
                        return;
                    }

                    Element targetElement = null;

                    // 1. Tenta resolver por linha e coluna se fornecidos
                    if (line > 0 && column > 0 && cu.getLineMap() != null) {
                        long pos = cu.getLineMap().getPosition(line, column);
                        if (pos >= 0) {
                            TreePath path = cc.getTreeUtilities().pathFor((int) pos);
                            if (path != null) {
                                targetElement = cc.getTrees().getElement(path);
                            }
                        }
                    }

                    // 2. Se não encontrou pelo offset, busca por symbolName no AST ou ElementUtilities
                    if (targetElement == null && symbolName != null && !symbolName.trim().isEmpty()) {
                        String cleanName = symbolName.trim();
                        for (Tree t : cu.getTypeDecls()) {
                            if (t instanceof ClassTree) {
                                ClassTree ct = (ClassTree) t;
                                if (cleanName.equals(ct.getSimpleName().toString())) {
                                    TreePath p = cc.getTrees().getPath(cu, ct);
                                    targetElement = cc.getTrees().getElement(p);
                                    break;
                                }
                                for (Tree m : ct.getMembers()) {
                                    if (m instanceof MethodTree) {
                                        MethodTree mt = (MethodTree) m;
                                        if (cleanName.equals(mt.getName().toString())) {
                                            TreePath p = cc.getTrees().getPath(cu, mt);
                                            targetElement = cc.getTrees().getElement(p);
                                            break;
                                        }
                                    } else if (m instanceof VariableTree) {
                                        VariableTree vt = (VariableTree) m;
                                        if (cleanName.equals(vt.getName().toString())) {
                                            TreePath p = cc.getTrees().getPath(cu, vt);
                                            targetElement = cc.getTrees().getElement(p);
                                            break;
                                        }
                                    }
                                }
                                if (targetElement != null) break;
                            }
                        }

                        if (targetElement == null) {
                            targetElement = cc.getElementUtilities().findElement(cleanName);
                        }
                    }

                    if (targetElement == null) {
                        Map<String, Object> notFound = new HashMap<>();
                        notFound.put("ok", false);
                        notFound.put("error", "Símbolo não encontrado ou não foi possível resolver elemento Java.");
                        resultRef.set(notFound);
                        return;
                    }

                    // 3. Identifica o arquivo de origem da declaração do elemento
                    FileObject targetFo = SourceUtils.getFile(targetElement, cc.getClasspathInfo());
                    if (targetFo == null) {
                        try {
                            ElementHandle<? extends Element> handle = ElementHandle.create(targetElement);
                            targetFo = SourceUtils.getFile(handle, cc.getClasspathInfo());
                        } catch (Exception ignored) {
                        }
                    }
                    if (targetFo == null) {
                        targetFo = fo; // elemento local
                    }

                    // 4. Determina linha e coluna da declaração
                    long targetLine = 1;
                    long targetCol = 1;
                    String targetFilePath = targetFo.getPath();
                    File targetFile = FileUtil.toFile(targetFo);
                    if (targetFile != null) {
                        targetFilePath = targetFile.getAbsolutePath();
                    }

                    if (targetFo.equals(fo)) {
                        TreePath declPath = cc.getTrees().getPath(targetElement);
                        if (declPath != null) {
                            long startPos = cc.getTrees().getSourcePositions().getStartPosition(cu, declPath.getLeaf());
                            if (startPos >= 0 && cu.getLineMap() != null) {
                                targetLine = cu.getLineMap().getLineNumber(startPos);
                                targetCol = cu.getLineMap().getColumnNumber(startPos);
                            }
                        }
                    } else {
                        JavaSource targetJs = JavaSource.forFileObject(targetFo);
                        if (targetJs != null) {
                            ElementHandle<? extends Element> handle = ElementHandle.create(targetElement);
                            final AtomicReference<long[]> posRef = new AtomicReference<>(new long[]{1, 1});
                            targetJs.runUserActionTask(new Task<CompilationController>() {
                                @Override
                                public void run(CompilationController targetCc) throws Exception {
                                    targetCc.toPhase(Phase.RESOLVED);
                                    Element resolvedElem = handle.resolve(targetCc);
                                    if (resolvedElem != null) {
                                        TreePath dp = targetCc.getTrees().getPath(resolvedElem);
                                        if (dp != null && targetCc.getCompilationUnit() != null) {
                                            long sp = targetCc.getTrees().getSourcePositions().getStartPosition(targetCc.getCompilationUnit(), dp.getLeaf());
                                            if (sp >= 0 && targetCc.getCompilationUnit().getLineMap() != null) {
                                                long l = targetCc.getCompilationUnit().getLineMap().getLineNumber(sp);
                                                long c = targetCc.getCompilationUnit().getLineMap().getColumnNumber(sp);
                                                posRef.set(new long[]{l, c});
                                            }
                                        }
                                    }
                                }
                            }, true);
                            targetLine = posRef.get()[0];
                            targetCol = posRef.get()[1];
                        }
                    }

                    String signature = formatElementSignature(targetElement);

                    Map<String, Object> defRes = new HashMap<>();
                    defRes.put("ok", true);
                    defRes.put("symbolName", targetElement.getSimpleName().toString());
                    defRes.put("kind", targetElement.getKind().name());
                    defRes.put("signature", signature);
                    defRes.put("targetFile", targetFilePath);
                    defRes.put("targetLine", targetLine);
                    defRes.put("targetColumn", targetCol);
                    if (targetElement.getEnclosingElement() != null) {
                        defRes.put("enclosingElement", targetElement.getEnclosingElement().getSimpleName().toString());
                    }
                    resultRef.set(defRes);

                } catch (Exception ex) {
                    errorRef.set(ex.getMessage());
                }
            }
        }, true);

        if (errorRef.get() != null) {
            Map<String, Object> res = new HashMap<>();
            res.put("ok", false);
            res.put("error", "Erro ao resolver definição: " + errorRef.get());
            return res;
        }

        Map<String, Object> finalRes = resultRef.get();
        if (finalRes == null) {
            finalRes = new HashMap<>();
            finalRes.put("ok", false);
            finalRes.put("error", "Definição não encontrada");
        }
        return finalRes;
    }

    // ==========================================
    // TASK-07: Localização de Usos (Find Usages)
    // ==========================================

    public Map<String, Object> findUsages(String filePath, String symbolName) throws Exception {
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

        AtomicReference<Map<String, Object>> resultRef = new AtomicReference<>(null);
        AtomicReference<String> errorRef = new AtomicReference<>(null);

        js.runUserActionTask(new Task<CompilationController>() {
            @Override
            public void run(CompilationController cc) throws Exception {
                try {
                    cc.toPhase(Phase.RESOLVED);
                    CompilationUnitTree cu = cc.getCompilationUnit();
                    if (cu == null) {
                        errorRef.set("CompilationUnitTree nulo para o arquivo.");
                        return;
                    }

                    Element targetElement = null;
                    if (symbolName != null && !symbolName.trim().isEmpty()) {
                        String cleanName = symbolName.trim();
                        for (Tree t : cu.getTypeDecls()) {
                            if (t instanceof ClassTree) {
                                ClassTree ct = (ClassTree) t;
                                if (cleanName.equals(ct.getSimpleName().toString())) {
                                    TreePath p = cc.getTrees().getPath(cu, ct);
                                    targetElement = cc.getTrees().getElement(p);
                                    break;
                                }
                                for (Tree m : ct.getMembers()) {
                                    if (m instanceof MethodTree) {
                                        MethodTree mt = (MethodTree) m;
                                        if (cleanName.equals(mt.getName().toString())) {
                                            TreePath p = cc.getTrees().getPath(cu, mt);
                                            targetElement = cc.getTrees().getElement(p);
                                            break;
                                        }
                                    } else if (m instanceof VariableTree) {
                                        VariableTree vt = (VariableTree) m;
                                        if (cleanName.equals(vt.getName().toString())) {
                                            TreePath p = cc.getTrees().getPath(cu, vt);
                                            targetElement = cc.getTrees().getElement(p);
                                            break;
                                        }
                                    }
                                }
                                if (targetElement != null) break;
                            }
                        }

                        if (targetElement == null) {
                            targetElement = cc.getElementUtilities().findElement(cleanName);
                        }
                    }

                    final String sym = (targetElement != null) ? targetElement.getSimpleName().toString() : symbolName;
                    final Set<FileObject> targetFiles = new HashSet<>();
                    targetFiles.add(fo);

                    if (targetElement != null) {
                        TypeElement typeElem = (targetElement instanceof TypeElement)
                                ? (TypeElement) targetElement
                                : cc.getElementUtilities().enclosingTypeElement(targetElement);

                        if (typeElem != null) {
                            try {
                                ElementHandle<TypeElement> th = ElementHandle.create(typeElem);
                                ClassIndex classIndex = cc.getClasspathInfo().getClassIndex();
                                if (classIndex != null) {
                                    Set<SearchKind> kinds = EnumSet.of(SearchKind.TYPE_REFERENCES, SearchKind.METHOD_REFERENCES, SearchKind.FIELD_REFERENCES, SearchKind.IMPLEMENTORS);
                                    Set<FileObject> resources = classIndex.getResources(th, kinds, EnumSet.of(SearchScope.SOURCE));
                                    if (resources != null) {
                                        targetFiles.addAll(resources);
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }

                    // Se encontrou poucos arquivos, também adiciona arquivos no mesmo diretório pai
                    FileObject parentDir = fo.getParent();
                    if (parentDir != null && targetFiles.size() <= 2) {
                        for (FileObject sibling : parentDir.getChildren()) {
                            if ("java".equalsIgnoreCase(sibling.getExt())) {
                                targetFiles.add(sibling);
                            }
                        }
                    }

                    List<Map<String, Object>> usagesList = new ArrayList<>();
                    Set<String> visitedLocations = new HashSet<>();

                    for (FileObject candFo : targetFiles) {
                        JavaSource candJs = JavaSource.forFileObject(candFo);
                        if (candJs == null) continue;

                        candJs.runUserActionTask(new Task<CompilationController>() {
                            @Override
                            public void run(CompilationController candCc) throws Exception {
                                candCc.toPhase(Phase.RESOLVED);
                                CompilationUnitTree candCu = candCc.getCompilationUnit();
                                if (candCu == null || candCu.getLineMap() == null) return;

                                String candFilePath = candFo.getPath();
                                File cf = FileUtil.toFile(candFo);
                                if (cf != null) {
                                    candFilePath = cf.getAbsolutePath();
                                }
                                final String finalCandPath = candFilePath;
                                final CharSequence docText = candCc.getText();

                                new TreePathScanner<Void, Void>() {
                                    @Override
                                    public Void visitIdentifier(IdentifierTree node, Void p) {
                                        if (node.getName().contentEquals(sym)) {
                                            recordUsage(candCc, candCu, getCurrentPath(), finalCandPath, docText, usagesList, visitedLocations);
                                        }
                                        return super.visitIdentifier(node, p);
                                    }

                                    @Override
                                    public Void visitMemberSelect(MemberSelectTree node, Void p) {
                                        if (node.getIdentifier().contentEquals(sym)) {
                                            recordUsage(candCc, candCu, getCurrentPath(), finalCandPath, docText, usagesList, visitedLocations);
                                        }
                                        return super.visitMemberSelect(node, p);
                                    }

                                    @Override
                                    public Void visitMemberReference(MemberReferenceTree node, Void p) {
                                        if (node.getName().contentEquals(sym)) {
                                            recordUsage(candCc, candCu, getCurrentPath(), finalCandPath, docText, usagesList, visitedLocations);
                                        }
                                        return super.visitMemberReference(node, p);
                                    }
                                }.scan(candCu, null);
                            }
                        }, true);
                    }

                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("ok", true);
                    res.put("symbol", sym);
                    res.put("kind", targetElement != null ? targetElement.getKind().name() : "SYMBOL");
                    res.put("declarationFile", file.getAbsolutePath());
                    res.put("usagesCount", usagesList.size());
                    res.put("usages", usagesList);
                    resultRef.set(res);

                } catch (Exception ex) {
                    errorRef.set(ex.getMessage());
                }
            }
        }, true);

        if (errorRef.get() != null) {
            Map<String, Object> res = new HashMap<>();
            res.put("ok", false);
            res.put("error", "Erro ao buscar usos: " + errorRef.get());
            return res;
        }

        Map<String, Object> finalRes = resultRef.get();
        if (finalRes == null) {
            finalRes = new HashMap<>();
            finalRes.put("ok", false);
            finalRes.put("error", "Nenhum uso localizado para o símbolo");
        }
        return finalRes;
    }

    private void recordUsage(CompilationController cc, CompilationUnitTree cu, TreePath path, String filePath, CharSequence docText, List<Map<String, Object>> usagesList, Set<String> visitedLocations) {
        try {
            long pos = cc.getTrees().getSourcePositions().getStartPosition(cu, path.getLeaf());
            if (pos < 0 || cu.getLineMap() == null) return;

            long line = cu.getLineMap().getLineNumber(pos);
            long col = cu.getLineMap().getColumnNumber(pos);

            String locKey = filePath + ":" + line + ":" + col;
            if (visitedLocations.contains(locKey)) return;
            visitedLocations.add(locKey);

            // Extrai snippet da linha
            String snippet = "";
            if (docText != null) {
                long lineStart = cu.getLineMap().getStartPosition(line);
                long nextLineStart = cu.getLineMap().getStartPosition(line + 1);
                if (lineStart >= 0) {
                    int end = (nextLineStart > lineStart && nextLineStart <= docText.length()) ? (int) nextLineStart : docText.length();
                    snippet = docText.subSequence((int) lineStart, end).toString().trim();
                }
            }

            // Identifica método ou classe envolvente
            String enclosingScope = "";
            TreePath p = path.getParentPath();
            while (p != null) {
                Tree leaf = p.getLeaf();
                if (leaf instanceof MethodTree) {
                    enclosingScope = ((MethodTree) leaf).getName().toString() + "()";
                    break;
                } else if (leaf instanceof ClassTree) {
                    enclosingScope = ((ClassTree) leaf).getSimpleName().toString();
                    break;
                }
                p = p.getParentPath();
            }

            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("file", filePath);
            usage.put("line", line);
            usage.put("column", col);
            usage.put("snippet", snippet);
            if (!enclosingScope.isEmpty()) {
                usage.put("enclosingScope", enclosingScope);
            }
            usagesList.add(usage);

        } catch (Exception ex) {
            LOG.log(Level.FINE, "Erro ao registrar uso de símbolo", ex);
        }
    }

    private String formatElementSignature(Element element) {
        if (element == null) return "";
        StringBuilder sb = new StringBuilder();
        if (element.getModifiers() != null && !element.getModifiers().isEmpty()) {
            for (Modifier m : element.getModifiers()) {
                sb.append(m.toString()).append(" ");
            }
        }
        if (element instanceof ExecutableElement) {
            ExecutableElement ee = (ExecutableElement) element;
            if (ee.getKind() == ElementKind.CONSTRUCTOR) {
                sb.append(ee.getEnclosingElement().getSimpleName());
            } else {
                sb.append(ee.getReturnType().toString()).append(" ").append(ee.getSimpleName());
            }
            sb.append("(");
            boolean first = true;
            for (VariableElement p : ee.getParameters()) {
                if (!first) sb.append(", ");
                sb.append(p.asType().toString()).append(" ").append(p.getSimpleName());
                first = false;
            }
            sb.append(")");
        } else if (element instanceof VariableElement) {
            VariableElement ve = (VariableElement) element;
            sb.append(ve.asType().toString()).append(" ").append(ve.getSimpleName());
        } else if (element instanceof TypeElement) {
            TypeElement te = (TypeElement) element;
            sb.append(te.getKind().name().toLowerCase()).append(" ").append(te.getQualifiedName());
        } else {
            sb.append(element.getKind().name()).append(" ").append(element.getSimpleName());
        }
        return sb.toString().trim();
    }
}
