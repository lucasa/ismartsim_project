

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.net.URL;

import javax.imageio.ImageIO;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.GeoPosition;

/**
 * A fancy waypoint painter
 *
 * @author Martin Steiger
 */
public class WifiAreaWaypointRenderer extends FancyWaypointRenderer<WifiWaypoint> {

    /**
     * Uses a default waypoint image
     */
    public WifiAreaWaypointRenderer() {
        URL resource = getClass().getResource("waypoint_white.png");
        try {
            origImage = ImageIO.read(resource);
        } catch (Exception ex) {
            log.warn("couldn't read waypoint_white.png", ex);
        }
    }

    @Override
    public void paintWaypoint(Graphics2D g, JXMapViewer viewer, WifiWaypoint w) {
        g = (Graphics2D) g.create();

        if (origImage == null) {
            return;
        }

        BufferedImage myImg = map.get(w.getColor());

        if (myImg == null) {
            myImg = convert(origImage, w.getColor());
            map.put(w.getColor(), myImg);
        }

        GeoPosition geo = w.getPosition();        
        Point2D point = viewer.getTileFactory().geoToPixel(geo, viewer.getZoom());
        
        GeoPosition geo1 = new GeoPosition(geo.getLatitude()-0.001, geo.getLongitude());
        Point2D point1 = viewer.getTileFactory().geoToPixel(geo1, viewer.getZoom());
        GeoPosition geo2 = new GeoPosition(geo.getLatitude()+0.001, geo.getLongitude());
        Point2D point2 = viewer.getTileFactory().geoToPixel(geo2, viewer.getZoom());
        double distanceInPixel = point1.distance(point2);
		

        int x = (int) point.getX();
        int y = (int) point.getY();

        {
            int dx = (int) distanceInPixel;
            int dy = (int) distanceInPixel;
            g.setColor(w.getColor());
            g.drawOval(x-dx/2, y-dy/2, dx, dy);
        }

        //g.drawImage(myImg, x - myImg.getWidth() / 2, y - myImg.getHeight(), null);

        String label = w.getLabel();

//		g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        int tw = metrics.stringWidth(label);
        int th = 1 + metrics.getAscent();
        int yOffset = -20;
//		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawString(label, x
                - tw / 2, y + th - myImg.getHeight() + yOffset);

        g.dispose();
    }
}
