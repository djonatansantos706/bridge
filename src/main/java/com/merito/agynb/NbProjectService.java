package com.merito.agynb;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.spi.project.ActionProvider;
import org.openide.awt.Actions;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

public class NbProjectService {

    private static final Logger LOG = Logger.getLogger(NbProjectService.class.getName());
    private static final NbProjectService INSTANCE = new NbProjectService();

    public static NbProjectService getInstance() {
        return INSTANCE;
    }

    private NbProjectService() {
    }

    public List<Map<String, Object>> listProjects() {
        List<Map<String, Object>> list = new ArrayList<>();
        Project[] openProjects = OpenProjects.getDefault().getOpenProjects();
        Project mainProject = OpenProjects.getDefault().getMainProject();

        for (Project p : openProjects) {
            Map<String, Object> m = new HashMap<>();
            ProjectInformation info = ProjectUtils.getInformation(p);
            m.put("name", info.getName());
            m.put("displayName", info.getDisplayName());
            File dir = FileUtil.toFile(p.getProjectDirectory());
            m.put("path", dir != null ? dir.getAbsolutePath() : p.getProjectDirectory().getPath());
            m.put("isMain", p.equals(mainProject));
            list.add(m);
        }
        return list;
    }

    public Map<String, Object> openProject(String projectDirPath) throws Exception {
        File dir = new File(projectDirPath).getCanonicalFile();
        if (!dir.exists() || !dir.isDirectory()) {
            throw new FileNotFoundException("Diretório de projeto não encontrado: " + projectDirPath);
        }

        FileObject fo = FileUtil.toFileObject(dir);
        if (fo == null) {
            throw new FileNotFoundException("FileObject não encontrado pelo NetBeans para: " + projectDirPath);
        }

        Project p = ProjectManager.getDefault().findProject(fo);
        if (p == null) {
            throw new IllegalArgumentException("O diretório informado não é reconhecido como um projeto NetBeans/Maven/Ant/Gradle: " + projectDirPath);
        }

        OpenProjects.getDefault().open(new Project[]{p}, false);
        ProjectInformation info = ProjectUtils.getInformation(p);

        Map<String, Object> res = new HashMap<>();
        res.put("ok", true);
        res.put("name", info.getName());
        res.put("displayName", info.getDisplayName());
        res.put("path", dir.getAbsolutePath());
        res.put("message", "Projeto aberto no NetBeans com sucesso.");
        return res;
    }

    public Map<String, Object> runProjectAction(String projectPath, String actionName, String targetFilePath) throws Exception {
        Project targetProject = null;

        if (projectPath != null && !projectPath.trim().isEmpty()) {
            File dir = new File(projectPath).getCanonicalFile();
            FileObject fo = FileUtil.toFileObject(dir);
            if (fo != null) {
                targetProject = ProjectManager.getDefault().findProject(fo);
            }
        }

        FileObject targetFo = null;
        DataObject targetDobj = null;
        if (targetFilePath != null && !targetFilePath.trim().isEmpty()) {
            File f = new File(targetFilePath).getCanonicalFile();
            if (f.exists()) {
                targetFo = FileUtil.toFileObject(f);
                if (targetFo != null) {
                    targetDobj = DataObject.find(targetFo);
                    if (targetProject == null) {
                        targetProject = findOwnerProject(targetFo);
                    }
                }
            }
        }

        if (targetProject == null) {
            targetProject = OpenProjects.getDefault().getMainProject();
        }

        if (targetProject == null) {
            Project[] open = OpenProjects.getDefault().getOpenProjects();
            if (open.length > 0) {
                targetProject = open[0];
            }
        }

        if (targetProject == null) {
            throw new IllegalStateException("Nenhum projeto selecionado ou aberto para executar a ação.");
        }

        ActionProvider ap = targetProject.getLookup().lookup(ActionProvider.class);
        if (ap == null) {
            throw new IllegalStateException("O projeto não possui ActionProvider disponível.");
        }

        String command = mapActionName(actionName);
        Lookup context = Lookup.EMPTY;

        if (targetDobj != null && targetFo != null) {
            if (targetDobj.getNodeDelegate() != null) {
                context = Lookups.fixed(targetFo, targetDobj, targetDobj.getNodeDelegate());
            } else {
                context = Lookups.fixed(targetFo, targetDobj);
            }
        }

        if (!ap.isActionEnabled(command, context)) {
            Map<String, Object> res = new HashMap<>();
            res.put("ok", false);
            res.put("error", "Ação \"" + command + "\" não está habilitada para o contexto atual do projeto.");
            return res;
        }

        final Project p = targetProject;
        final String cmd = command;
        final Lookup ctx = context;

        SwingUtilities.invokeLater(() -> {
            try {
                ap.invokeAction(cmd, ctx);
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "Erro ao invocar ação de projeto " + cmd, ex);
            }
        });

