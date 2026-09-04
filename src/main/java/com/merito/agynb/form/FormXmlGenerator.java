package com.merito.agynb.form;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gerador determinístico de arquivos XML .form compatíveis com o NetBeans Matisse.
 * Suporta Containers Complexos (JScrollPane, JTabbedPane, JSplitPane, JToolBar),
 * Layouts avançados (Border, Flow, GridBag, Absolute, Grid, Box),
 * Modelos (TableModel, ComboBoxModel, ListModel, SpinnerModel, ButtonGroup)
 * e Propriedades ricas (Color, Font, Dimension, Border).
 */
public class FormXmlGenerator {

    public static String generateXml(Map<String, Object> spec) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n\n");

        String formType = (String) spec.getOrDefault("formType", "org.netbeans.modules.form.forminfo.JDialogFormInfo");
        String version = (String) spec.getOrDefault("version", "1.3");
        String title = (String) spec.getOrDefault("title", "");

        sb.append("<Form version=\"").append(version).append("\" maxVersion=\"1.9\"")
          .append(" type=\"").append(formType).append("\">\n");

        // 1. NonVisualComponents (ButtonGroup, etc.)
        Set<String> buttonGroups = collectButtonGroups(spec);
        if (!buttonGroups.isEmpty()) {
            sb.append("  <NonVisualComponents>\n");
            for (String bg : buttonGroups) {
                sb.append("    <Component class=\"javax.swing.ButtonGroup\" name=\"").append(bg).append("\">\n");
                sb.append("    </Component>\n");
            }
            sb.append("  </NonVisualComponents>\n");
        }

        // 2. Properties do Form
        sb.append("  <Properties>\n");
        sb.append("    <Property name=\"defaultCloseOperation\" type=\"int\" value=\"2\"/>\n");
        if (title != null && !title.isEmpty()) {
            sb.append("    <Property name=\"title\" type=\"java.lang.String\" value=\"").append(escapeXml(title)).append("\"/>\n");
        }
        sb.append("  </Properties>\n");

        // 3. SyntheticProperties
        sb.append("  <SyntheticProperties>\n");
        sb.append("    <SyntheticProperty name=\"formSizePolicy\" type=\"int\" value=\"1\"/>\n");
        sb.append("    <SyntheticProperty name=\"generateCenter\" type=\"boolean\" value=\"true\"/>\n");
        sb.append("  </SyntheticProperties>\n");

        // 4. AuxValues padrao Matisse
        sb.append("  <AuxValues>\n");
        sb.append("    <AuxValue name=\"FormSettings_autoResourcing\" type=\"java.lang.Integer\" value=\"0\"/>\n");
        sb.append("    <AuxValue name=\"FormSettings_autoSetComponentName\" type=\"java.lang.Boolean\" value=\"false\"/>\n");
        sb.append("    <AuxValue name=\"FormSettings_generateFQN\" type=\"java.lang.Boolean\" value=\"true\"/>\n");
        sb.append("    <AuxValue name=\"FormSettings_generateMnemonicsCode\" type=\"java.lang.Boolean\" value=\"false\"/>\n");
        sb.append("    <AuxValue name=\"FormSettings_i18nAutoMode\" type=\"java.lang.Boolean\" value=\"false\"/>\n");
        sb.append("    <AuxValue name=\"FormSettings_layoutCodeTarget\" type=\"java.lang.Integer\" value=\"1\"/>\n");
        sb.append("    <AuxValue name=\"FormSettings_listenerGenerationStyle\" type=\"java.lang.Integer\" value=\"0\"/>\n");
        sb.append("    <AuxValue name=\"FormSettings_variablesLocal\" type=\"java.lang.Boolean\" value=\"false\"/>\n");
        sb.append("    <AuxValue name=\"FormSettings_variablesModifier\" type=\"java.lang.Integer\" value=\"2\"/>\n");
        sb.append("  </AuxValues>\n\n");

        // 5. Layout Raiz
        String rootLayout = (String) spec.getOrDefault("layout", "BorderLayout");
        appendLayout(sb, rootLayout, null, "  ");

