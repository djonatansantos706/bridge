package com.merito.agynb.form;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Leitor e parser determinístico de arquivos de formulário do NetBeans (.form).
 * Suporta formatos do NetBeans Matisse desde versões legadas (1.0) até modernas (1.9+).
 */
public class FormXmlReader {

    public static Map<String, Object> parseForm(File formFile) throws Exception {
        try (InputStream in = new FileInputStream(formFile)) {
            return parseForm(in);
        }
    }

    public static Map<String, Object> parseForm(InputStream in) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(in);
        doc.getDocumentElement().normalize();

        Element root = doc.getDocumentElement();
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("version", root.getAttribute("version"));
        result.put("maxVersion", root.getAttribute("maxVersion"));
        result.put("formType", root.getAttribute("type"));

        // Propriedades do Formulário Raiz
        result.put("properties", extractProperties(root));
        result.put("syntheticProperties", extractSyntheticProperties(root));

        // Layout Raiz
        Element layoutElem = getFirstChildElement(root, "Layout");
        if (layoutElem != null) {
            result.put("layoutClass", layoutElem.getAttribute("class"));
            result.put("layoutName", simplifyLayout(layoutElem.getAttribute("class")));
        }

        // Componentes Não-Visuais (ex: ButtonGroup)
        Element nonVisual = getFirstChildElement(root, "NonVisualComponents");
        if (nonVisual != null) {
            result.put("nonVisualComponents", extractComponents(nonVisual));
        } else {
            result.put("nonVisualComponents", new ArrayList<>());
        }

        // Árvore de Componentes Visuais (SubComponents)
        Element subComponents = getFirstChildElement(root, "SubComponents");
        if (subComponents != null) {
            result.put("components", extractComponents(subComponents));
        } else {
            result.put("components", new ArrayList<>());
        }

