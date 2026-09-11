package com.merito.agynb.form;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gerador de código Java para a camada Presenter (PR) no padrão MVP.
 * Implementa base.utils.mvp.Presenter e amarra os eventos canônicos da View (VW).
 * @author Antigravity Bridge Suite
 */
public class FormPresenterGenerator {

    public static final Charset ENCODING_WINDOWS_1252 = Charset.forName("windows-1252");

    public static String generateSource(Map<String, Object> spec, String prName, String vwName, String packageName) {
        StringBuilder sb = new StringBuilder();
        if (packageName != null && !packageName.trim().isEmpty()) {
            sb.append("package ").append(packageName).append(";\n\n");
        }

        sb.append("import base.utils.mvp.Presenter;\n");
        sb.append("import java.awt.Dialog;\n");
        sb.append("import java.awt.Frame;\n");
        sb.append("import java.awt.Window;\n\n");

        sb.append("/**\n");
        sb.append(" * Presenter para a tela ").append(vwName).append(" (Padrão MVP).\n");
        sb.append(" * Centraliza regras de negócio, validações e eventos, mantendo a View passiva.\n");
        sb.append(" * @author Antigravity Bridge Suite\n");
        sb.append(" */\n");
        sb.append("public class ").append(prName).append(" implements Presenter {\n\n");

        sb.append("    private final ").append(vwName).append(" view;\n\n");

        // Construtor 1: Window + ModalityType
        sb.append("    public ").append(prName).append("(Window window, Dialog.ModalityType modal) {\n");
        sb.append("        this.view = new ").append(vwName).append("(window, modal);\n");
        sb.append("        init();\n");
        sb.append("    }\n\n");

        // Construtor 2: Frame + boolean
        sb.append("    public ").append(prName).append("(Frame parent, boolean modal) {\n");
        sb.append("        this.view = new ").append(vwName).append("(parent, modal);\n");
        sb.append("        init();\n");
        sb.append("    }\n\n");

        // init
        sb.append("    @Override\n");
        sb.append("    public final void init() {\n");
        sb.append("        initListeners();\n");
        sb.append("        initEvents();\n");
        sb.append("    }\n\n");

        // initListeners
        sb.append("    @Override\n");
        sb.append("    public void initListeners() {\n");
        sb.append("        // Inicialização de listeners de dados e serviços\n");
        sb.append("    }\n\n");

        // Coleta de botões da View para gerar listeners em initEvents
        List<String> buttonGetters = collectButtonGetters(spec);

        // initEvents
        sb.append("    @Override\n");
        sb.append("    public void initEvents() {\n");
        for (String getter : buttonGetters) {
            String lower = getter.toLowerCase();
            if (lower.contains("fechar") || lower.contains("sair") || lower.contains("cancelar")) {
                sb.append("        view.").append(getter).append("().addActionListener(e -> fechar());\n");
            } else if (lower.contains("ok") || lower.contains("salvar") || lower.contains("gravar")) {
                sb.append("        view.").append(getter).append("().addActionListener(e -> salvar());\n");
            } else if (lower.contains("excluir") || lower.contains("deletar") || lower.contains("remover")) {
                sb.append("        view.").append(getter).append("().addActionListener(e -> excluir());\n");
            } else if (lower.contains("consultar") || lower.contains("pesquisar") || lower.contains("buscar")) {
                sb.append("        view.").append(getter).append("().addActionListener(e -> consultar());\n");
            } else if (lower.contains("limpar") || lower.contains("reset")) {
                sb.append("        view.").append(getter).append("().addActionListener(e -> limpar());\n");
            }
        }
        sb.append("    }\n\n");

        // Métodos canônicos
        sb.append("    private void salvar() {\n");
        sb.append("        // TODO: Implementar ação de salvar registro\n");
        sb.append("    }\n\n");

        sb.append("    private void excluir() {\n");
        sb.append("        // TODO: Implementar ação de excluir registro\n");
        sb.append("    }\n\n");

        sb.append("    private void consultar() {\n");
        sb.append("        // TODO: Implementar ação de consultar registros\n");
        sb.append("    }\n\n");

        sb.append("    private void limpar() {\n");
        sb.append("        // TODO: Implementar limpeza e reset dos campos da View\n");
        sb.append("    }\n\n");

        sb.append("    @Override\n");
        sb.append("    public void fechar() {\n");
        sb.append("        view.dispose();\n");
        sb.append("    }\n\n");

        sb.append("    @Override\n");
        sb.append("    public void setVisible(boolean mostrar) {\n");
        sb.append("        view.setVisible(mostrar);\n");
        sb.append("    }\n\n");

        sb.append("    public ").append(vwName).append(" getView() {\n");
        sb.append("        return view;\n");
        sb.append("    }\n");

        sb.append("}\n");
        return sb.toString();
    }

    public static void writeSourceFile(Map<String, Object> spec, File targetFile, String prName, String vwName, String packageName) throws Exception {
        String code = generateSource(spec, prName, vwName, packageName);
        try (OutputStream out = new FileOutputStream(targetFile);
             OutputStreamWriter writer = new OutputStreamWriter(out, ENCODING_WINDOWS_1252)) {
            writer.write(code);
        }
    }

    private static List<String> collectButtonGetters(Map<String, Object> spec) {
        List<String> list = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> comps = (List<Map<String, Object>>) spec.get("components");
        if (comps != null) {
            for (Map<String, Object> c : comps) {
                collectButtonsRecursive(c, list);
            }
        }
        return list;
    }

    private static void collectButtonsRecursive(Map<String, Object> comp, List<String> list) {
        String name = (String) comp.get("name");
        String clazz = (String) comp.get("class");
        if (name != null && ((clazz != null && clazz.endsWith("JButton")) || name.startsWith("jButton_") || name.startsWith("btn"))) {
            String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            list.add(getter);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) comp.get("children");
        if (children != null) {
            for (Map<String, Object> child : children) {
                collectButtonsRecursive(child, list);
            }
        }
    }
}
