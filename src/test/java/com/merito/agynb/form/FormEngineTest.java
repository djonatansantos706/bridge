package com.merito.agynb.form;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class FormEngineTest {

    private static final String SAMPLE_FORM = 
        "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
        "<Form version=\"1.3\" maxVersion=\"1.3\" type=\"org.netbeans.modules.form.forminfo.JDialogFormInfo\">\n" +
        "  <Properties>\n" +
        "    <Property name=\"defaultCloseOperation\" type=\"int\" value=\"2\"/>\n" +
        "    <Property name=\"title\" type=\"java.lang.String\" value=\"Tela Teste\"/>\n" +
        "  </Properties>\n" +
        "  <Layout class=\"org.netbeans.modules.form.compat2.layouts.DesignBorderLayout\"/>\n" +
        "  <SubComponents>\n" +
        "    <Container class=\"javax.swing.JPanel\" name=\"jPanel_Header\">\n" +
        "      <Constraints>\n" +
        "        <Constraint layoutClass=\"org.netbeans.modules.form.compat2.layouts.DesignBorderLayout\" value=\"org.netbeans.modules.form.compat2.layouts.DesignBorderLayout$BorderConstraintsDescription\">\n" +
        "          <BorderConstraints direction=\"North\"/>\n" +
        "        </Constraint>\n" +
        "      </Constraints>\n" +
        "      <Layout class=\"org.netbeans.modules.form.compat2.layouts.DesignFlowLayout\"/>\n" +
        "      <SubComponents>\n" +
        "        <Component class=\"javax.swing.JButton\" name=\"jButton_Fechar\">\n" +
        "          <Properties>\n" +
        "            <Property name=\"text\" type=\"java.lang.String\" value=\"Fechar\"/>\n" +
        "            <Property name=\"toolTipText\" type=\"java.lang.String\" value=\"Fecha a tela\"/>\n" +
        "          </Properties>\n" +
        "          <Events>\n" +
        "            <EventHandler event=\"actionPerformed\" listener=\"java.awt.event.ActionListener\" parameters=\"java.awt.event.ActionEvent\" handler=\"jButton_FecharActionPerformed\"/>\n" +
        "          </Events>\n" +
        "        </Component>\n" +
        "      </SubComponents>\n" +
        "    </Container>\n" +
        "  </SubComponents>\n" +
        "</Form>";

    @Test
    public void testParseForm() throws Exception {
        Map<String, Object> form = FormXmlReader.parseForm(new ByteArrayInputStream(SAMPLE_FORM.getBytes(StandardCharsets.UTF_8)));
        Assert.assertNotNull(form);
        Assert.assertEquals("1.3", form.get("version"));
        Assert.assertEquals("BorderLayout", form.get("layoutName"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> comps = (List<Map<String, Object>>) form.get("components");
        Assert.assertEquals(1, comps.size());

        Map<String, Object> header = comps.get(0);
        Assert.assertEquals("jPanel_Header", header.get("name"));
        Assert.assertEquals(Boolean.TRUE, header.get("isContainer"));
        Assert.assertEquals("FlowLayout", header.get("layoutName"));

        @SuppressWarnings("unchecked")
        Map<String, Object> constraints = (Map<String, Object>) header.get("constraints");
        Assert.assertEquals("North", constraints.get("direction"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> subComps = (List<Map<String, Object>>) header.get("children");
        Assert.assertEquals(1, subComps.size());

        Map<String, Object> btn = subComps.get(0);
        Assert.assertEquals("jButton_Fechar", btn.get("name"));
        Assert.assertEquals("JButton", btn.get("simpleClassName"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) btn.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> textProp = (Map<String, Object>) props.get("text");
        Assert.assertEquals("Fechar", textProp.get("value"));

        @SuppressWarnings("unchecked")
        List<Map<String, String>> events = (List<Map<String, String>>) btn.get("events");
        Assert.assertEquals(1, events.size());
        Assert.assertEquals("actionPerformed", events.get(0).get("event"));
        Assert.assertEquals("jButton_FecharActionPerformed", events.get(0).get("handler"));
    }

    @Test
    public void testModifyProperty() throws Exception {
        File temp = File.createTempFile("form_test", ".form");
        temp.deleteOnExit();
        Files.write(temp.toPath(), SAMPLE_FORM.getBytes(StandardCharsets.UTF_8));

        // Modifica propriedade existente
        boolean ok = FormPropertyModifier.setProperty(temp, "jButton_Fechar", "text", "java.lang.String", "Sair da Tela");
        Assert.assertTrue(ok);

        // Insere nova propriedade
        ok = FormPropertyModifier.setProperty(temp, "jButton_Fechar", "enabled", "boolean", "false");
        Assert.assertTrue(ok);

        // Re-parseia e confere
        Map<String, Object> parsed = FormXmlReader.parseForm(temp);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> comps = (List<Map<String, Object>>) parsed.get("components");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> subComps = (List<Map<String, Object>>) comps.get(0).get("children");
        Map<String, Object> btn = subComps.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) btn.get("properties");

        @SuppressWarnings("unchecked")
        Map<String, Object> textProp = (Map<String, Object>) props.get("text");
        Assert.assertEquals("Sair da Tela", textProp.get("value"));

        @SuppressWarnings("unchecked")
        Map<String, Object> enabledProp = (Map<String, Object>) props.get("enabled");
        Assert.assertEquals("false", enabledProp.get("value"));
    }

    @Test
    public void testGenerateBlueprint() throws Exception {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("packageName", "br.com.merito.teste");
        spec.put("className", "OperadorTesteVW");
        spec.put("title", "Cadastro de Operadores");

        List<Map<String, Object>> rootComps = new ArrayList<>();

        // Header
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("name", "jPanel_Header");
        header.put("class", "javax.swing.JPanel");
        header.put("layout", "BorderLayout");
        Map<String, Object> headerConstraints = new LinkedHashMap<>();
        headerConstraints.put("direction", "North");
        header.put("constraints", headerConstraints);

        List<Map<String, Object>> headerChildren = new ArrayList<>();
        Map<String, Object> btnFechar = new LinkedHashMap<>();
        btnFechar.put("name", "jButton_Fechar");
        btnFechar.put("class", "javax.swing.JButton");
        Map<String, Object> btnProps = new LinkedHashMap<>();
        btnProps.put("text", "Fechar");
        btnFechar.put("properties", btnProps);
        headerChildren.add(btnFechar);
        header.put("children", headerChildren);
        rootComps.add(header);

        // Content
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("name", "jPanel_Content");
        content.put("class", "javax.swing.JPanel");
        content.put("layout", "BoxLayout");
        Map<String, Object> contentConstraints = new LinkedHashMap<>();
        contentConstraints.put("direction", "Center");
        content.put("constraints", contentConstraints);

        List<Map<String, Object>> contentChildren = new ArrayList<>();
        Map<String, Object> txtNome = new LinkedHashMap<>();
        txtNome.put("name", "jTextField_Nome");
        txtNome.put("class", "javax.swing.JTextField");
        Map<String, Object> txtProps = new LinkedHashMap<>();
        txtProps.put("columns", 30);
        txtNome.put("properties", txtProps);
        contentChildren.add(txtNome);
        content.put("children", contentChildren);
        rootComps.add(content);

        spec.put("components", rootComps);

        // 1. Gera XML .form
        String xml = FormXmlGenerator.generateXml(spec);
        Assert.assertNotNull(xml);
        Assert.assertTrue(xml.contains("Cadastro de Operadores"));
        Assert.assertTrue(xml.contains("jButton_Fechar"));
        Assert.assertTrue(xml.contains("jTextField_Nome"));

        // Valida que o XML gerado pode ser lido pelo FormXmlReader sem erro
        Map<String, Object> reParsed = FormXmlReader.parseForm(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Assert.assertNotNull(reParsed);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parsedComps = (List<Map<String, Object>>) reParsed.get("components");
        Assert.assertEquals(2, parsedComps.size());

        // 2. Gera Java .java
        String javaSource = FormJavaGenerator.generateSource(spec);
        Assert.assertNotNull(javaSource);
        Assert.assertTrue(javaSource.contains("public class OperadorTesteVW extends javax.swing.JDialog"));
        Assert.assertTrue(javaSource.contains("private void initComponents()"));
        Assert.assertTrue(javaSource.contains("public javax.swing.JTextField getJTextField_Nome()"));
        Assert.assertTrue(javaSource.contains("private javax.swing.JTextField jTextField_Nome;"));
    }

    @Test
    public void testRealFormIfAvailable() {
        File realForm = new File("/home/merito/projetos/jbase/trunk/JTef/src/br/com/tef/netunna/VisualizarJsonVW.form");
        if (realForm.exists()) {
            try {
                Map<String, Object> form = FormXmlReader.parseForm(realForm);
                Assert.assertNotNull(form);
                Assert.assertEquals("1.3", form.get("version"));
                Assert.assertNotNull(form.get("components"));
            } catch (Exception e) {
                Assert.fail("Erro ao ler form real: " + e.getMessage());
            }
        }
    }

    @Test
    public void testFullShowcaseBlueprint() throws Exception {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("packageName", "br.com.merito.showcase");
        spec.put("className", "ShowcaseTesteVW");
        spec.put("title", "Showcase Total");

        List<Map<String, Object>> rootComps = new ArrayList<>();

        // 1. ToolBar
        Map<String, Object> tb = new LinkedHashMap<>();
        tb.put("name", "jToolBar1");
        tb.put("class", "javax.swing.JToolBar");
        Map<String, Object> tbCons = new LinkedHashMap<>();
        tbCons.put("direction", "North");
        tb.put("constraints", tbCons);
        rootComps.add(tb);

        // 2. TabbedPane no Center
        Map<String, Object> tabbed = new LinkedHashMap<>();
        tabbed.put("name", "jTabbedPane1");
        tabbed.put("class", "javax.swing.JTabbedPane");
        Map<String, Object> tabCons = new LinkedHashMap<>();
        tabCons.put("direction", "Center");
        tabbed.put("constraints", tabCons);

        List<Map<String, Object>> tabs = new ArrayList<>();

        // Aba 1: ScrollPane com Tabela
        Map<String, Object> scrollTable = new LinkedHashMap<>();
        scrollTable.put("name", "jScrollPaneTable");
        scrollTable.put("class", "javax.swing.JScrollPane");
        scrollTable.put("tabTitle", "Tabela de Dados");

        List<Map<String, Object>> tableChildren = new ArrayList<>();
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("name", "jTable1");
        table.put("class", "javax.swing.JTable");
        List<Map<String, Object>> cols = new ArrayList<>();
        Map<String, Object> c1 = new LinkedHashMap<>();
        c1.put("title", "Codigo"); c1.put("type", "java.lang.Integer"); c1.put("editable", false);
        cols.add(c1);
        Map<String, Object> c2 = new LinkedHashMap<>();
        c2.put("title", "Nome"); c2.put("type", "java.lang.String"); c2.put("editable", true);
        cols.add(c2);
        table.put("columns", cols);
        tableChildren.add(table);
        scrollTable.put("children", tableChildren);
        tabs.add(scrollTable);

        // Aba 2: SplitPane
        Map<String, Object> split = new LinkedHashMap<>();
        split.put("name", "jSplitPane1");
        split.put("class", "javax.swing.JSplitPane");
        split.put("tabTitle", "Divisao");

        List<Map<String, Object>> splitChildren = new ArrayList<>();
        Map<String, Object> leftPanel = new LinkedHashMap<>();
        leftPanel.put("name", "jPanelLeft");
        leftPanel.put("class", "javax.swing.JPanel");
        Map<String, Object> leftCons = new LinkedHashMap<>();
        leftCons.put("position", "left");
        leftPanel.put("constraints", leftCons);
        splitChildren.add(leftPanel);

        Map<String, Object> rightPanel = new LinkedHashMap<>();
        rightPanel.put("name", "jPanelRight");
        rightPanel.put("class", "javax.swing.JPanel");
        Map<String, Object> rightCons = new LinkedHashMap<>();
        rightCons.put("position", "right");
        rightPanel.put("constraints", rightCons);
        splitChildren.add(rightPanel);

        split.put("children", splitChildren);
        tabs.add(split);

        tabbed.put("children", tabs);
        rootComps.add(tabbed);

        // 3. Panel Sul com Radio e ButtonGroup, Combo e Spinner
        Map<String, Object> southPanel = new LinkedHashMap<>();
        southPanel.put("name", "jPanelSouth");
        southPanel.put("class", "javax.swing.JPanel");
        Map<String, Object> southCons = new LinkedHashMap<>();
        southCons.put("direction", "South");
        southPanel.put("constraints", southCons);

        List<Map<String, Object>> southChildren = new ArrayList<>();

        Map<String, Object> radio1 = new LinkedHashMap<>();
        radio1.put("name", "jRadioButton1");
        radio1.put("class", "javax.swing.JRadioButton");
        radio1.put("buttonGroup", "buttonGroupOpcoes");
        Map<String, Object> r1Props = new LinkedHashMap<>();
        r1Props.put("text", "Opcao 1");
        radio1.put("properties", r1Props);
        southChildren.add(radio1);

        Map<String, Object> combo = new LinkedHashMap<>();
        combo.put("name", "jComboBox1");
        combo.put("class", "javax.swing.JComboBox");
        List<String> items = new ArrayList<>();
        items.add("Alfa"); items.add("Beta");
        combo.put("items", items);
        southChildren.add(combo);

        southPanel.put("children", southChildren);
        rootComps.add(southPanel);

        spec.put("components", rootComps);

        // Gera XML
        String xml = FormXmlGenerator.generateXml(spec);
        Assert.assertNotNull(xml);
        Assert.assertTrue(xml.contains("<Component class=\"javax.swing.ButtonGroup\" name=\"buttonGroupOpcoes\">"));
        Assert.assertTrue(xml.contains("autoScrollPane"));
        Assert.assertTrue(xml.contains("JTabbedPaneSupportLayout"));
        Assert.assertTrue(xml.contains("tabName=\"Tabela de Dados\""));
        Assert.assertTrue(xml.contains("JSplitPaneSupportLayout"));
        Assert.assertTrue(xml.contains("position=\"left\""));
        Assert.assertTrue(xml.contains("<StringItem index=\"0\" value=\"Alfa\"/>"));

        // Gera Java
        String java = FormJavaGenerator.generateSource(spec);
        Assert.assertNotNull(java);
        Assert.assertTrue(java.contains("buttonGroupOpcoes = new javax.swing.ButtonGroup();"));
        Assert.assertTrue(java.contains("jScrollPaneTable.setViewportView(jTable1);"));
        Assert.assertTrue(java.contains("jTabbedPane1.addTab(\"Tabela de Dados\", jScrollPaneTable);"));
        Assert.assertTrue(java.contains("jSplitPane1.setLeftComponent(jPanelLeft);"));
        Assert.assertTrue(java.contains("jSplitPane1.setRightComponent(jPanelRight);"));
        Assert.assertTrue(java.contains("buttonGroupOpcoes.add(jRadioButton1);"));
        Assert.assertTrue(java.contains("new javax.swing.table.DefaultTableModel"));
    }

}