        return result;
    }

    private static List<Map<String, Object>> extractComponents(Element parent) {
        List<Map<String, Object>> list = new ArrayList<>();
        NodeList children = parent.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element elem = (Element) node;
            String tag = elem.getTagName();

            if ("Component".equals(tag) || "Container".equals(tag)) {
                Map<String, Object> comp = new LinkedHashMap<>();
                comp.put("isContainer", "Container".equals(tag));
                comp.put("name", elem.getAttribute("name"));
                comp.put("className", elem.getAttribute("class"));
                comp.put("simpleClassName", getSimpleName(elem.getAttribute("class")));

                // Properties
                comp.put("properties", extractProperties(elem));

                // Constraints
                comp.put("constraints", extractConstraints(elem));

                // Events
                comp.put("events", extractEvents(elem));

                // Layout (para Container)
                if ("Container".equals(tag)) {
                    Element layoutElem = getFirstChildElement(elem, "Layout");
                    if (layoutElem != null) {
                        comp.put("layoutClass", layoutElem.getAttribute("class"));
                        comp.put("layoutName", simplifyLayout(layoutElem.getAttribute("class")));
                        comp.put("layoutProperties", extractProperties(layoutElem));
                    }
                    Element sub = getFirstChildElement(elem, "SubComponents");
                    if (sub != null) {
                        comp.put("children", extractComponents(sub));
                    } else {
                        comp.put("children", new ArrayList<>());
                    }
                }

                list.add(comp);
            }
        }
        return list;
    }

    private static Map<String, Object> extractProperties(Element parent) {
        Map<String, Object> props = new LinkedHashMap<>();
        Element propsElem = getFirstChildElement(parent, "Properties");
        if (propsElem == null) {
            propsElem = parent;
        }

        NodeList list = propsElem.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && "Property".equals(node.getNodeName())) {
                Element p = (Element) node;
                String name = p.getAttribute("name");
                String type = p.getAttribute("type");
                String val = p.getAttribute("value");

                Map<String, Object> propDetail = new LinkedHashMap<>();
                propDetail.put("type", type);
                if (val != null && !val.isEmpty()) {
                    propDetail.put("value", val);
                }

                NodeList subNodes = p.getChildNodes();
                for (int j = 0; j < subNodes.getLength(); j++) {
                    Node sn = subNodes.item(j);
                    if (sn.getNodeType() == Node.ELEMENT_NODE) {
                        Element se = (Element) sn;
                        propDetail.put("kind", se.getTagName());
                        if (se.hasAttribute("name")) propDetail.put("refName", se.getAttribute("name"));
                        if (se.hasAttribute("value")) propDetail.put("value", se.getAttribute("value"));
                        if (se.hasAttribute("id")) propDetail.put("id", se.getAttribute("id"));
                    }
                }
                props.put(name, propDetail);
            }
        }
        return props;
    }

    private static Map<String, Object> extractSyntheticProperties(Element parent) {
        Map<String, Object> props = new LinkedHashMap<>();
        Element synElem = getFirstChildElement(parent, "SyntheticProperties");
        if (synElem == null) return props;

        NodeList list = synElem.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && "SyntheticProperty".equals(node.getNodeName())) {
                Element sp = (Element) node;
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("type", sp.getAttribute("type"));
                detail.put("value", sp.getAttribute("value"));
                props.put(sp.getAttribute("name"), detail);
            }
        }
        return props;
    }

    private static Map<String, Object> extractConstraints(Element comp) {
        Map<String, Object> result = new LinkedHashMap<>();
        Element constraintsElem = getFirstChildElement(comp, "Constraints");
        if (constraintsElem == null) return result;

        Element constraint = getFirstChildElement(constraintsElem, "Constraint");
        if (constraint == null) return result;

        result.put("layoutClass", constraint.getAttribute("layoutClass"));

        Element border = getFirstChildElement(constraint, "BorderConstraints");
        if (border != null) {
            result.put("type", "BorderLayout");
            result.put("direction", border.getAttribute("direction"));
        }

        Element abs = getFirstChildElement(constraint, "AbsoluteConstraints");
        if (abs != null) {
            result.put("type", "AbsoluteLayout");
            result.put("x", abs.getAttribute("x"));
            result.put("y", abs.getAttribute("y"));
            result.put("width", abs.getAttribute("width"));
            result.put("height", abs.getAttribute("height"));
        }

        Element grid = getFirstChildElement(constraint, "GridBagConstraints");
        if (grid != null) {
            result.put("type", "GridBagLayout");
            result.put("gridX", grid.getAttribute("gridX"));
            result.put("gridY", grid.getAttribute("gridY"));
            result.put("gridWidth", grid.getAttribute("gridWidth"));
            result.put("gridHeight", grid.getAttribute("gridHeight"));
        }

        return result;
    }

    private static List<Map<String, String>> extractEvents(Element comp) {
        List<Map<String, String>> events = new ArrayList<>();
        Element eventsElem = getFirstChildElement(comp, "Events");
        if (eventsElem == null) return events;

        NodeList list = eventsElem.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && "EventHandler".equals(node.getNodeName())) {
                Element eh = (Element) node;
                Map<String, String> ev = new LinkedHashMap<>();
                ev.put("event", eh.getAttribute("event"));
                ev.put("listener", eh.getAttribute("listener"));
                ev.put("parameters", eh.getAttribute("parameters"));
                ev.put("handler", eh.getAttribute("handler"));
                events.add(ev);
            }
        }
        return events;
    }

    private static Element getFirstChildElement(Element parent, String tagName) {
        if (parent == null) return null;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static String getSimpleName(String fqn) {
        if (fqn == null) return "";
        int idx = fqn.lastIndexOf('.');
        return idx >= 0 ? fqn.substring(idx + 1) : fqn;
    }

    private static String simplifyLayout(String layoutClass) {
        if (layoutClass == null) return "Unknown";
        if (layoutClass.contains("DesignBorderLayout") || layoutClass.contains("BorderLayout")) return "BorderLayout";
        if (layoutClass.contains("DesignFlowLayout") || layoutClass.contains("FlowLayout")) return "FlowLayout";
        if (layoutClass.contains("DesignBoxLayout") || layoutClass.contains("BoxLayout")) return "BoxLayout";
        if (layoutClass.contains("DesignAbsoluteLayout") || layoutClass.contains("AbsoluteLayout")) return "AbsoluteLayout";
        if (layoutClass.contains("DesignGridBagLayout") || layoutClass.contains("GridBagLayout")) return "GridBagLayout";
        if (layoutClass.contains("DesignGridLayout") || layoutClass.contains("GridLayout")) return "GridLayout";
        if (layoutClass.contains("DesignCardLayout") || layoutClass.contains("CardLayout")) return "CardLayout";
        return getSimpleName(layoutClass);
    }
}
