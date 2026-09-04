package com.merito.agynb.form;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Editor cirúrgico de propriedades em arquivos .form do NetBeans.
 * Localiza o componente pelo nome e atualiza ou insere a propriedade preservando a integridade do XML.
 */
public class FormPropertyModifier {

    public static boolean setProperty(File formFile, String componentName, String propertyName, String propertyType, String value) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(formFile);
        doc.getDocumentElement().normalize();

        Element targetComp = findComponentByName(doc.getDocumentElement(), componentName);
        if (targetComp == null) {
            // Se o componentName for "root" ou igual ao nome do formulário ou null, pode ser propriedade da raiz
            if ("root".equalsIgnoreCase(componentName) || "Form".equalsIgnoreCase(componentName)) {
                targetComp = doc.getDocumentElement();
            } else {
                return false;
            }
        }

        Element propsElem = getOrCreateChildElement(doc, targetComp, "Properties");
        Element propElem = findPropertyByName(propsElem, propertyName);

        if (propElem != null) {
            if (propertyType != null && !propertyType.isEmpty()) {
                propElem.setAttribute("type", propertyType);
            }
            propElem.setAttribute("value", value);
        } else {
            propElem = doc.createElement("Property");
            propElem.setAttribute("name", propertyName);
            String type = (propertyType != null && !propertyType.isEmpty()) ? propertyType : inferType(value);
            propElem.setAttribute("type", type);
            propElem.setAttribute("value", value);
            propsElem.appendChild(propElem);
        }

        saveDocument(doc, formFile);
        return true;
    }

    private static Element findComponentByName(Element parent, String name) {
        if (name == null || name.isEmpty()) return null;
        if (name.equals(parent.getAttribute("name"))) {
            return parent;
        }

        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                Element found = findComponentByName((Element) n, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Element findPropertyByName(Element propsElem, String propName) {
        NodeList list = propsElem.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node n = list.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && "Property".equals(n.getNodeName())) {
                Element p = (Element) n;
                if (propName.equals(p.getAttribute("name"))) {
                    return p;
                }
            }
        }
        return null;
    }

    private static Element getOrCreateChildElement(Document doc, Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && tagName.equals(n.getNodeName())) {
                return (Element) n;
            }
        }
        Element newElem = doc.createElement(tagName);
        parent.insertBefore(newElem, parent.getFirstChild());
        return newElem;
    }

    private static String inferType(String value) {
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return "boolean";
        }
        try {
            Integer.parseInt(value);
            return "int";
        } catch (NumberFormatException ignored) {}
        return "java.lang.String";
    }

    private static void saveDocument(Document doc, File targetFile) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        try (OutputStream out = new FileOutputStream(targetFile)) {
            transformer.transform(new DOMSource(doc), new StreamResult(out));
        }
    }
}
