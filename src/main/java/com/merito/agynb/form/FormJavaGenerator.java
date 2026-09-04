package com.merito.agynb.form;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gerador de código Java (.java) para companheiro de .form do NetBeans Matisse.
 * Suporta setViewportView() para JScrollPane, addTab() para JTabbedPane,
 * setLeft/RightComponent() para JSplitPane, DefaultTableModel para JTable,
 * ButtonGroup para JRadioButton, e encoding windows-1252.
 */
public class FormJavaGenerator {

    public static final Charset ENCODING_WINDOWS_1252 = Charset.forName("windows-1252");

    public static String generateSource(Map<String, Object> spec) {
        String packageName = (String) spec.getOrDefault("packageName", "com.merito.view");
        String className = (String) spec.getOrDefault("className", "MinhaTelaVW");
        String superClass = (String) spec.getOrDefault("superClass", "javax.swing.JDialog");
        String author = (String) spec.getOrDefault("author", "Antigravity Bridge Suite");
        String title = (String) spec.getOrDefault("title", "");

        List<ComponentDef> allComponents = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawComponents = (List<Map<String, Object>>) spec.get("components");
        if (rawComponents != null) {
            for (Map<String, Object> c : rawComponents) {
                collectComponents(c, allComponents);
            }
        }

        Set<String> buttonGroups = collectButtonGroups(spec);

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n");
        sb.append("import java.awt.Window;\n");
        sb.append("import java.awt.Dialog.ModalityType;\n\n");

        sb.append("/**\n");
        sb.append(" * ").append(className).append("\n");
        sb.append(" * @author ").append(author).append("\n");
        sb.append(" */\n");
        sb.append("public class ").append(className).append(" extends ").append(superClass).append(" {\n\n");

        // Construtores padrao JPosto
        sb.append("    public ").append(className).append("(Window window, ModalityType modal) {\n");
        sb.append("        super(window, modal);\n");
        sb.append("        init();\n");
        sb.append("    }\n\n");

        sb.append("    public ").append(className).append("(java.awt.Frame parent, boolean modal) {\n");
        sb.append("        super(parent, modal);\n");
        sb.append("        init();\n");
        sb.append("    }\n\n");

        sb.append("    public void init() {\n");
        sb.append("        initComponents();\n");
        sb.append("    }\n\n");

        // Getters para padrao MVP
        sb.append("    //<editor-fold desc=\"M\u00e9todos getters dos componentes\">\n");
        for (ComponentDef c : allComponents) {
            if (!c.className.endsWith("Separator") && (!c.isContainer || c.name.startsWith("jPanel_Content") || c.name.startsWith("jPanel_Aba") || c.className.endsWith("JTabbedPane"))) {
                String getterName = "get" + Character.toUpperCase(c.name.charAt(0)) + c.name.substring(1);
                sb.append("    public ").append(c.className).append(" ").append(getterName).append("() {\n");
                sb.append("        return ").append(c.name).append(";\n");
                sb.append("    }\n\n");
            }
        }
        sb.append("    //</editor-fold>\n\n");

        // Bloco protegido initComponents()
        sb.append("    @SuppressWarnings(\"unchecked\")\n");
        sb.append("    // <editor-fold defaultstate=\"collapsed\" desc=\"Generated Code\">//GEN-BEGIN:initComponents\n");
        sb.append("    private void initComponents() {\n\n");

        // 1. Instanciacao dos ButtonGroups
        for (String bg : buttonGroups) {
            sb.append("        ").append(bg).append(" = new javax.swing.ButtonGroup();\n");
        }

        // 2. Instanciacao de todos os componentes
        for (ComponentDef c : allComponents) {
            sb.append("        ").append(c.name).append(" = new ").append(c.className).append("();\n");
        }
        sb.append("\n");

        // 3. Configuracoes gerais do Dialog/Frame
        sb.append("        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);\n");
        if (title != null && !title.isEmpty()) {
            sb.append("        setTitle(\"").append(escapeJava(title)).append("\");\n");
        }
        sb.append("\n");

        // 4. Montagem hierarquica, propriedades e layouts
        if (rawComponents != null) {
            for (Map<String, Object> rootComp : rawComponents) {
                appendInitComponentHierarchy(sb, rootComp, "getContentPane()", null);
            }
        }

        sb.append("\n        pack();\n");
        sb.append("        setLocationRelativeTo(null);\n");
        sb.append("    }// </editor-fold>//GEN-END:initComponents\n\n");

        // Declaracao das variaveis
        sb.append("    // Variables declaration - do not modify//GEN-BEGIN:variables\n");
        for (String bg : buttonGroups) {
            sb.append("    private javax.swing.ButtonGroup ").append(bg).append(";\n");
        }
        for (ComponentDef c : allComponents) {
            sb.append("    private ").append(c.className).append(" ").append(c.name).append(";\n");
        }
        sb.append("    // End of variables declaration//GEN-END:variables\n");
        sb.append("}\n");

        return sb.toString();
    }

