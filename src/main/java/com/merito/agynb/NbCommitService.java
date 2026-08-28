package com.merito.agynb;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.util.Lookup;

public class NbCommitService {

    private static final Logger LOG = Logger.getLogger(NbCommitService.class.getName());
    private static final NbCommitService INSTANCE = new NbCommitService();

    public static NbCommitService getInstance() {
        return INSTANCE;
    }

    private NbCommitService() {
    }

    public static class CommitResult {
        public final boolean success;
        public final String vcs;
        public final int filesCount;
        public final String message;

        public CommitResult(boolean success, String vcs, int filesCount, String message) {
            this.success = success;
            this.vcs = vcs;
            this.filesCount = filesCount;
            this.message = message;
        }
    }

    public CommitResult openCommitDialog(List<String> filePaths, String commitMessage) throws Exception {
        if (filePaths == null || filePaths.isEmpty()) {
            return new CommitResult(false, "Unknown", 0, "Lista de arquivos para commit está vazia.");
        }

        List<File> validFiles = new ArrayList<>();
        for (String path : filePaths) {
            if (path == null || path.trim().isEmpty()) continue;
            File f = new File(path.trim()).getCanonicalFile();
            if (f.exists()) {
                validFiles.add(f);
            } else {
                LOG.warning("[Antigravity] Arquivo não encontrado para commit: " + path);
            }
        }

        if (validFiles.isEmpty()) {
            return new CommitResult(false, "Unknown", 0, "Nenhum dos arquivos especificados foi encontrado no disco.");
        }

        ClassLoader cl = Lookup.getDefault().lookup(ClassLoader.class);
        if (cl == null) {
            cl = Thread.currentThread().getContextClassLoader();
        }
        if (cl == null) {
            cl = NbCommitService.class.getClassLoader();
        }

        String vcsType = detectVcsType(validFiles.get(0), cl);

        if ("Git".equalsIgnoreCase(vcsType)) {
            openGitCommitDialog(validFiles, commitMessage, cl);
        } else {
            // Default to Subversion
            openSvnCommitDialog(validFiles, commitMessage, cl);
            vcsType = "Subversion";
        }

        String statusMsg = "[Antigravity] Tela de commit aberta (" + vcsType + ") para " + validFiles.size() + " arquivo(s)";
        StatusDisplayer.getDefault().setStatusText(statusMsg);

        return new CommitResult(true, vcsType, validFiles.size(), "Tela de commit aberta no NetBeans com sucesso. Revise as alterações e confirme o commit na interface do NetBeans.");
    }

    private String detectVcsType(File sampleFile, ClassLoader cl) {
        try {
            Class<?> versioningSupportClass = cl.loadClass("org.netbeans.modules.versioning.spi.VersioningSupport");
            Method getOwnerMethod = versioningSupportClass.getMethod("getOwner", File.class);
            Object owner = getOwnerMethod.invoke(null, sampleFile);
            if (owner != null) {
                String className = owner.getClass().getName().toLowerCase();
                if (className.contains("git")) {
                    return "Git";
                } else if (className.contains("subversion") || className.contains("svn")) {
                    return "Subversion";
                }
            }
        } catch (Throwable t) {
            LOG.log(Level.FINE, "Falha ao consultar VersioningSupport.getOwner", t);
        }

        // Fallback por verificação de diretório .svn ou .git na árvore de diretórios
        File curr = sampleFile.isDirectory() ? sampleFile : sampleFile.getParentFile();
        while (curr != null) {
            if (new File(curr, ".svn").isDirectory()) {
                return "Subversion";
            }
            if (new File(curr, ".git").isDirectory() || new File(curr, ".git").isFile()) {
                return "Git";
            }
            curr = curr.getParentFile();
        }

        return "Subversion";
    }

    private void openSvnCommitDialog(List<File> files, String commitMessage, ClassLoader cl) throws Exception {
        File[] fileArray = files.toArray(new File[0]);

        Class<?> contextClass = cl.loadClass("org.netbeans.modules.subversion.util.Context");
        Constructor<?> ctxCtor = contextClass.getConstructor(File[].class);
        Object svnContext = ctxCtor.newInstance((Object) fileArray);

        Class<?> commitActionClass = cl.loadClass("org.netbeans.modules.subversion.ui.commit.CommitAction");
        Method commitMethod = commitActionClass.getMethod("commit", String.class, contextClass, boolean.class);

        SwingUtilities.invokeLater(() -> {
            try {
                commitMethod.invoke(null, commitMessage != null ? commitMessage : "", svnContext, false);
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "[Antigravity] Erro ao invocar diálogo de commit do Subversion", ex);
            }
        });
    }

    private void openGitCommitDialog(List<File> files, String commitMessage, ClassLoader cl) throws Exception {
        if (commitMessage != null && !commitMessage.isEmpty()) {
            try {
                Class<?> gitConfigClass = cl.loadClass("org.netbeans.modules.git.GitModuleConfig");
                Method getDefaultMethod = gitConfigClass.getMethod("getDefault");
                Object gitConfig = getDefaultMethod.invoke(null);
                Method setMsgMethod = gitConfigClass.getMethod("setLastCanceledCommitMessage", String.class);
                setMsgMethod.invoke(gitConfig, commitMessage);
            } catch (Throwable t) {
                LOG.log(Level.FINE, "Não foi possível registrar mensagem prévia no GitModuleConfig", t);
            }
        }

        List<Node> nodes = new ArrayList<>();
        for (File f : files) {
            FileObject fo = FileUtil.toFileObject(f);
            if (fo != null) {
                DataObject dobj = DataObject.find(fo);
                if (dobj != null && dobj.getNodeDelegate() != null) {
                    nodes.add(dobj.getNodeDelegate());
                }
            }
        }

        if (nodes.isEmpty()) {
            throw new IllegalStateException("Nenhum Node do NetBeans pôde ser criado para os arquivos Git fornecidos.");
        }

        Class<?> vcsContextClass = cl.loadClass("org.netbeans.modules.versioning.spi.VCSContext");
        Method forNodesMethod = vcsContextClass.getMethod("forNodes", Node[].class);
        Object vcsContext = forNodesMethod.invoke(null, (Object) nodes.toArray(new Node[0]));

        Class<?> gitCommitActionClass = cl.loadClass("org.netbeans.modules.git.ui.commit.CommitAction");
        Object gitCommitAction = gitCommitActionClass.getDeclaredConstructor().newInstance();
        Method performActionMethod = gitCommitActionClass.getMethod("performAction", vcsContextClass);

        SwingUtilities.invokeLater(() -> {
            try {
                performActionMethod.invoke(gitCommitAction, vcsContext);
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "[Antigravity] Erro ao invocar diálogo de commit do Git", ex);
            }
        });
    }
}
