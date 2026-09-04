package com.merito.agynb.handlers;

import com.merito.agynb.NbCommitService;
import com.merito.agynb.NbEditorService;
import com.merito.agynb.core.AbstractJsonHandler;
import com.merito.agynb.core.BridgeResponse;
import com.sun.net.httpserver.HttpExchange;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handlers HTTP para operações de buffer em memória, editor e controle de versão.
 */
public final class EditorHandlers {

    private EditorHandlers() {
    }

    public static class OpenHandler extends AbstractJsonHandler {
        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String file = getStringParam(params, "file", "filePath");
            if (file == null || file.trim().isEmpty()) {
                throw new IllegalArgumentException("Parâmetro 'file' é obrigatório.");
            }
            int line = getIntParam(params, 1, "line");
            boolean opened = NbEditorService.getInstance().openFileAtLine(file, line);
            if (opened) {
                return BridgeResponse.ok("message", "Arquivo aberto no editor do NetBeans");
            }
            return BridgeResponse.error(404, "Arquivo não encontrado no disco: " + file);
        }
    }

    public static class GetContentHandler extends AbstractJsonHandler {
        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String file = getStringParam(params, "file", "filePath");
            if (file == null) {
                throw new IllegalArgumentException("Parâmetro 'file' é obrigatório.");
            }
            String content = NbEditorService.getInstance().getDocumentContent(file);
            return BridgeResponse.ok()
                    .put("file", file)
                    .put("length", content.length())
                    .put("content", content);
        }
    }

    public static class EditHandler extends AbstractJsonHandler {
        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String file = getStringParam(params, "file", "filePath");
            String oldText = getStringParam(params, "old_text");
            String newText = getStringParam(params, "new_text");
            boolean allowMultiple = getBoolParam(params, false, "allow_multiple");

            if (file == null || oldText == null || newText == null) {
                throw new IllegalArgumentException("Parâmetros 'file', 'old_text' e 'new_text' são obrigatórios.");
            }
            boolean success = NbEditorService.getInstance().editBuffer(file, oldText, newText, allowMultiple);
            return BridgeResponse.ok("message", "Buffer do NetBeans atualizado com sucesso (marcação * pendente de salvar)")
                    .put("success", success);
        }
    }

    public static class ReplaceLinesHandler extends AbstractJsonHandler {
        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String file = getStringParam(params, "file", "filePath");
            int startLine = getIntParam(params, -1, "start_line");
            int endLine = getIntParam(params, -1, "end_line");
            String targetContent = getStringParam(params, "target_content");
            String replacementContent = getStringParam(params, "replacement_content");

            if (file == null || startLine <= 0 || endLine < startLine || targetContent == null || replacementContent == null) {
                throw new IllegalArgumentException("Parâmetros inválidos para replace-lines.");
            }
            boolean success = NbEditorService.getInstance().replaceLines(file, startLine, endLine, targetContent, replacementContent);
            return BridgeResponse.ok("message", "Linhas substituídas com sucesso no buffer do NetBeans")
                    .put("success", success);
        }
    }

    public static class SetContentHandler extends AbstractJsonHandler {
        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String file = getStringParam(params, "file", "filePath");
            String content = getStringParam(params, "content");
            if (file == null || content == null) {
                throw new IllegalArgumentException("Parâmetros 'file' e 'content' são obrigatórios.");
            }
            boolean success = NbEditorService.getInstance().setFullBuffer(file, content);
            return BridgeResponse.ok("message", "Conteúdo completo do buffer substituído com sucesso")
                    .put("success", success);
        }
    }

    public static class SaveDocumentHandler extends AbstractJsonHandler {
        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String file = getStringParam(params, "file", "filePath");
            if (file == null) {
                throw new IllegalArgumentException("Parâmetro 'file' é obrigatório.");
            }
            boolean saved = NbEditorService.getInstance().saveDocument(file);
            return BridgeResponse.ok("message", "Documento salvo no disco com sucesso").put("saved", saved);
        }
    }

    public static class RevertDocumentHandler extends AbstractJsonHandler {
        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String file = getStringParam(params, "file", "filePath");
            if (file == null) {
                throw new IllegalArgumentException("Parâmetro 'file' é obrigatório.");
            }
            boolean reverted = NbEditorService.getInstance().revertDocument(file);
            return BridgeResponse.ok("message", "Buffer revertido a partir do disco com sucesso").put("reverted", reverted);
        }
    }

    public static class FormatCodeHandler extends AbstractJsonHandler {
        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String file = getStringParam(params, "file", "filePath");
            if (file == null) {
                throw new IllegalArgumentException("Parâmetro 'file' é obrigatório.");
            }
            Integer startLine = getIntParam(params, null, "start_line");
            Integer endLine = getIntParam(params, null, "end_line");
            boolean formatted = NbEditorService.getInstance().formatCode(file, startLine, endLine);
            return BridgeResponse.ok("message", "Código formatado com sucesso no padrão NetBeans").put("formatted", formatted);
        }
    }

    public static class GetSelectionHandler extends AbstractJsonHandler {
        public GetSelectionHandler() {
            super(false); // Permite GET e POST
        }

        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String file = getStringParam(params, "file", "filePath");
            if (file == null) {
                throw new IllegalArgumentException("Parâmetro 'file' é obrigatório.");
            }
            Map<String, Object> res = NbEditorService.getInstance().getSelection(file);
            return BridgeResponse.of(res);
        }
    }

    public static class SetSelectionHandler extends AbstractJsonHandler {
        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String file = getStringParam(params, "file", "filePath");
            if (file == null) {
                throw new IllegalArgumentException("Parâmetro 'file' é obrigatório.");
            }
            int startLine = getIntParam(params, 1, "start_line");
            int startCol = getIntParam(params, 1, "start_column");
            int endLine = getIntParam(params, startLine, "end_line");
            int endCol = getIntParam(params, startCol, "end_column");

            boolean success = NbEditorService.getInstance().setSelection(file, startLine, startCol, endLine, endCol);
            return BridgeResponse.ok("message", "Seleção aplicada no editor do NetBeans").put("success", success);
        }
    }

    public static class CommitHandler extends AbstractJsonHandler {
        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            List<String> files = new ArrayList<>();
            if (params.get("files") instanceof List) {
                for (Object item : (List<?>) params.get("files")) {
                    if (item instanceof String) files.add((String) item);
                }
            } else if (params.get("file") instanceof String) {
                files.add((String) params.get("file"));
            }
            String message = getStringParam(params, "message");

            NbCommitService.CommitResult res = NbCommitService.getInstance().openCommitDialog(files, message);
            return BridgeResponse.ok()
                    .put("vcs", res.vcs)
                    .put("filesCount", res.filesCount)
                    .put("message", res.message);
        }
    }

    public static class CreateFolderHandler extends AbstractJsonHandler {
        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String path = getStringParam(params, "path", "dir", "folder");
            if (path == null || path.trim().isEmpty()) {
                throw new IllegalArgumentException("Parâmetro 'path' é obrigatório.");
            }

            java.io.File dir = new java.io.File(path);
            boolean created = false;
            if (!dir.exists()) {
                created = dir.mkdirs();
            }

            // Sincroniza com o sistema de arquivos do NetBeans
            org.openide.filesystems.FileObject fo = org.openide.filesystems.FileUtil.toFileObject(dir);
            if (fo != null) {
                fo.refresh();
                org.openide.filesystems.FileObject parent = fo.getParent();
                if (parent != null) {
                    parent.refresh();
                }
            } else {
                org.openide.filesystems.FileUtil.refreshFor(dir);
            }

            return BridgeResponse.ok("message", "Diretório sincronizado com sucesso no NetBeans")
                    .put("path", dir.getAbsolutePath())
                    .put("created", created)
                    .put("exists", dir.exists());
        }
    }

    public static class CreateFileHandler extends AbstractJsonHandler {
        @Override
        protected BridgeResponse handleRequest(Map<String, Object> params, HttpExchange exchange) throws Exception {
            String path = getStringParam(params, "path", "file", "filePath");
            if (path == null || path.trim().isEmpty()) {
                throw new IllegalArgumentException("Parâmetro 'path' é obrigatório.");
            }

            String content = getStringParam(params, "content");
            if (content == null) {
                content = "";
            }

            String encodingName = getStringParam(params, "encoding");
            java.nio.charset.Charset charset;
            if (encodingName != null && !encodingName.trim().isEmpty()) {
                charset = java.nio.charset.Charset.forName(encodingName);
            } else if (path.endsWith(".java")) {
                charset = java.nio.charset.Charset.forName("windows-1252");
            } else {
                charset = java.nio.charset.StandardCharsets.UTF_8;
            }

            boolean openInEditor = getBoolParam(params, true, "open_in_editor", "open");
            int line = getIntParam(params, 1, "line");

            java.io.File file = new java.io.File(path);
            java.io.File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            try (java.io.OutputStream fos = new java.io.FileOutputStream(file);
                 java.io.Writer writer = new java.io.OutputStreamWriter(fos, charset)) {
                writer.write(content);
                writer.flush();
            }

            // Sincroniza com o NetBeans
            org.openide.filesystems.FileObject fo = org.openide.filesystems.FileUtil.toFileObject(file);
            if (fo != null) {
                fo.refresh();
                org.openide.filesystems.FileObject p = fo.getParent();
                if (p != null) {
                    p.refresh();
                }
            } else {
                org.openide.filesystems.FileUtil.refreshFor(file);
            }

            boolean opened = false;
            if (openInEditor) {
                opened = NbEditorService.getInstance().openFileAtLine(file.getAbsolutePath(), line);
            }

            return BridgeResponse.ok("message", "Arquivo criado e sincronizado com sucesso no NetBeans")
                    .put("path", file.getAbsolutePath())
                    .put("encoding", charset.name())
                    .put("opened", opened);
        }
    }
}
