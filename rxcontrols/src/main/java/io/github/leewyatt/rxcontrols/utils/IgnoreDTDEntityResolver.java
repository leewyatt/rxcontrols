package io.github.leewyatt.rxcontrols.utils;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;



/**
 *
 */
public class IgnoreDTDEntityResolver implements EntityResolver {

    /**
     * 忽略DTD文档类型定义验证 ; 否则很耗时间
     */
    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
        return new InputSource(new ByteArrayInputStream("<?xml version='1.0' encoding='UTF-8'?>".getBytes()));
    }

}
