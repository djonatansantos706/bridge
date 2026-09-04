package com.merito.agynb.form;

import com.merito.agynb.NbEditorService;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Serviço singleton de gerenciamento de formulários Swing (.form e .java) na IDE NetBeans.
 */
public class NbFormService {

    private static final Logger LOG = Logger.getLogger(NbFormService.class.getName());
    private static final NbFormService INSTANCE = new NbFormService();

    public static NbFormService getInstance() {
        return INSTANCE;
    }

    private NbFormService() {
    }

    /**
     * Inspeciona um arquivo .form e retorna sua árvore de componentes e propriedades em Map.
     */
    public Map<String, Object> inspect(String filePath) throws Exception {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Parâmetro 'filePath' é obrigatório.");
        }

        File file = resolveFormFile(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("Arquivo de formulário não encontrado: " + file.getAbsolutePath());
        }

        Map<String, Object> formTree = FormXmlReader.parseForm(file);
        formTree.put("filePath", file.getAbsolutePath());

        // Verifica se existe o .java companheiro
        File companionJava = new File(file.getAbsolutePath().replaceAll("\\.form$", ".java"));
        formTree.put("companionJavaExists", companionJava.exists());
        if (companionJava.exists()) {
            formTree.put("companionJavaPath", companionJava.getAbsolutePath());
        }

        return formTree;
    }

    /**
     * Modifica ou insere uma propriedade cirurgicamente no .form e notifica a IDE.
     */
    public Map<String, Object> setProperty(String filePath, String componentName, String propertyName, String propertyType, String value) throws Exception {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Parâmetro 'filePath' é obrigatório.");
        }
        if (propertyName == null || propertyName.trim().isEmpty()) {
            throw new IllegalArgumentException("Parâmetro 'propertyName' é obrigatório.");
        }

        File file = resolveFormFile(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("Arquivo .form não encontrado: " + file.getAbsolutePath());
        }

        boolean updated = FormPropertyModifier.setProperty(file, componentName, propertyName, propertyType, value);
        if (!updated) {
            throw new IllegalArgumentException("Componente '" + componentName + "' não encontrado no formulário.");
        }

        // Notifica o sistema de arquivos do NetBeans
        refreshNetBeansFile(file);

        // Notifica também o arquivo .java companheiro
        File companionJava = new File(file.getAbsolutePath().replaceAll("\\.form$", ".java"));
        if (companionJava.exists()) {
            refreshNetBeansFile(companionJava);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("message", "Propriedade '" + propertyName + "' atualizada no componente '" + componentName + "'.");
        result.put("filePath", file.getAbsolutePath());
        return result;
    }

    /**
     * Cria um formulário completo (.form + .java) a partir de um blueprint declarativo.
     */
    public Map<String, Object> createBlueprint(String targetDir, String packageName, String className, Map<String, Object> blueprint) throws Exception {
        if (targetDir == null || targetDir.trim().isEmpty()) {
            throw new IllegalArgumentException("Parâmetro 'targetDir' é obrigatório.");
        }
        if (className == null || className.trim().isEmpty()) {
            throw new IllegalArgumentException("Parâmetro 'className' é obrigatório.");
        }

        File dir = new File(targetDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Preenche metadados no blueprint
        blueprint.put("packageName", packageName != null ? packageName : "");
        blueprint.put("className", className);

        File formFile = new File(dir, className + ".form");
        File javaFile = new File(dir, className + ".java");

        // 1. Gera e salva o .form
        FormXmlGenerator.writeFormFile(blueprint, formFile);

        // 2. Gera e salva o .java em windows-1252
        FormJavaGenerator.writeSourceFile(blueprint, javaFile);

        // 3. Atualiza cache do NetBeans
        refreshNetBeansFile(formFile);
        refreshNetBeansFile(javaFile);

        // 4. Abre o .java no editor do NetBeans
        try {
            NbEditorService.getInstance().openFileAtLine(javaFile.getAbsolutePath(), 1);
        } catch (Throwable t) {
            LOG.log(Level.FINE, "Não foi possível abrir o arquivo automaticamente no editor", t);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("message", "Tela " + className + " (.form e .java) gerada com sucesso.");
        result.put("formPath", formFile.getAbsolutePath());
        result.put("javaPath", javaFile.getAbsolutePath());
        return result;
    }

    private File resolveFormFile(String path) {
        if (path.endsWith(".java")) {
            path = path.substring(0, path.length() - 5) + ".form";
        }
        return new File(path);
    }

    private void refreshNetBeansFile(File file) {
        try {
            FileObject fo = FileUtil.toFileObject(file);
            if (fo != null) {
                fo.refresh();
                fo.getParent().refresh();
            }
        } catch (Throwable t) {
            LOG.log(Level.FINE, "Falha ao dar refresh no FileObject do NetBeans", t);
        }
    }
}
