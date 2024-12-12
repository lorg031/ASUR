package com.asur.autoTest.controllers;

import com.asur.autoTest.validators.XmlValidator;
import com.mifmif.common.regex.Generex;
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
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

                        // Пытаемся найти паттерн в restriction
                        String childPattern = getPatternFromXsd(childXsdElement);
                        String base = getBaseFromXsd(childXsdElement);
                        System.out.println("Element: " + childName + ", Pattern: " + childPattern);  // Логирование паттерна

                        // Создаем элемент с дефолтным значением или значением по паттерну
                        Element childXmlElement = document.createElement(childName);
                        childXmlElement.setTextContent(getExampleDataForType(childType, childPattern, base));
                        parent.appendChild(childXmlElement);

                        // Рекурсивно обрабатываем вложенные элементы
                        if (hasComplexType(childType, xsdDocument)) {
                            addChildElementsFromXsd(document, childXmlElement, xsdFile);
                        }
                    }
                }

                // Обрабатываем xs:sequence, если существует
                NodeList sequences = xsdElement.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "sequence");
                if (sequences.getLength() > 0) {
                    Element sequenceGroup = (Element) sequences.item(0);
                    NodeList childElements = sequenceGroup.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
                    for (int j = 0; j < childElements.getLength(); j++) {
                        Element childXsdElement = (Element) childElements.item(j);
                        String childName = childXsdElement.getAttribute("name");
                        String childType = childXsdElement.getAttribute("type");

                        // Пытаемся найти паттерн в restriction
                        String childPattern = getPatternFromXsd(childXsdElement);
                        String base = getBaseFromXsd(childXsdElement);
                        System.out.println("Element: " + childName + ", Pattern: " + childPattern);  // Логирование паттерна

                        // Создаем элемент с дефолтным значением или значением по паттерну
                        Element childXmlElement = document.createElement(childName);
                        childXmlElement.setTextContent(getExampleDataForType(childType, childPattern, base));
                        parent.appendChild(childXmlElement);

                        // Рекурсивно обрабатываем вложенные элементы
                        if (hasComplexType(childType, xsdDocument)) {
                            addChildElementsFromXsd(document, childXmlElement, xsdFile);
                        }
                    }
                }
            }
        }
    }

