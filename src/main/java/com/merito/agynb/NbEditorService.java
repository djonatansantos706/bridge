package com.merito.agynb;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.StyledDocument;
import org.openide.awt.StatusDisplayer;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.LineCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.text.Line;
import org.openide.text.NbDocument;

public class NbEditorService {

    private static final NbEditorService INSTANCE = new NbEditorService();

    public static NbEditorService getInstance() {
        return INSTANCE;
    }

    private NbEditorService() {
    }

    public static class EditResult {
        public boolean success;
        public int replacementsCount;
        public String message;
        public int documentLength;

        public EditResult(boolean success, int replacementsCount, String message, int documentLength) {
            this.success = success;
            this.replacementsCount = replacementsCount;
            this.message = message;
            this.documentLength = documentLength;
        }
    }

    public StyledDocument getOrOpenDocument(String filePath, boolean openInEditor) throws IOException {
        File file = new File(filePath).getCanonicalFile();
        if (!file.exists()) {
            throw new FileNotFoundException("Arquivo não encontrado no disco: " + filePath);
        }

        FileObject fo = FileUtil.toFileObject(file);
        if (fo == null) {
            throw new FileNotFoundException("FileObject não encontrado pelo NetBeans para: " + filePath);
        }

        DataObject dataObj = DataObject.find(fo);
        EditorCookie cookie = dataObj.getLookup().lookup(EditorCookie.class);
        if (cookie == null) {
            throw new IllegalStateException("O arquivo não possui suporte a EditorCookie (não é editável): " + filePath);
        }

        StyledDocument doc = cookie.openDocument();
        if (openInEditor) {
            SwingUtilities.invokeLater(cookie::open);
        }
        return doc;
    }

    public String getDocumentContent(String filePath) throws IOException {
        StyledDocument doc = getOrOpenDocument(filePath, false);
        try {
            return doc.getText(0, doc.getLength());
        } catch (BadLocationException ex) {
            throw new IOException("Erro ao ler conteúdo do buffer: " + ex.getMessage(), ex);
        }
    }

    public boolean openFileAtLine(String filePath, int lineNum) throws IOException {
        File file = new File(filePath).getCanonicalFile();
        if (!file.exists()) {
            return false;
        }
        FileObject fo = FileUtil.toFileObject(file);
        if (fo == null) return false;

        DataObject dataObj = DataObject.find(fo);
        EditorCookie cookie = dataObj.getLookup().lookup(EditorCookie.class);
        if (cookie != null) {
            cookie.openDocument();
            SwingUtilities.invokeLater(() -> {
                cookie.open();
                if (lineNum > 0) {
                    LineCookie lc = dataObj.getLookup().lookup(LineCookie.class);
                    if (lc != null) {
                        try {
                            Line line = lc.getLineSet().getOriginal(lineNum - 1);
                            line.show(Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS);
                        } catch (Exception ignored) {
                        }
                    }
                }
            });
            return true;
        }
        return false;
    }

    public EditResult replaceExact(String filePath, String targetContent, String replacementContent, boolean allowMultiple) throws IOException {
        StyledDocument doc = getOrOpenDocument(filePath, true);
        AtomicInteger replacements = new AtomicInteger(0);
        AtomicReference<String> errorRef = new AtomicReference<>(null);

        try {
            NbDocument.runAtomicAsUser(doc, () -> {
                try {
                    String fullText = doc.getText(0, doc.getLength());
                    int idx = fullText.indexOf(targetContent);
                    if (idx == -1) {
                        errorRef.set("Texto alvo não encontrado no buffer do NetBeans.");
                        return;
                    }

                    if (!allowMultiple) {
                        int secondIdx = fullText.indexOf(targetContent, idx + targetContent.length());
                        if (secondIdx != -1) {
                            errorRef.set("O texto alvo ocorre múltiplas vezes no buffer. Especifique um trecho único ou use allowMultiple=true.");
                            return;
                        }
                    }

                    while (idx != -1) {
                        doc.remove(idx, targetContent.length());
                        doc.insertString(idx, replacementContent, null);
                        replacements.incrementAndGet();

                        if (!allowMultiple) {
                            break;
                        }

                        fullText = doc.getText(0, doc.getLength());
                        idx = fullText.indexOf(targetContent, idx + replacementContent.length());
                    }
                } catch (BadLocationException ex) {
                    errorRef.set("Erro de posicionamento no buffer: " + ex.getMessage());
                } catch (Exception ex) {
                    errorRef.set("Exceção ao modificar buffer: " + ex.getMessage());
                }
            });
        } catch (Exception ex) {
            return new EditResult(false, 0, "Falha na transação atômica: " + ex.getMessage(), doc.getLength());
        }

        if (errorRef.get() != null) {
            return new EditResult(false, 0, errorRef.get(), doc.getLength());
        }

        StatusDisplayer.getDefault().setStatusText("[Antigravity] Buffer atualizado: " + new File(filePath).getName() + " (" + replacements.get() + " substituições)");
        return new EditResult(true, replacements.get(), "Buffer atualizado com sucesso no NetBeans (não salvo em disco)", doc.getLength());
    }

