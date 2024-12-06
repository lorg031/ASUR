package com.asur.autoTest.controllers;

import com.asur.autoTest.validators.XmlValidator;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;


import javax.xml.XMLConstants;
import javax.xml.bind.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.*;
import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping("/api/xml")
public class XmlController {

    private final XmlValidator xmlValidator = new XmlValidator();

    @PostMapping("/generate")
    public ResponseEntity<String> generateXml(@RequestParam("xsdFile") MultipartFile xsdFile) {
        System.out.println("Received XSD file: " + xsdFile.getOriginalFilename());
        try {
            // Сохраняем XSD файл во временную папку
            File tempFile = File.createTempFile("schema", ".xsd");
            xsdFile.transferTo(tempFile);
            System.out.println("XSD file saved to: " + tempFile.getAbsolutePath());

            // Генерируем XML на основе XSD
            String xml = generateXmlFromXsd(tempFile);

            // Валидация XML против XSD
            boolean isValid = xmlValidator.validateXml(xml, tempFile.getAbsolutePath());
            System.out.println("XML validation result: " + isValid);

//            if (!isValid) {
//                return ResponseEntity.badRequest().body("Generated XML is not valid against XSD.");
//            }

            return ResponseEntity.ok(xml);  // Возвращаем сгенерированный XML
        } catch (Exception e) {
            System.err.println("Error during XML generation: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }




    private String generateXmlFromXsd(File xsdFile) throws Exception {
        // Чтение XSD для получения структуры
        String rootElementName = getRootElementNameFromXsd(xsdFile);

        // Создание DOM-документа
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document document = documentBuilder.newDocument();

        // Создание корневого элемента с именем из XSD
        Element rootElement = document.createElement(rootElementName);
        document.appendChild(rootElement);

        // Добавляем дочерние элементы согласно XSD
        addChildElementsFromXsd(document, rootElement, xsdFile);

        // Преобразование DOM-документа в строку
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));

        return writer.toString();
    }

    Set<String> processedElements = new HashSet<>();

    private void addChildElementsFromXsd(Document document, Element parent, File xsdFile) throws Exception {
        // Чтение XSD как DOM
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document xsdDocument = builder.parse(xsdFile);

        // Поиск определений элементов по имени родительского элемента
        String parentName = parent.getTagName();
        NodeList elementNodes = xsdDocument.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");

        for (int i = 0; i < elementNodes.getLength(); i++) {
            Element xsdElement = (Element) elementNodes.item(i);
            if (parentName.equals(xsdElement.getAttribute("name"))) {
                // Проверяем на наличие xs:all или xs:sequence
                NodeList groups = xsdElement.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "all");
                if (groups.getLength() > 0) {
                    // Обрабатываем xs:all
                    Element allGroup = (Element) groups.item(0);
                    NodeList childElements = allGroup.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
                    for (int j = 0; j < childElements.getLength(); j++) {
                        Element childXsdElement = (Element) childElements.item(j);
                        String childName = childXsdElement.getAttribute("name");
                        String childType = childXsdElement.getAttribute("type");
                        String minOccurs = childXsdElement.getAttribute("minOccurs");

                        // Создаем элемент с дефолтным значением
                        Element childXmlElement = document.createElement(childName);
                        childXmlElement.setTextContent(getExampleDataForType(childType));
                        parent.appendChild(childXmlElement);

                        // Обрабатываем тип Void отдельно
                        if ("Void".equals(childType)) {
                            childXmlElement.setTextContent(""); // Пустое содержимое для Void
                        }

                        // Рекурсивно обрабатываем вложенные элементы
                        if (hasComplexType(childType, xsdDocument)) {
                            addChildElementsFromXsd(document, childXmlElement, xsdFile);
                        }
                    }
                }
            }
        }
    }



    // Проверка, является ли тип сложным
    private boolean hasComplexType(String typeName, Document xsdDocument) {
        NodeList complexTypes = xsdDocument.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "complexType");
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element complexType = (Element) complexTypes.item(i);
            if (typeName.equals(complexType.getAttribute("name"))) {
                return true;
            }
        }
        return false;
    }



    private String getExampleDataForType(String type) {
        switch (type) {
            case "xs:string":
                return "Example String";
            case "xs:int":
            case "xs:integer":
                return "123";
            case "xs:float":
            case "xs:double":
                return "123.45";
            case "xs:boolean":
                return "true";
            case "xs:date":
                return "2024-12-06";
            case "xs:dateTime":
                return "2024-12-06T12:00:00";
            default:
                return "Default Value"; // Для пользовательских типов
        }
    }

    private String getExampleDataForElementWithRestrictions(Element xsdElement) {
        NodeList restrictions = xsdElement.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "restriction");
        if (restrictions.getLength() > 0) {
            Element restriction = (Element) restrictions.item(0);
            NodeList patterns = restriction.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "pattern");
            if (patterns.getLength() > 0) {
                String pattern = ((Element) patterns.item(0)).getAttribute("value");
                // Генерация значения на основе паттерна (можно использовать библиотеку)
                return "PatternMatch";
            }

            NodeList maxLengths = restriction.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "maxLength");
            if (maxLengths.getLength() > 0) {
                int maxLength = Integer.parseInt(((Element) maxLengths.item(0)).getAttribute("value"));
                return "x".repeat(Math.min(5, maxLength)); // Пример значения
            }
        }
        return "Default Value";
    }

    private void addAttributes(Element xmlElement, Element xsdElement) {
        NodeList attributes = xsdElement.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "attribute");
        for (int i = 0; i < attributes.getLength(); i++) {
            Element attribute = (Element) attributes.item(i);
            String name = attribute.getAttribute("name");
            String type = attribute.getAttribute("type");
            xmlElement.setAttribute(name, getExampleDataForType(type)); // Примерное значение
        }
    }


    // Метод для проверки, имеет ли элемент вложенные элементы
    private boolean hasChildElements(Element element) {
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            if (childNodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                return true;
            }
        }
        return false;
    }



    private String getRootElementNameFromXsd(File xsdFile) throws Exception {
        // Чтение XSD как DOM
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(xsdFile);

        // Получение имени root-элемента
        NodeList elementNodes = document.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
        for (int i = 0; i < elementNodes.getLength(); i++) {
            Element element = (Element) elementNodes.item(i);
            if (element.getParentNode().getNodeName().equals("xs:schema")) {
                return element.getAttribute("name");
            }
        }
        throw new Exception("Root element not found in XSD");
    }


}

