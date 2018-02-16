import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.jxmapviewer.viewer.GeoPosition;

public class ParticleTuple {
	/**
	 * 
	 */
	private final SmartCityPlayer simulation;
	private double init;
	private double end;
	private GeoPosition from;
	private GeoPosition to;
	private int id;
	private Double point;
	boolean fromCloud;

	public ParticleTuple(SmartCityPlayer smartCitySimulation, int id, double i, double d, GeoPosition f, GeoPosition t, boolean fromCloud) {
		simulation = smartCitySimulation;
		this.id = id;
		this.init = i;
		this.end = i + d;
		this.from = f;
		this.to = t;
		this.fromCloud = fromCloud;
	}

	public Color getColor() {
		if (fromCloud) {
			return Color.ORANGE;
		} else {
			return Color.WHITE;
		}
	}

	public Point2D getActualPosition(double clock) {
		if (clock < init || clock > end) { // past
			return null;
		}
		// percentage in path = actual / total
		double perc = (clock - init) / (end - init);

		Point2D f = simulation.getPixelFromGeo(from);
		Vector2D vf = new Vector2D(f.getX(), f.getY());
		Point2D t = simulation.getPixelFromGeo(to);
		Vector2D vt = new Vector2D(t.getX(), t.getY());

		Vector2D vector = vt.subtract(vf);
		vector = vector.scalarMultiply(perc);
		vector = vf.add(vector);

		point = new Point2D.Double(vector.getX(), vector.getY());
		return point;
	}
}