//    private String getPatternFromXsd(Element element) {
//        // Добавление отладочного сообщения для отслеживания
//        System.out.println("Processing element: " + element.getAttribute("name") + element.getAttribute("minOccurs"));
//
//
//        // Найдем все простые типы (simpleType) внутри элемента
//        NodeList simpleTypes = element.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "simpleType");
//
//        for (int i = 0; i < simpleTypes.getLength(); i++) {
//            Element simpleType = (Element) simpleTypes.item(i);
//
//            // Найдем все элементы xs:restriction внутри xs:simpleType
//            NodeList restrictions = simpleType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "restriction");
//
//            for (int j = 0; j < restrictions.getLength(); j++) {
//                Element restriction = (Element) restrictions.item(j);
//
//                // Найдем элементы xs:pattern в xs:restriction
//                NodeList patternNodes = restriction.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "pattern");
//
//                if (patternNodes.getLength() > 0) {
//                    // Получаем паттерн
//                    String pattern = patternNodes.item(0).getTextContent();
//                    if (pattern != null && !pattern.isEmpty()) {
//                        System.out.println("Pattern found for element " + element.getAttribute("name") + ": " + pattern);
//                        return pattern;
//                    }
//                }
//            }
//        }
//
//        // Если паттерн не найден, возвращаем null
//        System.out.println("No pattern found for element " + element.getAttribute("name"));
//        return null;
//    }

    private Element findRestriction(Element simpleType) {
        NodeList restrictions = simpleType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "restriction");
        return restrictions.getLength() > 0 ? (Element) restrictions.item(0) : null;
    }

    private Element findSimpleType(Element element) {
        NodeList simpleTypes = element.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "simpleType");
        return simpleTypes.getLength() > 0 ? (Element) simpleTypes.item(0) : null;
    }


    private String getPatternFromXsd(Element element) {

        Element simpleType = findSimpleType(element); // Найдем simpleType

        if (simpleType != null) {
            Element restriction = findRestriction(simpleType); // Найдем restriction

            if (restriction != null) {
                String pattern = findPattern(restriction); // Найдем pattern

                if (pattern != null) {
                    System.out.println("Pattern found for element " + element.getAttribute("name") + ": " + pattern);
                    return pattern;
                }
            }
        }

        System.out.println("No pattern found for element " + (element != null ? element.getAttribute("name") : "null"));
        return null;
    }

    private String getBaseFromXsd(Element element) {

        Element simpleType = findSimpleType(element); // Найдем simpleType

        if (simpleType != null) {
            Element restriction = findRestriction(simpleType); // Найдем restriction

            if (restriction != null) {
                return restriction.getAttribute("base");


            }
        }

        System.out.println("No pattern found for element " + (element != null ? element.getAttribute("name") : "null"));
        return null;
    }



    private String findPattern(Element restriction) {
        NodeList patternNodes = restriction.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "pattern");
        if (patternNodes.getLength() > 0) {
            Element pattern = (Element) patternNodes.item(0);
            return pattern.getAttribute("value"); // Используем getAttribute
        }
        return null;
    }




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

    private String getExampleDataForType(String type, String pattern, String base) {
        if (pattern != null && !pattern.isEmpty()) {
            System.out.println("PATTERN:    " + generateTestValue(pattern, base));
            return generateTestValue(pattern, base); // Генерация данных по паттерну

        }

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
                return "2024-12-06"; // пример даты
            case "xs:dateTime":
                return "2024-12-06T12:00:00"; // пример даты и времени
            default:
                return "Default Value"; // Для неизвестных или нестандартных типов
        }
    }


    public String generateTestValue(String pattern, String base) {
        if (pattern == null || pattern.isEmpty()) {
            return null; // Handle empty or null pattern
        }

        Pattern compiledPattern = Pattern.compile(pattern);

//        if(base.equals("date")){
//            for (int i = 0; i < 100; i++) { // Maximum attempts to generate a value
//                String generatedValue = generateRandomString(pattern, base);
//                Matcher matcher = compiledPattern.matcher(generatedValue);
//                if (matcher.matches()) {
//                    return generatedValue;
//                }
//            }
//            System.err.println("Unable to generate a value matching pattern: " + pattern + " After many attempts");
//            return null;
//        }

        // Crucial: Avoid infinite loops with non-matching patterns
        for (int i = 0; i < 100; i++) { // Maximum attempts to generate a value
            String generatedValue = generateRandomString(pattern, base);
            Matcher matcher = compiledPattern.matcher(generatedValue);
            if (matcher.matches()) {
                return generatedValue;
            }
        }
        System.err.println("Unable to generate a value matching pattern: " + pattern + " After many attempts");
        return null; // Return null if no matching value is found after multiple attempts
    }


    private String generateRandomString(String pattern, String base) {
        Generex generex = new Generex(pattern);
        if (base.equals("date")){
            Random random = new Random();
            int day = random.nextInt(28) + 1; // Avoid invalid dates
            int month = random.nextInt(12) + 1;
            int year = random.nextInt(100) + 2000; // Generate years from 2000
            return String.format("%02d.%02d.%04d", day, month, year);
        }
        return generex.random();
    }
    // Helper function to generate a random string based on the pattern
//    private String generateRandomString(String pattern) {
//        // Improve random value generation
//        StringBuilder sb = new StringBuilder();
//
//        Pattern p = Pattern.compile("\\{[^}]*\\}");
//        Matcher m = p.matcher(pattern);
//        StringBuffer result = new StringBuffer();
//
//        while (m.find()) {
//            String match = m.group();
//            int num = Integer.parseInt(match.replaceAll("[{}]", "").trim());
//
//            String digits = "0123456789";
//            Random r = new Random();
//            for(int i = 0; i < num; i++){
//                int randomIndex = r.nextInt(digits.length());
//                sb.append(digits.charAt(randomIndex));
//            }
//            m.appendReplacement(result, sb.toString());
//            sb.delete(0,sb.length()); //Clear the StringBuilder for the next repetition
//
//        }
//        m.appendTail(result);
//
//
//        return result.toString();
//    }

    private String generatePatternTest(){
        Generex generex = new Generex("[0-9]{2}[.][0-9]{2}[.][0-9]{4}");
        return (generex.random());
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