    public static void writeSourceFile(Map<String, Object> spec, File targetFile) throws Exception {
        String code = generateSource(spec);
        try (OutputStream out = new FileOutputStream(targetFile);
             OutputStreamWriter writer = new OutputStreamWriter(out, ENCODING_WINDOWS_1252)) {
            writer.write(code);
        }
    }

    private static void appendInitComponentHierarchy(StringBuilder sb, Map<String, Object> comp, String parentRef, String parentClass) {
        String name = (String) comp.get("name");
        String clazz = (String) comp.get("class");
        if (clazz == null || clazz.isEmpty()) clazz = "javax.swing.JPanel";
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) comp.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> constraints = (Map<String, Object>) comp.get("constraints");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) comp.get("children");

        // Propriedades comuns
        if (props != null) {
            for (Map.Entry<String, Object> e : props.entrySet()) {
                String p = e.getKey();
                Object vo = e.getValue();
                String v = String.valueOf(vo);
                if ("text".equals(p)) {
                    sb.append("        ").append(name).append(".setText(\"").append(escapeJava(v)).append("\");\n");
                } else if ("toolTipText".equals(p)) {
                    sb.append("        ").append(name).append(".setToolTipText(\"").append(escapeJava(v)).append("\");\n");
                } else if ("columns".equals(p)) {
                    sb.append("        ").append(name).append(".setColumns(").append(v).append(");\n");
                } else if ("rows".equals(p)) {
                    sb.append("        ").append(name).append(".setRows(").append(v).append(");\n");
                } else if ("enabled".equals(p)) {
                    sb.append("        ").append(name).append(".setEnabled(").append(v).append(");\n");
                } else if ("selected".equals(p)) {
                    sb.append("        ").append(name).append(".setSelected(").append(v).append(");\n");
                } else if ("stringPainted".equals(p)) {
                    sb.append("        ").append(name).append(".setStringPainted(").append(v).append(");\n");
                } else if ("value".equals(p)) {
                    sb.append("        ").append(name).append(".setValue(").append(v).append(");\n");
                } else if ("rollover".equals(p)) {
                    sb.append("        ").append(name).append(".setRollover(").append(v).append(");\n");
                } else if ("focusable".equals(p)) {
                    sb.append("        ").append(name).append(".setFocusable(").append(v).append(");\n");
                } else if ("dividerLocation".equals(p)) {
                    sb.append("        ").append(name).append(".setDividerLocation(").append(v).append(");\n");
                } else if ("border".equals(p)) {
                    if (v.toLowerCase().contains("titled")) {
                        String title = v.contains(":") ? v.substring(v.indexOf(':') + 1).trim() : "Dados";
                        sb.append("        ").append(name).append(".setBorder(javax.swing.BorderFactory.createTitledBorder(\"")
                          .append(escapeJava(title)).append("\"));\n");
                    } else if (v.toLowerCase().contains("etched")) {
                        sb.append("        ").append(name).append(".setBorder(javax.swing.BorderFactory.createEtchedBorder());\n");
                    }
                }
            }
        }

        // Modelos de dados especificos
        // 1. TableModel
        if (clazz.endsWith("JTable") && (comp.containsKey("columns") || (props != null && props.containsKey("columns")))) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cols = (List<Map<String, Object>>) (comp.containsKey("columns") ? comp.get("columns") : props.get("columns"));
            appendTableModelJava(sb, name, cols);
        }
        // 2. ComboBoxModel
        if (clazz.endsWith("JComboBox") && (comp.containsKey("items") || (props != null && props.containsKey("items")))) {
            @SuppressWarnings("unchecked")
            List<String> items = (List<String>) (comp.containsKey("items") ? comp.get("items") : props.get("items"));
            sb.append("        ").append(name).append(".setModel(new javax.swing.DefaultComboBoxModel(new String[] { ");
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append("\"").append(escapeJava(items.get(i))).append("\"");
            }
            sb.append(" }));\n");
        }
        // 3. ListModel
        if (clazz.endsWith("JList") && (comp.containsKey("items") || (props != null && props.containsKey("items")))) {
            @SuppressWarnings("unchecked")
            List<String> items = (List<String>) (comp.containsKey("items") ? comp.get("items") : props.get("items"));
            sb.append("        ").append(name).append(".setModel(new javax.swing.AbstractListModel() {\n");
            sb.append("            String[] strings = { ");
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append("\"").append(escapeJava(items.get(i))).append("\"");
            }
            sb.append(" };\n");
            sb.append("            public int getSize() { return strings.length; }\n");
            sb.append("            public Object getElementAt(int i) { return strings[i]; }\n");
            sb.append("        });\n");
        }
        // 4. SpinnerModel
        if (clazz.endsWith("JSpinner") && (comp.containsKey("spinnerModel") || comp.containsKey("min") || comp.containsKey("max"))) {
            int init = comp.containsKey("initial") ? ((Number) comp.get("initial")).intValue() : 1;
            int min = comp.containsKey("minimum") ? ((Number) comp.get("minimum")).intValue() : 0;
            int max = comp.containsKey("maximum") ? ((Number) comp.get("maximum")).intValue() : 100;
            int step = comp.containsKey("step") ? ((Number) comp.get("step")).intValue() : 1;
            sb.append("        ").append(name).append(".setModel(new javax.swing.SpinnerNumberModel(")
              .append(init).append(", ").append(min).append(", ").append(max).append(", ").append(step).append("));\n");
        }
        // 5. ButtonGroup associacao
        if (clazz.endsWith("JRadioButton") && (comp.containsKey("buttonGroup") || (props != null && props.containsKey("buttonGroup")))) {
            String bg = comp.containsKey("buttonGroup") ? String.valueOf(comp.get("buttonGroup")) : String.valueOf(props.get("buttonGroup"));
            sb.append("        ").append(bg).append(".add(").append(name).append(");\n");
        }

        // Layout de container
        if (children != null || clazz.endsWith("JPanel") || clazz.endsWith("JToolBar")) {
            String layout = (String) comp.getOrDefault("layout", "FlowLayout");
            if (clazz.endsWith("JToolBar")) {
                // JToolBar padrao BoxLayout
            } else if ("BorderLayout".equalsIgnoreCase(layout)) {
                sb.append("        ").append(name).append(".setLayout(new java.awt.BorderLayout());\n");
            } else if ("FlowLayout".equalsIgnoreCase(layout)) {
                sb.append("        ").append(name).append(".setLayout(new java.awt.FlowLayout());\n");
            } else if ("BoxLayout".equalsIgnoreCase(layout)) {
                sb.append("        ").append(name).append(".setLayout(new javax.swing.BoxLayout(").append(name).append(", javax.swing.BoxLayout.Y_AXIS));\n");
            } else if ("GridBagLayout".equalsIgnoreCase(layout)) {
                sb.append("        ").append(name).append(".setLayout(new java.awt.GridBagLayout());\n");
            } else if ("GridLayout".equalsIgnoreCase(layout)) {
                sb.append("        ").append(name).append(".setLayout(new java.awt.GridLayout());\n");
            }
        }

        // Adicao ao pai (Hierarquia especializada!)
        if (parentRef != null) {
            if (parentClass != null && parentClass.endsWith("JScrollPane")) {
                // REGRA DE OURO SWING: JScrollPane usa setViewportView()
                sb.append("        ").append(parentRef).append(".setViewportView(").append(name).append(");\n");
            } else if (parentClass != null && parentClass.endsWith("JTabbedPane")) {
                // REGRA JTabbedPane: usa addTab()
                String tabTitle = comp.containsKey("tabTitle") ? String.valueOf(comp.get("tabTitle")) : 
                        (constraints != null && constraints.containsKey("tabTitle") ? String.valueOf(constraints.get("tabTitle")) : name);
                sb.append("        ").append(parentRef).append(".addTab(\"").append(escapeJava(tabTitle)).append("\", ").append(name).append(");\n");
            } else if (parentClass != null && parentClass.endsWith("JSplitPane")) {
                // REGRA JSplitPane: setLeftComponent / setRightComponent
                String pos = constraints != null && constraints.containsKey("position") ? String.valueOf(constraints.get("position")) : "left";
                if ("right".equalsIgnoreCase(pos) || "bottom".equalsIgnoreCase(pos)) {
                    sb.append("        ").append(parentRef).append(".setRightComponent(").append(name).append(");\n");
                } else {
                    sb.append("        ").append(parentRef).append(".setLeftComponent(").append(name).append(");\n");
                }
            } else if (constraints != null && constraints.containsKey("direction")) {
                String dir = (String) constraints.get("direction");
                sb.append("        ").append(parentRef).append(".add(").append(name)
                  .append(", java.awt.BorderLayout.").append(dir.toUpperCase()).append(");\n");
            } else if (constraints != null && (constraints.containsKey("gridx") || constraints.containsKey("gridX"))) {
                appendGridBagAdd(sb, name, parentRef, constraints);
            } else {
                sb.append("        ").append(parentRef).append(".add(").append(name).append(");\n");
            }
        }

        // Filhos recursivos
        if (children != null) {
            for (Map<String, Object> child : children) {
                appendInitComponentHierarchy(sb, child, name, clazz);
            }
        }
    }

    private static void appendTableModelJava(StringBuilder sb, String name, List<Map<String, Object>> cols) {
        sb.append("        ").append(name).append(".setModel(new javax.swing.table.DefaultTableModel(\n");
        sb.append("            new Object [][] {},\n");
        sb.append("            new String [] { ");
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(escapeJava(String.valueOf(cols.get(i).getOrDefault("title", "Col" + i)))).append("\"");
        }
        sb.append(" }\n");
        sb.append("        ) {\n");
        sb.append("            Class[] types = new Class [] { ");
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sb.append(", ");
            String t = String.valueOf(cols.get(i).getOrDefault("type", "java.lang.Object"));
            sb.append(t).append(".class");
        }
        sb.append(" };\n");
        sb.append("            boolean[] canEdit = new boolean [] { ");
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sb.append(", ");
            boolean ce = Boolean.parseBoolean(String.valueOf(cols.get(i).getOrDefault("editable", "true")));
            sb.append(ce);
        }
        sb.append(" };\n");
        sb.append("            public Class getColumnClass(int columnIndex) { return types [columnIndex]; }\n");
        sb.append("            public boolean isCellEditable(int rowIndex, int columnIndex) { return canEdit [columnIndex]; }\n");
        sb.append("        });\n");
    }

    private static void appendGridBagAdd(StringBuilder sb, String name, String parentRef, Map<String, Object> c) {
        sb.append("        {\n");
        sb.append("            java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();\n");
        if (c.containsKey("gridx")) sb.append("            gbc.gridx = ").append(c.get("gridx")).append(";\n");
        if (c.containsKey("gridy")) sb.append("            gbc.gridy = ").append(c.get("gridy")).append(";\n");
        if (c.containsKey("gridwidth")) sb.append("            gbc.gridwidth = ").append(c.get("gridwidth")).append(";\n");
        if (c.containsKey("gridheight")) sb.append("            gbc.gridheight = ").append(c.get("gridheight")).append(";\n");
        if (c.containsKey("fill")) sb.append("            gbc.fill = ").append(c.get("fill")).append(";\n");
        if (c.containsKey("weightx")) sb.append("            gbc.weightx = ").append(c.get("weightx")).append(";\n");
        if (c.containsKey("weighty")) sb.append("            gbc.weighty = ").append(c.get("weighty")).append(";\n");
        sb.append("            ").append(parentRef).append(".add(").append(name).append(", gbc);\n");
        sb.append("        }\n");
    }

    private static void collectComponents(Map<String, Object> comp, List<ComponentDef> list) {
        String name = (String) comp.get("name");
        String clazz = (String) comp.get("class");
        if (clazz == null || clazz.isEmpty()) clazz = "javax.swing.JPanel";
        if (clazz.endsWith("JToolBar$Separator") || clazz.endsWith("JToolBar.Separator")) {
            clazz = "javax.swing.JToolBar.Separator";
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) comp.get("children");
        boolean isContainer = children != null 
                || clazz.endsWith("JPanel") 
                || clazz.endsWith("JScrollPane") 
                || clazz.endsWith("JTabbedPane") 
                || clazz.endsWith("JSplitPane") 
                || clazz.endsWith("JToolBar");

        list.add(new ComponentDef(name, clazz, isContainer));
        if (children != null) {
            for (Map<String, Object> child : children) {
                collectComponents(child, list);
            }
        }
    }

    private static Set<String> collectButtonGroups(Map<String, Object> spec) {
        Set<String> groups = new LinkedHashSet<>();
        if (spec.containsKey("buttonGroups")) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) spec.get("buttonGroups");
            if (list != null) groups.addAll(list);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> comps = (List<Map<String, Object>>) spec.get("components");
        if (comps != null) {
            for (Map<String, Object> c : comps) {
                collectGroupsRecursive(c, groups);
            }
        }
        return groups;
    }

    private static void collectGroupsRecursive(Map<String, Object> comp, Set<String> groups) {
        if (comp.containsKey("buttonGroup")) {
            groups.add(String.valueOf(comp.get("buttonGroup")));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) comp.get("properties");
        if (props != null && props.containsKey("buttonGroup")) {
            groups.add(String.valueOf(props.get("buttonGroup")));
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) comp.get("children");
        if (children != null) {
            for (Map<String, Object> child : children) {
                collectGroupsRecursive(child, groups);
            }
        }
    }

    private static String escapeJava(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static class ComponentDef {
        final String name;
        final String className;
        final boolean isContainer;

        ComponentDef(String name, String className, boolean isContainer) {
            this.name = name;
            this.className = className;
            this.isContainer = isContainer;
        }
    }
}
