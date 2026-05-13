/*
 * MIT License
 *
 * Copyright (c) 2021 LeeWyatt
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package com.leewyatt.rxcontrols.utils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import com.leewyatt.rxcontrols.pojo.PathInfo;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
/**
 * @author LeeWyatt
 * QQ: 9670453
 * QQ群: 518914410
 *
 */
public class SvgUtil {

    public static ArrayList<PathInfo> parseSvg(String content){
        ArrayList<PathInfo> list=new ArrayList<>();
        DocumentBuilderFactory dbf=DocumentBuilderFactory.newInstance();

        DocumentBuilder db=null;
        Document dom=null;

        try {
            StringReader sr=new StringReader(content);
            InputSource is=new InputSource(sr);
            //设置不验证
            dbf.setValidating(false);
            db=dbf.newDocumentBuilder();
            //忽略DTD文档类型定义验证
            db.setEntityResolver(new IgnoreDTDEntityResolver());
            dom=db.parse(is);
            Element item = dom.getDocumentElement();
            NodeList paths = dom.getElementsByTagName("path");
            for (int i = 0; i < paths.getLength(); i++) {
                Node node= paths.item(i);
                Element element = (Element) node;
                PathInfo info=new PathInfo();
                String pathD=element.getAttribute("d");
                //info.setPathD(ratePath(pathD,rate));
                info.setPathD(pathD);
                String pathFill = element.getAttribute("fill");
                info.setPathFill(pathFill==""?"#000000":pathFill);
                String pathID = element.getAttribute("p-id");
                //如果不存在p-id,就设置p-id
                pathID=pathID.trim().isEmpty()?String.valueOf(i):pathID;
                info.setPathId("p-id"+pathID);
                list.add(info);
            }
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return list;
    }
}