        // 6. SubComponents
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) spec.get("components");
        sb.append("  <SubComponents>\n");
        if (components != null) {
            for (Map<String, Object> comp : components) {
                appendComponent(sb, comp, "    ", null);
            }
        }
        sb.append("  </SubComponents>\n");

        sb.append("</Form>\n");
        return sb.toString();
    }

    public static void writeFormFile(Map<String, Object> spec, File targetFile) throws Exception {
        String xml = generateXml(spec);
        try (OutputStream out = new FileOutputStream(targetFile);
             OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writer.write(xml);
        }
    }

    private static void appendComponent(StringBuilder sb, Map<String, Object> comp, String indent, String parentClass) {
        String name = (String) comp.get("name");
        String clazz = (String) comp.get("class");
        if (clazz == null || clazz.isEmpty()) {
            clazz = "javax.swing.JPanel";
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) comp.get("children");
        boolean isContainer = children != null 
                || clazz.endsWith("JPanel") 
                || clazz.endsWith("JScrollPane") 
                || clazz.endsWith("JTabbedPane") 
                || clazz.endsWith("JSplitPane") 
                || clazz.endsWith("JToolBar");

        boolean isToolBarSeparator = clazz.endsWith("JToolBar$Separator") || clazz.endsWith("JToolBar.Separator");
        String tag = isToolBarSeparator ? "Component" : (isContainer ? "Container" : "Component");
        String normClazz = isToolBarSeparator ? "javax.swing.JToolBar$Separator" : clazz;

        sb.append(indent).append("<").append(tag).append(" class=\"").append(normClazz)
          .append("\" name=\"").append(name).append("\">\n");

        // AuxValue autoScrollPane
        if (clazz.endsWith("JScrollPane")) {
            sb.append(indent).append("  <AuxValues>\n");
            sb.append(indent).append("    <AuxValue name=\"autoScrollPane\" type=\"java.lang.Boolean\" value=\"true\"/>\n");
            sb.append(indent).append("  </AuxValues>\n");
        }

        // Properties
        appendProperties(sb, comp, indent + "  ");

        // Constraints
        appendComponentConstraints(sb, comp, parentClass, indent + "  ");

        // Container Layout e Filhos
        if (isContainer) {
            appendContainerLayout(sb, comp, clazz, indent + "  ");

            sb.append(indent).append("  <SubComponents>\n");
            if (children != null) {
                for (Map<String, Object> child : children) {
                    appendComponent(sb, child, indent + "    ", clazz);
                }
            }
            sb.append(indent).append("  </SubComponents>\n");
        }

        sb.append(indent).append("</").append(tag).append(">\n");
    }

    private static void appendProperties(StringBuilder sb, Map<String, Object> comp, String indent) {
        String clazz = (String) comp.get("class");
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) comp.get("properties");

        // Check if there are any special models or properties to output
        boolean hasProps = props != null && !props.isEmpty();
        boolean hasButtonGroup = comp.containsKey("buttonGroup") || (props != null && props.containsKey("buttonGroup"));
        boolean hasTableColumns = clazz != null && clazz.endsWith("JTable") && (comp.containsKey("columns") || (props != null && props.containsKey("columns")));
        boolean hasComboItems = clazz != null && clazz.endsWith("JComboBox") && (comp.containsKey("items") || (props != null && props.containsKey("items")));
        boolean hasListItems = clazz != null && clazz.endsWith("JList") && (comp.containsKey("items") || (props != null && props.containsKey("items")));
        boolean hasSpinner = clazz != null && clazz.endsWith("JSpinner") && (comp.containsKey("spinnerModel") || comp.containsKey("min") || comp.containsKey("max"));

        if (!hasProps && !hasButtonGroup && !hasTableColumns && !hasComboItems && !hasListItems && !hasSpinner) {
            return;
        }

        sb.append(indent).append("<Properties>\n");

        // 1. ButtonGroup
        if (hasButtonGroup) {
            String bgName = comp.containsKey("buttonGroup") ? String.valueOf(comp.get("buttonGroup")) : String.valueOf(props.get("buttonGroup"));
            sb.append(indent).append("  <Property name=\"buttonGroup\" type=\"javax.swing.ButtonGroup\" editor=\"org.netbeans.modules.form.RADComponent$ButtonGroupPropertyEditor\">\n");
            sb.append(indent).append("    <ComponentRef name=\"").append(bgName).append("\"/>\n");
            sb.append(indent).append("  </Property>\n");
        }

        // 2. TableModel (JTable)
        if (hasTableColumns) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> columns = (List<Map<String, Object>>) (comp.containsKey("columns") ? comp.get("columns") : props.get("columns"));
            appendTableProperty(sb, columns, indent + "  ");
        }

        // 3. ComboBoxModel (JComboBox com index no StringItem)
        if (hasComboItems) {
            @SuppressWarnings("unchecked")
            List<String> items = (List<String>) (comp.containsKey("items") ? comp.get("items") : props.get("items"));
            appendComboListProperty(sb, "model", "javax.swing.ComboBoxModel", "org.netbeans.modules.form.editors2.ComboBoxModelEditor", items, indent + "  ");
        }

        // 4. ListModel (JList com index no StringItem)
        if (hasListItems) {
            @SuppressWarnings("unchecked")
            List<String> items = (List<String>) (comp.containsKey("items") ? comp.get("items") : props.get("items"));
            appendComboListProperty(sb, "model", "javax.swing.ListModel", "org.netbeans.modules.form.editors2.ListModelEditor", items, indent + "  ");
        }

        // 5. SpinnerModel (JSpinner)
        if (hasSpinner) {
            int init = comp.containsKey("initial") ? ((Number) comp.get("initial")).intValue() : 1;
            int min = comp.containsKey("minimum") ? ((Number) comp.get("minimum")).intValue() : 0;
            int max = comp.containsKey("maximum") ? ((Number) comp.get("maximum")).intValue() : 100;
            int step = comp.containsKey("step") ? ((Number) comp.get("step")).intValue() : 1;
            sb.append(indent).append("  <Property name=\"model\" type=\"javax.swing.SpinnerModel\" editor=\"org.netbeans.modules.form.editors2.SpinnerModelEditor\">\n");
            sb.append(indent).append("    <SpinnerModel initial=\"").append(init).append("\" maximum=\"").append(max)
              .append("\" minimum=\"").append(min).append("\" numberType=\"java.lang.Integer\" stepSize=\"").append(step).append("\" type=\"number\"/>\n");
            sb.append(indent).append("  </Property>\n");
        }

        // 6. Demais propriedades normais e ricas
        if (props != null) {
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                String pName = entry.getKey();
                if ("buttonGroup".equals(pName) || "columns".equals(pName) || "items".equals(pName)) {
                    continue; // Ja tratados
                }
                Object valObj = entry.getValue();
                appendRichProperty(sb, pName, valObj, indent + "  ");
            }
        }

        sb.append(indent).append("</Properties>\n");
    }

    private static void appendRichProperty(StringBuilder sb, String pName, Object valObj, String indent) {
        String val = String.valueOf(valObj);

        // Border
        if ("border".equalsIgnoreCase(pName)) {
            if (val.toLowerCase().contains("titled")) {
                String borderTitle = val.contains(":") ? val.substring(val.indexOf(':') + 1).trim() : "Dados";
                sb.append(indent).append("<Property name=\"border\" type=\"javax.swing.border.Border\" editor=\"org.netbeans.modules.form.editors2.BorderEditor\">\n");
                sb.append(indent).append("  <Border info=\"org.netbeans.modules.form.compat2.border.TitledBorderInfo\">\n");
                sb.append(indent).append("    <TitledBorder title=\"").append(escapeXml(borderTitle)).append("\"/>\n");
                sb.append(indent).append("  </Border>\n");
                sb.append(indent).append("</Property>\n");
            } else if (val.toLowerCase().contains("etched")) {
                sb.append(indent).append("<Property name=\"border\" type=\"javax.swing.border.Border\" editor=\"org.netbeans.modules.form.editors2.BorderEditor\">\n");
                sb.append(indent).append("  <Border info=\"org.netbeans.modules.form.compat2.border.EtchedBorderInfo\">\n");
                sb.append(indent).append("    <EtchetBorder/>\n");
                sb.append(indent).append("  </Border>\n");
                sb.append(indent).append("</Property>\n");
            } else if (val.toLowerCase().contains("bevel")) {
                sb.append(indent).append("<Property name=\"border\" type=\"javax.swing.border.Border\" editor=\"org.netbeans.modules.form.editors2.BorderEditor\">\n");
                sb.append(indent).append("  <Border info=\"org.netbeans.modules.form.compat2.border.BevelBorderInfo\">\n");
                sb.append(indent).append("    <BevelBorder bevelType=\"1\"/>\n");
                sb.append(indent).append("  </Border>\n");
                sb.append(indent).append("</Property>\n");
            }
            return;
        }

        // Dimension (preferredSize, minimumSize, maximumSize)
        if (pName.endsWith("Size") && valObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Number> dims = (List<Number>) valObj;
            if (dims.size() >= 2) {
                sb.append(indent).append("<Property name=\"").append(pName).append("\" type=\"java.awt.Dimension\" editor=\"org.netbeans.beaninfo.editors.DimensionEditor\">\n");
                sb.append(indent).append("  <Dimension value=\"[").append(dims.get(0)).append(", ").append(dims.get(1)).append("]\"/>\n");
                sb.append(indent).append("</Property>\n");
                return;
            }
        }

        // Color
        if (("foreground".equalsIgnoreCase(pName) || "background".equalsIgnoreCase(pName)) && valObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Number> rgb = (List<Number>) valObj;
            if (rgb.size() >= 3) {
                sb.append(indent).append("<Property name=\"").append(pName).append("\" type=\"java.awt.Color\" editor=\"org.netbeans.beaninfo.editors.ColorEditor\">\n");
                sb.append(indent).append("  <Color blue=\"").append(rgb.get(2)).append("\" green=\"").append(rgb.get(1))
                  .append("\" red=\"").append(rgb.get(0)).append("\" type=\"rgb\"/>\n");
                sb.append(indent).append("</Property>\n");
                return;
            }
        }

        // Font
        if ("font".equalsIgnoreCase(pName) && valObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> fontMap = (Map<String, Object>) valObj;
            String fName = (String) fontMap.getOrDefault("name", "Ubuntu");
            int fSize = fontMap.containsKey("size") ? ((Number) fontMap.get("size")).intValue() : 12;
            int fStyle = fontMap.containsKey("style") ? ((Number) fontMap.get("style")).intValue() : 0;
            sb.append(indent).append("<Property name=\"font\" type=\"java.awt.Font\" editor=\"org.netbeans.beaninfo.editors.FontEditor\">\n");
            sb.append(indent).append("  <Font name=\"").append(escapeXml(fName)).append("\" size=\"").append(fSize).append("\" style=\"").append(fStyle).append("\"/>\n");
            sb.append(indent).append("</Property>\n");
            return;
        }

        // Primitivos e String padrao
        String pType = inferPropertyType(pName, valObj);
        sb.append(indent).append("<Property name=\"").append(pName).append("\" type=\"").append(pType).append("\" value=\"")
          .append(escapeXml(val)).append("\"/>\n");
    }

    private static void appendTableProperty(StringBuilder sb, List<Map<String, Object>> columns, String indent) {
        int count = columns != null ? columns.size() : 4;
        sb.append(indent).append("<Property name=\"model\" type=\"javax.swing.table.TableModel\" editor=\"org.netbeans.modules.form.editors2.TableModelEditor\">\n");
        sb.append(indent).append("  <Table columnCount=\"").append(count).append("\" rowCount=\"0\">\n");
        if (columns != null) {
            for (Map<String, Object> col : columns) {
                String title = (String) col.getOrDefault("title", "Coluna");
                String type = (String) col.getOrDefault("type", "java.lang.Object");
                boolean editable = Boolean.parseBoolean(String.valueOf(col.getOrDefault("editable", "true")));
                sb.append(indent).append("    <Column editable=\"").append(editable).append("\" title=\"").append(escapeXml(title))
                  .append("\" type=\"").append(type).append("\"/>\n");
            }
        }
        sb.append(indent).append("  </Table>\n");
        sb.append(indent).append("</Property>\n");
    }

    private static void appendComboListProperty(StringBuilder sb, String pName, String pType, String editor, List<String> items, String indent) {
        int count = items != null ? items.size() : 0;
        sb.append(indent).append("<Property name=\"").append(pName).append("\" type=\"").append(pType).append("\" editor=\"").append(editor).append("\">\n");
        sb.append(indent).append("  <StringArray count=\"").append(count).append("\">\n");
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                sb.append(indent).append("    <StringItem index=\"").append(i).append("\" value=\"").append(escapeXml(items.get(i))).append("\"/>\n");
            }
        }
        sb.append(indent).append("  </StringArray>\n");
        sb.append(indent).append("</Property>\n");
    }

    private static void appendComponentConstraints(StringBuilder sb, Map<String, Object> comp, String parentClass, String indent) {
        @SuppressWarnings("unchecked")
        Map<String, Object> constraints = (Map<String, Object>) comp.get("constraints");

        // 1. JTabbedPane Child Constraints
        if (parentClass != null && parentClass.endsWith("JTabbedPane")) {
            String tabTitle = comp.containsKey("tabTitle") ? String.valueOf(comp.get("tabTitle")) : 
                    (constraints != null && constraints.containsKey("tabTitle") ? String.valueOf(constraints.get("tabTitle")) : String.valueOf(comp.get("name")));
            sb.append(indent).append("<Constraints>\n");
            sb.append(indent).append("  <Constraint layoutClass=\"org.netbeans.modules.form.compat2.layouts.support.JTabbedPaneSupportLayout\" value=\"org.netbeans.modules.form.compat2.layouts.support.JTabbedPaneSupportLayout$JTabbedPaneConstraintsDescription\">\n");
            sb.append(indent).append("    <JTabbedPaneConstraints tabName=\"").append(escapeXml(tabTitle)).append("\">\n");
            sb.append(indent).append("      <Property name=\"tabTitle\" type=\"java.lang.String\" value=\"").append(escapeXml(tabTitle)).append("\"/>\n");
            sb.append(indent).append("    </JTabbedPaneConstraints>\n");
            sb.append(indent).append("  </Constraint>\n");
            sb.append(indent).append("</Constraints>\n");
            return;
        }

        // 2. JSplitPane Child Constraints
        if (parentClass != null && parentClass.endsWith("JSplitPane")) {
            String pos = constraints != null && constraints.containsKey("position") ? String.valueOf(constraints.get("position")) : "left";
            sb.append(indent).append("<Constraints>\n");
            sb.append(indent).append("  <Constraint layoutClass=\"org.netbeans.modules.form.compat2.layouts.support.JSplitPaneSupportLayout\" value=\"org.netbeans.modules.form.compat2.layouts.support.JSplitPaneSupportLayout$JSplitPaneConstraintsDescription\">\n");
            sb.append(indent).append("    <JSplitPaneConstraints position=\"").append(pos).append("\"/>\n");
            sb.append(indent).append("  </Constraint>\n");
            sb.append(indent).append("</Constraints>\n");
            return;
        }

        // 3. Constraints gerais (Border, GridBag, Absolute)
        if (constraints != null && !constraints.isEmpty()) {
            appendConstraints(sb, constraints, indent);
        }
    }

    private static void appendContainerLayout(StringBuilder sb, Map<String, Object> comp, String clazz, String indent) {
        if (clazz.endsWith("JScrollPane")) {
            sb.append(indent).append("<Layout class=\"org.netbeans.modules.form.compat2.layouts.support.JScrollPaneSupportLayout\"/>\n");
            return;
        }
        if (clazz.endsWith("JTabbedPane")) {
            sb.append(indent).append("<Layout class=\"org.netbeans.modules.form.compat2.layouts.support.JTabbedPaneSupportLayout\"/>\n");
            return;
        }
        if (clazz.endsWith("JSplitPane")) {
            sb.append(indent).append("<Layout class=\"org.netbeans.modules.form.compat2.layouts.support.JSplitPaneSupportLayout\"/>\n");
            return;
        }
        if (clazz.endsWith("JToolBar")) {
            sb.append(indent).append("<Layout class=\"org.netbeans.modules.form.compat2.layouts.DesignBoxLayout\"/>\n");
            return;
        }

        String layout = (String) comp.getOrDefault("layout", "FlowLayout");
        @SuppressWarnings("unchecked")
        Map<String, Object> layoutProps = (Map<String, Object>) comp.get("layoutProperties");
        appendLayout(sb, layout, layoutProps, indent);
    }

    private static void appendLayout(StringBuilder sb, String layoutName, Map<String, Object> layoutProps, String indent) {
        String netbeansLayoutClass = getNetBeansLayoutClass(layoutName);
        sb.append(indent).append("<Layout class=\"").append(netbeansLayoutClass).append("\"");

        if (layoutProps == null || layoutProps.isEmpty()) {
            sb.append("/>\n");
        } else {
            sb.append(">\n");
            for (Map.Entry<String, Object> entry : layoutProps.entrySet()) {
                sb.append(indent).append("  <Property name=\"").append(entry.getKey())
                  .append("\" type=\"int\" value=\"").append(entry.getValue()).append("\"/>\n");
            }
            sb.append(indent).append("</Layout>\n");
        }
    }

    private static void appendConstraints(StringBuilder sb, Map<String, Object> constraints, String indent) {
        // BorderLayout
        String dir = (String) constraints.get("direction");
        if (dir != null) {
            sb.append(indent).append("<Constraints>\n");
            sb.append(indent).append("  <Constraint layoutClass=\"org.netbeans.modules.form.compat2.layouts.DesignBorderLayout\" value=\"org.netbeans.modules.form.compat2.layouts.DesignBorderLayout$BorderConstraintsDescription\">\n");
            sb.append(indent).append("    <BorderConstraints direction=\"").append(dir).append("\"/>\n");
            sb.append(indent).append("  </Constraint>\n");
            sb.append(indent).append("</Constraints>\n");
            return;
        }

        // GridBagLayout
        if (constraints.containsKey("gridx") || constraints.containsKey("gridX")) {
            int gridx = getIntConstraint(constraints, "gridx", "gridX", 0);
            int gridy = getIntConstraint(constraints, "gridy", "gridY", 0);
            int gridwidth = getIntConstraint(constraints, "gridwidth", "gridWidth", 1);
            int gridheight = getIntConstraint(constraints, "gridheight", "gridHeight", 1);
            int fill = getIntConstraint(constraints, "fill", "fill", 0);
            double weightx = getDoubleConstraint(constraints, "weightx", "weightX", 0.0);
            double weighty = getDoubleConstraint(constraints, "weighty", "weightY", 0.0);
            int anchor = getIntConstraint(constraints, "anchor", "anchor", 10);
            int top = getIntConstraint(constraints, "top", "insetsTop", 0);
            int left = getIntConstraint(constraints, "left", "insetsLeft", 0);
            int bottom = getIntConstraint(constraints, "bottom", "insetsBottom", 0);
            int right = getIntConstraint(constraints, "right", "insetsRight", 0);

            sb.append(indent).append("<Constraints>\n");
            sb.append(indent).append("  <Constraint layoutClass=\"org.netbeans.modules.form.compat2.layouts.DesignGridBagLayout\" value=\"org.netbeans.modules.form.compat2.layouts.DesignGridBagLayout$GridBagConstraintsDescription\">\n");
            sb.append(indent).append("    <GridBagConstraints gridX=\"").append(gridx).append("\" gridY=\"").append(gridy)
              .append("\" gridWidth=\"").append(gridwidth).append("\" gridHeight=\"").append(gridheight)
              .append("\" fill=\"").append(fill).append("\" weightX=\"").append(weightx).append("\" weightY=\"").append(weighty)
              .append("\" anchor=\"").append(anchor).append("\" insetsTop=\"").append(top).append("\" insetsLeft=\"").append(left)
              .append("\" insetsBottom=\"").append(bottom).append("\" insetsRight=\"").append(right).append("\"/>\n");
            sb.append(indent).append("  </Constraint>\n");
            sb.append(indent).append("</Constraints>\n");
            return;
        }

        // AbsoluteLayout
        if (constraints.containsKey("x") || constraints.containsKey("y")) {
            int x = getIntConstraint(constraints, "x", "x", 0);
            int y = getIntConstraint(constraints, "y", "y", 0);
            int w = getIntConstraint(constraints, "width", "w", -1);
            int h = getIntConstraint(constraints, "height", "h", -1);

            sb.append(indent).append("<Constraints>\n");
            sb.append(indent).append("  <Constraint layoutClass=\"org.netbeans.modules.form.compat2.layouts.DesignAbsoluteLayout\" value=\"org.netbeans.modules.form.compat2.layouts.DesignAbsoluteLayout$AbsoluteConstraintsDescription\">\n");
            sb.append(indent).append("    <AbsoluteConstraints x=\"").append(x).append("\" y=\"").append(y)
              .append("\" width=\"").append(w).append("\" height=\"").append(h).append("\"/>\n");
            sb.append(indent).append("  </Constraint>\n");
            sb.append(indent).append("</Constraints>\n");
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

    private static int getIntConstraint(Map<String, Object> m, String k1, String k2, int def) {
        Object val = m.containsKey(k1) ? m.get(k1) : m.get(k2);
        return val instanceof Number ? ((Number) val).intValue() : def;
    }

    private static double getDoubleConstraint(Map<String, Object> m, String k1, String k2, double def) {
        Object val = m.containsKey(k1) ? m.get(k1) : m.get(k2);
        return val instanceof Number ? ((Number) val).doubleValue() : def;
    }

    private static String getNetBeansLayoutClass(String name) {
        if ("BorderLayout".equalsIgnoreCase(name)) return "org.netbeans.modules.form.compat2.layouts.DesignBorderLayout";
        if ("FlowLayout".equalsIgnoreCase(name)) return "org.netbeans.modules.form.compat2.layouts.DesignFlowLayout";
        if ("BoxLayout".equalsIgnoreCase(name)) return "org.netbeans.modules.form.compat2.layouts.DesignBoxLayout";
        if ("AbsoluteLayout".equalsIgnoreCase(name)) return "org.netbeans.modules.form.compat2.layouts.DesignAbsoluteLayout";
        if ("GridLayout".equalsIgnoreCase(name)) return "org.netbeans.modules.form.compat2.layouts.DesignGridLayout";
        if ("GridBagLayout".equalsIgnoreCase(name)) return "org.netbeans.modules.form.compat2.layouts.DesignGridBagLayout";
        if ("CardLayout".equalsIgnoreCase(name)) return "org.netbeans.modules.form.compat2.layouts.DesignCardLayout";
        return name.contains(".") ? name : "org.netbeans.modules.form.compat2.layouts.Design" + name;
    }

    private static String inferPropertyType(String name, Object val) {
        if (val instanceof Boolean) return "boolean";
        if (val instanceof Integer) return "int";
        if ("columns".equalsIgnoreCase(name) || "rows".equalsIgnoreCase(name) || "value".equalsIgnoreCase(name)
                || "dividerLocation".equalsIgnoreCase(name) || "majorTickSpacing".equalsIgnoreCase(name)) return "int";
        if ("enabled".equalsIgnoreCase(name) || "visible".equalsIgnoreCase(name) || "editable".equalsIgnoreCase(name)
                || "selected".equalsIgnoreCase(name) || "rollover".equalsIgnoreCase(name)
                || "stringPainted".equalsIgnoreCase(name) || "paintTicks".equalsIgnoreCase(name)
                || "paintLabels".equalsIgnoreCase(name) || "focusable".equalsIgnoreCase(name)) return "boolean";
        return "java.lang.String";
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
