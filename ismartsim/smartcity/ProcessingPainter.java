import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.painter.Painter;

import processing.core.PApplet;
import processing.core.PGraphicsJava2D;

public class ProcessingPainter extends PApplet implements Painter<JXMapViewer> {
	/**
	 * 
	 */
	private final SmartCityPlayer simulation;
	JXMapViewer mapViewer;
	// private PGraphicsJava2D pGraphics;
	private double timeClock = 0;
	private List<ParticleTuple> particlesTuples;

	public ProcessingPainter(SmartCityPlayer SmartCityPlayer, JXMapViewer mapViewer, List<LinkedHashMap> allEvents) {
		simulation = SmartCityPlayer;
		this.mapViewer = mapViewer;
		init();
		size(mapViewer.getWidth(), mapViewer.getHeight(), PApplet.JAVA2D);
		setup();
		// pGraphics = (PGraphicsJava2D) this.g;

		particlesTuples = SimulationUtil.createParticleSystem(simulation, allEvents);
	}

	@Override
	public void setup() {
		frameRate(30);
	}

	@Override
	public synchronized void draw() {
		fill(150);
		textSize(24);
		text(String.format("Clock: %.2f", timeClock), 10, 30);
		noSmooth();
		if (particlesTuples != null) {
			Rectangle rect = mapViewer.getViewportBounds();
			// convert from viewport to world bitmap
			// ((PGraphicsJava2D)g).translate(-rect.x, -rect.y);
			for (ParticleTuple particleTuple : particlesTuples) {
				try {
					Point2D point = particleTuple.getActualPosition(timeClock);
					if (point != null) {
						float x = (float) (point.getX() - rect.getX());
						float y = (float) (point.getY() - rect.getY());
						Color color = particleTuple.getColor();
						fill(color.getRed(), color.getGreen(), color.getBlue());
						ellipseMode(CENTER);
						int size = 10;
						if (particleTuple.fromCloud) {
							size *= 2;
						}
						ellipse(x, y, size, size);
						fill(0);
					}
				} catch(Exception e) {
					//e.printStackTrace(); // its ok ignore here
				}
			}
		}
	}

	@Override
	public void paint(Graphics2D g, JXMapViewer object, int width, int height) {
		((PGraphicsJava2D) this.g).g2 = g;
		draw();
	}

	private BufferedImage generateImage(Component component) {
		GraphicsConfiguration gc = component.getGraphicsConfiguration();
		// Create an image that supports transparent pixels
		BufferedImage bimage = gc.createCompatibleImage(getWidth(), getHeight(), Transparency.BITMASK);
		/* And now this is how we get an image of the component */
		Graphics2D g = bimage.createGraphics();
		// Then use the current component we're in and call paint on this
		// graphics object
		paint(g);
		return bimage;
	}

	public double getClock() {
		return timeClock;
	}

	public void setClock(double clock) {
		this.timeClock = clock;
	}

}