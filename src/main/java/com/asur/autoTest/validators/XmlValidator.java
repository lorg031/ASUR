package com.asur.autoTest.validators;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.io.IOException;
import javax.xml.validation.Validator;
import org.xml.sax.SAXException;

public class XmlValidator {

    public boolean validateXml(String xml, String xsdPath) {
        try {
            // Создаем объект SchemaFactory для XSD
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            // Загружаем XSD-схему
            File xsdFile = new File(xsdPath);
            Schema schema = factory.newSchema(xsdFile);

            // Создаем валидатор для проверки XML
            Validator validator = schema.newValidator();
            StreamSource source = new StreamSource(new java.io.StringReader(xml));

            // Выполняем валидацию
            validator.validate(source);
            return true;  // Если валидация прошла успешно
        } catch (SAXException | IOException e) {
            System.out.println("XML validation error: " + e.getMessage());
            return false;  // Ошибка валидации
        }
    }
}
