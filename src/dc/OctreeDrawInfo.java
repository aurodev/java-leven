package dc;

import core.math.Vec3f;
import dc.solver.QEFData;

public class OctreeDrawInfo {
    public int index;
    public int corners;
    public Vec3f position;
    public Vec3f color;
    public Vec3f averageNormal;
    public QEFData qef;

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getCorners() {
        return corners;
    }

    public void setCorners(int corners) {
        this.corners = corners;
    }

    public Vec3f getPosition() {
        return position;
    }

    public void setPosition(Vec3f position) {
        this.position = position;
    }

    public Vec3f getAverageNormal() {
        return averageNormal;
    }

    public void setAverageNormal(Vec3f averageNormal) {
        this.averageNormal = averageNormal;
    }

    public Vec3f getColor() {
        return color;
    }

    public void setColor(Vec3f color) {
        this.color = color;
    }
}
