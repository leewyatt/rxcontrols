package io.github.leewyatt.rxcontrols.pojo;

/**
 *
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