    public EditResult replaceLineRange(String filePath, int startLine, int endLine, String targetContent, String replacementContent) throws IOException {
        StyledDocument doc = getOrOpenDocument(filePath, true);
        AtomicReference<String> errorRef = new AtomicReference<>(null);
        AtomicBoolean successRef = new AtomicBoolean(false);

        try {
            NbDocument.runAtomicAsUser(doc, () -> {
                try {
                    Element root = doc.getDefaultRootElement();
                    int totalLines = root.getElementCount();
                    
                    int sLine = Math.max(1, startLine) - 1;
                    int eLine = Math.min(totalLines, endLine) - 1;

                    if (sLine >= totalLines) {
                        errorRef.set("Linha inicial (" + startLine + ") fora do documento (total: " + totalLines + ")");
                        return;
                    }

                    int startOffset = root.getElement(sLine).getStartOffset();
                    int endOffset = root.getElement(Math.min(eLine, totalLines - 1)).getEndOffset();

                    int rangeLen = endOffset - startOffset;
                    String rangeText = doc.getText(startOffset, rangeLen);

                    int targetIdx = rangeText.indexOf(targetContent);
                    if (targetIdx == -1) {
                        errorRef.set("Texto alvo não encontrado no intervalo de linhas especificado [" + startLine + "-" + endLine + "].");
                        return;
                    }

                    int absoluteOffset = startOffset + targetIdx;
                    doc.remove(absoluteOffset, targetContent.length());
                    doc.insertString(absoluteOffset, replacementContent, null);
                    successRef.set(true);

                } catch (BadLocationException ex) {
                    errorRef.set("Erro ao calcular linhas no buffer: " + ex.getMessage());
                } catch (Exception ex) {
                    errorRef.set("Exceção ao modificar linhas: " + ex.getMessage());
                }
            });
        } catch (Exception ex) {
            return new EditResult(false, 0, "Falha na transação atômica: " + ex.getMessage(), doc.getLength());
        }

        if (errorRef.get() != null) {
            return new EditResult(false, 0, errorRef.get(), doc.getLength());
        }

        StatusDisplayer.getDefault().setStatusText("[Antigravity] Buffer atualizado (linhas " + startLine + "-" + endLine + "): " + new File(filePath).getName());
        return new EditResult(true, 1, "Buffer atualizado com sucesso no NetBeans", doc.getLength());
    }

    public EditResult setFullContent(String filePath, String fullNewContent) throws IOException {
        StyledDocument doc = getOrOpenDocument(filePath, true);
        AtomicReference<String> errorRef = new AtomicReference<>(null);

        try {
            NbDocument.runAtomicAsUser(doc, () -> {
                try {
                    int len = doc.getLength();
                    if (len > 0) {
                        doc.remove(0, len);
                    }
                    doc.insertString(0, fullNewContent, null);
                } catch (BadLocationException ex) {
                    errorRef.set("Erro ao substituir conteúdo total: " + ex.getMessage());
                }
            });
        } catch (Exception ex) {
            return new EditResult(false, 0, "Falha na transação atômica: " + ex.getMessage(), doc.getLength());
        }

        if (errorRef.get() != null) {
            return new EditResult(false, 0, errorRef.get(), doc.getLength());
        }

        StatusDisplayer.getDefault().setStatusText("[Antigravity] Conteúdo total do buffer atualizado: " + new File(filePath).getName());
        return new EditResult(true, 1, "Conteúdo total do buffer atualizado com sucesso", doc.getLength());
    }
}
