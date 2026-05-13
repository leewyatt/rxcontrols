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
package com.leewyatt.rxcontrols.pojo;

/**
 * @author LeeWyatt
 *
 * QQ: 9670453
 * QQ群: 518914410
 *
 */
public class PathInfo {
    /**
    * svgPath路径
    */
    private String pathD;
    /**
    * svgPath填充颜色
    */
    private String pathFill;
    /**
    * SvgPathId
    */
    private String pathId;

    public PathInfo() {
    }

    public PathInfo(String pathD, String pathFill, String pathId) {
        this.pathD = pathD;
        this.pathFill = pathFill;
        this.pathId = pathId;
    }

    public String getPathD() {
        return pathD;
    }

    public void setPathD(String pathD) {
        this.pathD = pathD;
    }

    public String getPathFill() {
        return pathFill;
    }

    public void setPathFill(String pathFill) {
        this.pathFill = pathFill;
    }

    public String getPathId() {
        return pathId;
    }

    public void setPathId(String pathId) {
        this.pathId = pathId;
    }

    @Override
    public String toString() {
        return "PathInfo{" +
                "pathD='" + pathD + '\'' +
                ", pathFill=" + pathFill +
                ", pathId='" + pathId + '\'' +
                '}';
    }

}
