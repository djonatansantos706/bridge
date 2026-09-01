package com.merito.agynb;

import java.awt.EventQueue;
import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JEditorPane;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.StyledDocument;
import com.merito.agynb.core.BridgeNotifier;
import org.netbeans.modules.editor.indent.api.Reformat;
import org.openide.awt.StatusDisplayer;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.SaveCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.text.NbDocument;

/**
 * Serviço de manipulação direta de arquivos no editor e buffers em memória do NetBeans.
 */
public class NbEditorService {

    private static final Logger LOG = Logger.getLogger(NbEditorService.class.getName());
    private static final NbEditorService INSTANCE = new NbEditorService();

    public static NbEditorService getInstance() {
        return INSTANCE;
    }

    private NbEditorService() {
    }

    public FileObject findFileObject(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return null;
        }
        File f = new File(filePath);
        if (!f.exists()) {
            return null;
        }
        File normalized = FileUtil.normalizeFile(f);
        return FileUtil.toFileObject(normalized);
    }

    public EditorCookie getEditorCookie(FileObject fo) {
        if (fo == null) {
            return null;
        }
        try {
            DataObject dataObj = DataObject.find(fo);
            if (dataObj != null) {
                return dataObj.getLookup().lookup(EditorCookie.class);
            }
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Erro ao obter EditorCookie para " + fo.getPath(), ex);
        }
        return null;
    }

    public boolean openFileAtLine(String filePath, int line) {
        FileObject fo = findFileObject(filePath);
        if (fo == null) {
            return false;
        }
        EditorCookie cookie = getEditorCookie(fo);
        if (cookie == null) {
            return false;
        }

        EventQueue.invokeLater(() -> {
            try {
                cookie.open();
                if (line > 0) {
                    cookie.openDocument();
                    cookie.getLineSet().getCurrent(Math.max(0, line - 1)).show(cookie.getLineSet().getCurrent(Math.max(0, line - 1)).SHOW_GOTO);
                }
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Erro ao navegar para linha no NetBeans", ex);
            }
        });
        return true;
    }

    public String getDocumentContent(String filePath) throws Exception {
        FileObject fo = findFileObject(filePath);
        if (fo == null) {
            throw new IllegalArgumentException("Arquivo não encontrado: " + filePath);
        }
        EditorCookie cookie = getEditorCookie(fo);
        if (cookie == null) {
            throw new IllegalStateException("Não foi possível acessar o EditorCookie para: " + filePath);
        }
        StyledDocument doc = cookie.openDocument();
        return doc.getText(0, doc.getLength());
    }

    public boolean editBuffer(String filePath, String oldText, String newText, boolean allowMultiple) throws Exception {
        FileObject fo = findFileObject(filePath);
        if (fo == null) {
            throw new IllegalArgumentException("Arquivo não encontrado: " + filePath);
        }
        EditorCookie cookie = getEditorCookie(fo);
        if (cookie == null) {
            throw new IllegalStateException("EditorCookie não disponível para o arquivo: " + filePath);
        }

        StyledDocument doc = cookie.openDocument();
        final String docText = doc.getText(0, doc.getLength());

        int firstIdx = docText.indexOf(oldText);
        if (firstIdx == -1) {
            throw new IllegalArgumentException("TargetContent não encontrado no buffer do NetBeans.");
        }

        if (!allowMultiple) {
            int secondIdx = docText.indexOf(oldText, firstIdx + oldText.length());
            if (secondIdx != -1) {
                throw new IllegalArgumentException("Foram encontradas múltiplas ocorrências de TargetContent no buffer e allowMultiple=false.");
            }
        }

        final int oldLen = oldText.length();
        final int[] occurrences = new int[1];

        NbDocument.runAtomicAsUser(doc, () -> {
            try {
                if (!allowMultiple) {
                    doc.remove(firstIdx, oldLen);
                    doc.insertString(firstIdx, newText, null);
                    occurrences[0] = 1;
                } else {
                    int currOffset = 0;
                    int count = 0;
                    while (true) {
                        String currentDocStr = doc.getText(0, doc.getLength());
                        int found = currentDocStr.indexOf(oldText, currOffset);
                        if (found == -1) {
                            break;
                        }
                        doc.remove(found, oldLen);
                        doc.insertString(found, newText, null);
                        currOffset = found + newText.length();
                        count++;
                    }
                    occurrences[0] = count;
                }
            } catch (Exception ex) {
                throw new RuntimeException("Erro ao modificar documento atomicamente", ex);
            }
        });

        StatusDisplayer.getDefault().setStatusText("[Antigravity] Buffer modificado com sucesso (" + occurrences[0] + " ocorrência(s)) em: " + fo.getNameExt());
        BridgeNotifier.bufferChanged(fo.getNameExt(), occurrences[0] + " ocorrência(s) substituída(s) — não salvo (*), Ctrl+Z disponível");
        return true;
    }

    public boolean replaceLines(String filePath, int startLine, int endLine, String targetContent, String replacementContent) throws Exception {
        FileObject fo = findFileObject(filePath);
        if (fo == null) {
            throw new IllegalArgumentException("Arquivo não encontrado: " + filePath);
        }
        EditorCookie cookie = getEditorCookie(fo);
        if (cookie == null) {
            throw new IllegalStateException("EditorCookie não disponível para o arquivo: " + filePath);
        }

        StyledDocument doc = cookie.openDocument();
        int docLines = NbDocument.findLineNumber(doc, doc.getLength()) + 1;
        if (startLine < 1 || startLine > docLines) {
            throw new IllegalArgumentException("startLine inválido: " + startLine + " (total de linhas: " + docLines + ")");
        }
        if (endLine < startLine || endLine > docLines) {
            throw new IllegalArgumentException("endLine inválido: " + endLine + " (total de linhas: " + docLines + ")");
        }

        int startOffset = NbDocument.findLineOffset(doc, startLine - 1);
        int endOffset = (endLine < docLines) ? NbDocument.findLineOffset(doc, endLine) : doc.getLength();

        int rangeLen = endOffset - startOffset;
        String rangeText = doc.getText(startOffset, rangeLen);

        int matchIdx = rangeText.indexOf(targetContent);
        if (matchIdx == -1) {
            throw new IllegalArgumentException("TargetContent não encontrado no intervalo de linhas [" + startLine + ".." + endLine + "].");
        }

        final int targetGlobalOffset = startOffset + matchIdx;
        final int targetLen = targetContent.length();

        NbDocument.runAtomicAsUser(doc, () -> {
            try {
                doc.remove(targetGlobalOffset, targetLen);
                doc.insertString(targetGlobalOffset, replacementContent, null);
            } catch (Exception ex) {
                throw new RuntimeException("Erro ao substituir trecho de linhas", ex);
            }
        });

        StatusDisplayer.getDefault().setStatusText("[Antigravity] Intervalo L" + startLine + "-L" + endLine + " atualizado em: " + fo.getNameExt());
        BridgeNotifier.bufferChanged(fo.getNameExt(), "linhas " + startLine + "-" + endLine + " substituídas — não salvo (*), Ctrl+Z disponível");
        return true;
    }

    public boolean setFullBuffer(String filePath, String content) throws Exception {
        FileObject fo = findFileObject(filePath);
        if (fo == null) {
            throw new IllegalArgumentException("Arquivo não encontrado: " + filePath);
        }
        EditorCookie cookie = getEditorCookie(fo);
        if (cookie == null) {
            throw new IllegalStateException("EditorCookie não disponível para o arquivo: " + filePath);
        }

        StyledDocument doc = cookie.openDocument();
        NbDocument.runAtomicAsUser(doc, () -> {
            try {
                doc.remove(0, doc.getLength());
                doc.insertString(0, content, null);
            } catch (Exception ex) {
                throw new RuntimeException("Erro ao substituir conteúdo total do buffer", ex);
            }
        });

        StatusDisplayer.getDefault().setStatusText("[Antigravity] Buffer totalmente recarregado em: " + fo.getNameExt());
        BridgeNotifier.bufferChanged(fo.getNameExt(), "conteúdo completo substituído — não salvo (*), Ctrl+Z disponível");
        return true;
    }

    public boolean saveDocument(String filePath) throws Exception {
        FileObject fo = findFileObject(filePath);
        if (fo == null) {
            throw new IllegalArgumentException("Arquivo não encontrado: " + filePath);
        }
        DataObject dataObj = DataObject.find(fo);
        if (dataObj == null) {
            throw new IllegalStateException("DataObject não disponível para o arquivo: " + filePath);
        }

        SaveCookie saveCookie = dataObj.getLookup().lookup(SaveCookie.class);
        if (saveCookie != null) {
            saveCookie.save();
            StatusDisplayer.getDefault().setStatusText("[Antigravity] Arquivo salvo no disco: " + fo.getNameExt());
            BridgeNotifier.diskChanged("Bridge: arquivo salvo no disco — " + fo.getNameExt(), fo.getPath());
            return true;
        }

        EditorCookie editorCookie = dataObj.getLookup().lookup(EditorCookie.class);
        if (editorCookie != null) {
            editorCookie.saveDocument();
            StatusDisplayer.getDefault().setStatusText("[Antigravity] Documento salvo no disco: " + fo.getNameExt());
            BridgeNotifier.diskChanged("Bridge: arquivo salvo no disco — " + fo.getNameExt(), fo.getPath());
            return true;
        }

        return false;
    }

    public boolean revertDocument(String filePath) throws Exception {
        FileObject fo = findFileObject(filePath);
        if (fo == null) {
            throw new IllegalArgumentException("Arquivo não encontrado: " + filePath);
        }
        DataObject dataObj = DataObject.find(fo);
        if (dataObj == null) {
            throw new IllegalStateException("DataObject não disponível para o arquivo: " + filePath);
        }

        EditorCookie editorCookie = dataObj.getLookup().lookup(EditorCookie.class);
        if (editorCookie == null) {
            throw new IllegalStateException("EditorCookie não disponível para o arquivo: " + filePath);
        }

        fo.refresh();
        final String diskContent = fo.asText();
        StyledDocument doc = editorCookie.openDocument();

        NbDocument.runAtomicAsUser(doc, () -> {
            try {
                doc.remove(0, doc.getLength());
                doc.insertString(0, diskContent, null);
            } catch (Exception ex) {
                throw new RuntimeException("Erro ao reverter buffer a partir do disco", ex);
            }
        });

        dataObj.setModified(false);
        StatusDisplayer.getDefault().setStatusText("[Antigravity] Buffer revertido a partir do disco: " + fo.getNameExt());
        BridgeNotifier.diskChanged("Bridge: buffer revertido — " + fo.getNameExt(),
                "Alterações em memória foram descartadas; conteúdo recarregado do disco.");
        return true;
    }

    public boolean formatCode(String filePath, Integer startLine, Integer endLine) throws Exception {
        FileObject fo = findFileObject(filePath);
        if (fo == null) {
            throw new IllegalArgumentException("Arquivo não encontrado: " + filePath);
        }
        EditorCookie cookie = getEditorCookie(fo);
        if (cookie == null) {
            throw new IllegalStateException("EditorCookie não disponível para o arquivo: " + filePath);
        }

        StyledDocument doc = cookie.openDocument();
        int docLines = NbDocument.findLineNumber(doc, doc.getLength()) + 1;

        final int sOffset;
        final int eOffset;

        if (startLine != null && startLine > 0) {
            int validStart = Math.min(startLine, docLines);
            int validEnd = (endLine != null && endLine >= validStart) ? Math.min(endLine, docLines) : validStart;
            sOffset = NbDocument.findLineOffset(doc, validStart - 1);
            eOffset = (validEnd < docLines) ? NbDocument.findLineOffset(doc, validEnd) : doc.getLength();
        } else {
            sOffset = 0;
            eOffset = doc.getLength();
        }

        final Reformat reformat = Reformat.get(doc);
        reformat.lock();
        try {
            NbDocument.runAtomicAsUser(doc, () -> {
                try {
                    reformat.reformat(sOffset, eOffset);
                } catch (Exception ex) {
                    throw new RuntimeException("Erro ao formatar código", ex);
                }
            });
        } finally {
            reformat.unlock();
        }

        StatusDisplayer.getDefault().setStatusText("[Antigravity] Código formatado com sucesso em: " + fo.getNameExt());
        BridgeNotifier.bufferChanged(fo.getNameExt(), "código reformatado — não salvo (*), Ctrl+Z disponível");
        return true;
    }

    public Map<String, Object> getSelection(String filePath) throws Exception {
        FileObject fo = findFileObject(filePath);
        if (fo == null) {
            throw new IllegalArgumentException("Arquivo não encontrado: " + filePath);
        }
        EditorCookie cookie = getEditorCookie(fo);
        if (cookie == null) {
            throw new IllegalStateException("EditorCookie não disponível para: " + filePath);
        }

        StyledDocument doc = cookie.openDocument();
        JEditorPane[] panes = cookie.getOpenedPanes();
        JEditorPane activePane = (panes != null && panes.length > 0) ? panes[0] : null;

        Map<String, Object> res = new HashMap<>();
        res.put("ok", true);
        res.put("file", filePath);

        if (activePane != null) {
            Caret caret = activePane.getCaret();
            int dot = caret.getDot();
            int mark = caret.getMark();
            int start = Math.min(dot, mark);
            int end = Math.max(dot, mark);

            int startLine = NbDocument.findLineNumber(doc, start) + 1;
            int startCol = NbDocument.findLineColumn(doc, start) + 1;
            int endLine = NbDocument.findLineNumber(doc, end) + 1;
            int endCol = NbDocument.findLineColumn(doc, end) + 1;

            String selectedText = (end > start) ? doc.getText(start, end - start) : "";

            res.put("hasSelection", end > start);
            res.put("selectedText", selectedText);
            res.put("cursorLine", startLine);
            res.put("cursorColumn", startCol);
            res.put("startLine", startLine);
            res.put("startColumn", startCol);
            res.put("endLine", endLine);
            res.put("endColumn", endCol);
        } else {
            res.put("hasSelection", false);
            res.put("selectedText", "");
            res.put("cursorLine", 1);
            res.put("cursorColumn", 1);
        }

        return res;
    }

    public boolean setSelection(String filePath, int startLine, int startCol, int endLine, int endCol) throws Exception {
        FileObject fo = findFileObject(filePath);
        if (fo == null) {
            throw new IllegalArgumentException("Arquivo não encontrado: " + filePath);
        }
        EditorCookie cookie = getEditorCookie(fo);
        if (cookie == null) {
            throw new IllegalStateException("EditorCookie não disponível para: " + filePath);
        }

        StyledDocument doc = cookie.openDocument();
        int docLines = NbDocument.findLineNumber(doc, doc.getLength()) + 1;

        int sLine = Math.max(1, Math.min(startLine, docLines));
        int eLine = Math.max(sLine, Math.min(endLine, docLines));

        int sOffset = NbDocument.findLineOffset(doc, sLine - 1) + Math.max(0, startCol - 1);
        int eOffset = NbDocument.findLineOffset(doc, eLine - 1) + Math.max(0, endCol - 1);

        sOffset = Math.min(sOffset, doc.getLength());
        eOffset = Math.min(Math.max(sOffset, eOffset), doc.getLength());

        final int finalStart = sOffset;
        final int finalEnd = eOffset;

        EventQueue.invokeLater(() -> {
            try {
                cookie.open();
                JEditorPane[] panes = cookie.getOpenedPanes();
                if (panes != null && panes.length > 0) {
                    JEditorPane pane = panes[0];
                    pane.setCaretPosition(finalStart);
                    pane.moveCaretPosition(finalEnd);
                    pane.requestFocusInWindow();
                }
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Erro ao definir seleção no editor", ex);
            }
        });

        return true;
    }
}