        ProjectInformation info = ProjectUtils.getInformation(targetProject);
        StatusDisplayer.getDefault().setStatusText("[Antigravity] Ação disparada no NetBeans: " + command + " (" + info.getDisplayName() + ")");

        Map<String, Object> res = new HashMap<>();
        res.put("ok", true);
        res.put("command", command);
        res.put("project", info.getDisplayName());
        res.put("message", "Ação \"" + command + "\" disparada com sucesso no NetBeans.");
        return res;
    }

    public Map<String, Object> invokeGlobalAction(String category, String actionId) throws Exception {
        if (actionId == null || actionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Action ID é obrigatório.");
        }

        String cat = (category != null && !category.trim().isEmpty()) ? category.trim() : "Actions";
        Action a = Actions.forID(cat, actionId.trim());

        if (a == null) {
            String[] commonCats = new String[]{"Edit", "File", "View", "Navigate", "Source", "Refactor", "Build", "Run", "Debug", "Profile", "Tools", "Window", "Help"};
            for (String c : commonCats) {
                a = Actions.forID(c, actionId.trim());
                if (a != null) {
                    cat = c;
                    break;
                }
            }
        }

        if (a == null) {
            Map<String, Object> res = new HashMap<>();
            res.put("ok", false);
            res.put("error", "Ação não encontrada para ID: " + actionId);
            return res;
        }

        final Action actionToRun = a;
        SwingUtilities.invokeLater(() -> {
            try {
                actionToRun.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, actionId));
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "Erro ao executar ação global " + actionId, ex);
            }
        });

        Map<String, Object> res = new HashMap<>();
        res.put("ok", true);
        res.put("actionId", actionId);
        res.put("category", cat);
        res.put("message", "Ação global \"" + actionId + "\" executada com sucesso.");
        return res;
    }

    private String mapActionName(String actionName) {
        if (actionName == null) return ActionProvider.COMMAND_BUILD;
        String a = actionName.trim().toLowerCase();
        switch (a) {
            case "build": return ActionProvider.COMMAND_BUILD;
            case "clean": return ActionProvider.COMMAND_CLEAN;
            case "rebuild":
            case "clean_and_build":
            case "clean_build": return ActionProvider.COMMAND_REBUILD;
            case "run": return ActionProvider.COMMAND_RUN;
            case "debug": return ActionProvider.COMMAND_DEBUG;
            case "test": return ActionProvider.COMMAND_TEST;
            case "test_single":
            case "testsingle": return ActionProvider.COMMAND_TEST_SINGLE;
            case "run_single":
            case "runsingle": return ActionProvider.COMMAND_RUN_SINGLE;
            case "debug_single":
            case "debugsingle": return ActionProvider.COMMAND_DEBUG_SINGLE;
            default: return actionName;
        }
    }

    private Project findOwnerProject(FileObject fo) {
        try {
            Class<?> foqClass = Class.forName("org.netbeans.api.project.FileOwnerQuery");
            java.lang.reflect.Method getOwnerMethod = foqClass.getMethod("getOwner", FileObject.class);
            return (Project) getOwnerMethod.invoke(null, fo);
        } catch (Throwable t) {
            return null;
        }
    }
}
