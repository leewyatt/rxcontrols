package io.github.leewyatt.rxcontrols.utils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import io.github.leewyatt.rxcontrols.pojo.PathInfo;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
/**
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
