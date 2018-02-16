
import java.awt.Color;
import org.jxmapviewer.viewer.GeoPosition;

/**
 *
 * @author lucasa
 */
public class WifiWaypoint extends ColorLabelWaypoint {

    public static float AREA = 0.001f;

    public WifiWaypoint(float area, String label, Color color, GeoPosition coord) {
        super(label, color, coord);
        this.AREA = area;
    }

    public float getArea() {
        return AREA;
    }

    public void setArea(float area) {
        this.AREA = area;
    }

}
