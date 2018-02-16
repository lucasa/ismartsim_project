import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.Vector;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.input.CenterMapListener;
import org.jxmapviewer.input.PanKeyListener;
import org.jxmapviewer.input.PanMouseInputListener;
import org.jxmapviewer.input.ZoomMouseWheelListenerCenter;
import org.jxmapviewer.painter.CompoundPainter;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactory;
import org.jxmapviewer.viewer.WaypointPainter;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * A simple sample application that shows a OSM map of Europe
 *
 * @author Lucas Alberto - 03/2017
 */
public class SmartCitySimulation {

	public int ZOOM_DEFAULT = 6;
	
	protected static GeoPosition cityGeoPosition;
	protected static GeoPosition cloudGeoPosition;
	protected static GeoPosition userGeoPosition;


	protected static List<GeoPosition> sensorsGeoPositions = new ArrayList();
	protected static List<GeoPosition> wifiSpotsGeoPositions = new ArrayList();

	private TileFactory tileFactory;

	private JXMapViewer mapViewer;

	private List<Painter> overlayPainters;

	private ProcessingPainter processingPainter;

	private JFrame frame;

	private List<LinkedHashMap> simulationEvents = new ArrayList<>();

	protected static double CLOCK_TICK = 0.005;

	public SmartCitySimulation() {
	}
	
	public FogSimulation newSimulation() {
		FogSimulation.newInstance();
		return FogSimulation.getInstance();
	}

	public static void main(String[] args) {
		System.out.println(args);
		String cityName = "Porto Alegre";
		cityGeoPosition = new GeoPosition(-30.04717901252634, -51.21553659439087);
		// Procempa Data Center
		cloudGeoPosition = new GeoPosition(-30.043709, -51.215665);
		userGeoPosition = new GeoPosition(-30.04709078575702, -51.21131479740143);
		SmartCitySimulation smartCityGeoSimulation = new SmartCitySimulation();
		smartCityGeoSimulation.createMapInterface(cityName, cityGeoPosition, cloudGeoPosition, userGeoPosition);
	}

	public void createMapInterface(String cityName, GeoPosition cityGeo, final GeoPosition cloudGeo,
			final GeoPosition userClientGeo) {
		tileFactory = SimulationUtil.createMapTileFactory(cityName);
		// tileFactory = createMapTileFactoryOffline();

		// Setup JXMapViewer
		mapViewer = new JXMapViewer();
		mapViewer.setTileFactory(tileFactory);

		System.out.println("SmartCityGeoSimulation City Geo Position: " + cityGeo);

		// Cloud Data Center

		System.out.println("SmartCityGeoSimulation ADD CLOUD DATACENTER to simulation: " + cloudGeo);

		// Set the focus
		mapViewer.setZoom(ZOOM_DEFAULT);
		mapViewer.setAddressLocation(cityGeo);

		// Add interactions
		PanMouseInputListener mia = new SimulationMouseListener(mapViewer, cloudGeo);
		mapViewer.addMouseListener(mia);
		mapViewer.addMouseMotionListener(mia);
		mapViewer.addMouseListener(new CenterMapListener(mapViewer));
		mapViewer.addMouseWheelListener(new ZoomMouseWheelListenerCenter(mapViewer));
		mapViewer.addKeyListener(new PanKeyListener(mapViewer));

		// Display the viewer in a JFrame
		frame = new JFrame("SmartCityGeoSimulation");
		frame.getContentPane().add(mapViewer);
		//frame.setSize(500, 500);
		frame.setSize(1080, 720);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setFocusTraversalKeysEnabled(false);
		frame.setVisible(true);

		frame.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent ke) {
				if (ke.getKeyCode() == KeyEvent.VK_SPACE) {
					restartSimulation(cloudGeo, userClientGeo);
				}
				if (ke.getKeyCode() == KeyEvent.VK_ESCAPE) {
					System.out.println("App exit");
					System.exit(0);
				}
				if (ke.getKeyCode() == KeyEvent.VK_TAB) {
					FogSimulation.PROCESS_AT_CLOUD = !FogSimulation.PROCESS_AT_CLOUD;
					updateWindowTitle(frame, mapViewer);
				}
				if (ke.getKeyCode() == KeyEvent.VK_J) {
					loadSensorGeoPositionsFromFile(cloudGeo, userClientGeo, "speed_camera_poa.geojson");
//					restartUI();
				}
				if (ke.getKeyCode() == KeyEvent.VK_UP) {
					CLOCK_TICK *= 2;
				}
				if (ke.getKeyCode() == KeyEvent.VK_DOWN) {
					CLOCK_TICK /= 2;
				}
				if (ke.getKeyCode() == KeyEvent.VK_S) {
					try {
						//TODO get path from GUI file selector
						importJsonEventsLog("");
						startAnimation();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		});

		mapViewer.addPropertyChangeListener("zoom", new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				updateWindowTitle(frame, mapViewer);
			}
		});

		mapViewer.addPropertyChangeListener("center", new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				updateWindowTitle(frame, mapViewer);
			}
		});

		updateWindowTitle(frame, mapViewer);
		updateMapOverlay(cloudGeo, userClientGeo);
	}
	
	protected void loadSensorGeoPositionsFromFile(final GeoPosition cloudGeo, final GeoPosition userClientGeo, String geoJSonPath) {
		sensorsGeoPositions.clear();
		wifiSpotsGeoPositions.clear();
		
		//TODO get options from GUI file selector
		sensorsGeoPositions.addAll(SimulationUtil.importPositionsFromGeoJson(geoJSonPath));
//			sensorsGeoPositions.addAll(SimulationUtil.importPositionsFromGeoJson("traffic_signals_poa.geojson"));
		wifiSpotsGeoPositions
				.addAll(createWifiSpotsFromSensorsGeoPositions(sensorsGeoPositions, WifiWaypoint.AREA));
		System.out.println("Loaded sensors :" + sensorsGeoPositions.size());
	}

	

	protected void printSimulationGeoInfos() {
		String info = "Total of Sensors: " + sensorsGeoPositions.size();
		System.out.println();
		System.out.println(info);
		System.out.println();
	}

	protected static void updateWindowTitle(JFrame frame, JXMapViewer mapViewer) {
		if (frame != null) {
			double lat = mapViewer.getCenterPosition().getLatitude();
			double lon = mapViewer.getCenterPosition().getLongitude();
			int zoom = mapViewer.getZoom();

			frame.setTitle(String
					.format("SmartCity Fog Simulation (%.6f / %.6f) - Zoom: %d", lat, lon, zoom)
					+ " - " + FogSimulation.getInstance().getSimulationIdFromConfig());
			//System.out.println(frame.getTitle());
		}
	}

	public class SimulationMouseListener extends PanMouseInputListener {

		GeoPosition cloudGeo;

		public SimulationMouseListener(JXMapViewer viewer, GeoPosition cloudGeo) {
			super(viewer);
			this.cloudGeo = cloudGeo;
		}

		@Override
		public void mousePressed(MouseEvent evt) {
			super.mousePressed(evt);

			final boolean right = SwingUtilities.isRightMouseButton(evt);
			final boolean singleClick = (evt.getClickCount() == 1);

			Rectangle bounds = viewer.getViewportBounds();
			int x = bounds.x + evt.getX();
			int y = bounds.y + evt.getY();
			Point pixelCoordinates = new Point(x, y);
			GeoPosition geopos = getGeoFromPixel(pixelCoordinates);
			// System.out.println("GeoPosition: " + geopos);

			if(right && singleClick) {			
				// add sensor to geoposition = right click
				if (!evt.isShiftDown()) {
					sensorsGeoPositions.add(geopos);
					System.out.println("SmartCityGeoSimulation ADD Sensor to simulation: " + geopos);
					updateMapOverlay(cloudGeo, userGeoPosition);
				}
				
				// add sensor to geoposition = right click + shift
				if (evt.isShiftDown()) {
					wifiSpotsGeoPositions.add(geopos);
					System.out.println("SmartCityGeoSimulation ADD Wifi Cell to simulation: " + geopos);
					updateMapOverlay(cloudGeo, userGeoPosition);
				}
			}
		}
	}

	public void updateMapOverlay() {
		updateMapOverlay(SmartCitySimulation.cloudGeoPosition, SmartCitySimulation.userGeoPosition);
	}

	protected void updateMapOverlay(GeoPosition cloudGeo, GeoPosition userClientGeo) {
		overlayPainters = new Vector<Painter>();

		// datacenters = new ArrayList<ColorLabelWaypoint>();
		Set<ColorLabelWaypoint> wayPointers = new HashSet<ColorLabelWaypoint>();

		wayPointers.add(new ColorLabelWaypoint(
				"SmartCity DATACENTER [" + FogSimulation.getFogDeviceNetworkUsageByGeoPosition(cloudGeo) + "] ",
				Color.GREEN, cloudGeo));

		// RoutePainter routePainter = new RoutePainter(Arrays.asList(cloudGeo,
		// userClientGeo));
		// overlayPainters.add(routePainter);

		int i = 0;
		for (GeoPosition geo : sensorsGeoPositions) {
			// mark sensors on map
			wayPointers.add(new ColorLabelWaypoint("", Color.ORANGE, geo));
			// wayPointers.add(new ColorLabelWaypoint("SENSOR " + i + " ["
			// + FogSimulation.getFogDeviceNetworkUsageByGeoPosition(geo) + "]
			// ", Color.ORANGE, geo));
			i++;
		}

		// Create a sensor painter that takes all the sensors
		if (!wayPointers.isEmpty()) {
			WaypointPainter<ColorLabelWaypoint> sensorsPainter = new WaypointPainter<ColorLabelWaypoint>();
			sensorsPainter.setWaypoints(wayPointers);
			sensorsPainter.setRenderer(new FancyWaypointRenderer<ColorLabelWaypoint>());
			overlayPainters.add(sensorsPainter);
		}

		Set<WifiWaypoint> wificells = new HashSet<WifiWaypoint>();
		i = 0;
		for (GeoPosition geo : wifiSpotsGeoPositions) {
			// mark sensors on map
			wificells.add(new WifiWaypoint(0.001f,
					"" + i + " [" + FogSimulation.getFogDeviceNetworkUsageByGeoPosition(geo) + "] ", Color.BLUE, geo));
			// Create tracks from cells to cloud center
			// routePainter = new RoutePainter(Arrays.asList(geo, cloudGeo));
			// overlayPainters.add(routePainter);
			i++;
		}

		// Create a cell painter that takes all the wifis spots
		if (!wificells.isEmpty()) {
			WaypointPainter<WifiWaypoint> wificellPainter = new WaypointPainter<WifiWaypoint>();
			wificellPainter.setWaypoints(wificells);
			wificellPainter.setRenderer(new WifiAreaWaypointRenderer());
			overlayPainters.add(wificellPainter);
		}

		if (!simulationEvents.isEmpty()) {
			processingPainter = new ProcessingPainter(this, mapViewer, simulationEvents);
			overlayPainters.add(processingPainter);
		}

		// Create a compound painter that overlays all layers
		CompoundPainter<JXMapViewer> compoundPainter = new CompoundPainter<JXMapViewer>((List) overlayPainters);
		mapViewer.setOverlayPainter(compoundPainter);

		// repaint with new configuration
		mapViewer.invalidate();
		mapViewer.repaint();
	}

	
	public void restartSimulation(final GeoPosition cloudGeo, final GeoPosition userClientGeo) {
		System.out.println("Restart simulation...");

		final JXMapViewer m = mapViewer;
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				// while is still simulating...
				while (!FogSimulation.getInstance().isEndSimulation()) {
					
					// update simulation information
					SwingUtilities.invokeLater(new Runnable() {
						@Override
						public void run() {
							updateWindowTitle(frame, m);
						}
					});

					synchronized (this) {
						try {
							this.wait(1000); // 1s
						} catch (Exception e) {
						}
					}
				}

			}
		});
		thread.start();

		// start simulation
		thread = new Thread(new Runnable() {
			@Override
			public void run() {
				updateWindowTitle(frame, m);
				
				FogSimulation.getInstance().simulate(cloudGeo, userClientGeo, sensorsGeoPositions,
						wifiSpotsGeoPositions);
				
				extractSimulationInfos(true);
				
				System.out.println("End simulation.");
			}
		});
		thread.start();
	}

	

	protected List<GeoPosition> createWifiSpotsFromSensorsGeoPositions(List<GeoPosition> geoPositions,
			float wifiCellRadius) {
		List<GeoPosition> wifiPositions = new ArrayList<GeoPosition>(geoPositions);
		Map<GeoPosition, List<GeoPosition>> neighbors = new HashMap<GeoPosition, List<GeoPosition>>();
		for (GeoPosition geo1 : geoPositions) {
			neighbors.put(geo1, new ArrayList<GeoPosition>());
			for (GeoPosition geo2 : geoPositions) {
				if (!neighbors.containsKey(geo2) && SimulationUtil.isCircleIntersect(geo1.getLatitude(), geo1.getLongitude(),
						wifiCellRadius, geo2.getLatitude(), geo2.getLongitude(), wifiCellRadius)) {
					neighbors.get(geo1).add(geo2);
				}
			}
		}
		for (GeoPosition key : neighbors.keySet()) {
			wifiPositions.removeAll(neighbors.get(key));
		}

		return wifiPositions;
	}

	

	public GeoPosition getGeoFromPixel(Point pixelCoordinates) {
		return tileFactory.pixelToGeo(pixelCoordinates, mapViewer.getZoom());
	}

	public Point2D getPixelFromGeo(GeoPosition geo) {
		return tileFactory.geoToPixel(geo, mapViewer.getZoom());
	}

	public void extractSimulationInfos(boolean writeLog) {
		try {
			String id = FogSimulation.getInstance().getSimulationIdFromConfig();
			String pathResultDir = getOutputPath(id);
			
			// create directory if necessary
			File f = new File(pathResultDir);
			if(!f.exists()) f.mkdir();
			
			String pathResultFile = pathResultDir + id + ".csv";
			File file = new File(pathResultFile);
			boolean header = !file.exists();
			String csv = FogSimulation.getInstance().getFinalSimulationInfoCSV(header);			
			FileWriter fileWriter = new FileWriter(file, true);
			fileWriter.append(csv);
			fileWriter.close();
			System.out.println("CSV of results created: " + pathResultFile);
			
			if(writeLog) {
				System.out.println("Exporting Json Log Events...");
				ObjectMapper mapper = new ObjectMapper();
				mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
				ObjectWriter ow = mapper.writer();
				List<SimulationEvent> allLogEvents = FogSimulation.getInstance().getAllLogEvents();
				
				//String json = ow.writeValueAsString(allLogEvents);
				byte[] jsonBin = ow.writeValueAsBytes(allLogEvents);
				int hashcode = Arrays.hashCode(jsonBin);
							
				String pathEventsJsonFile = pathResultDir + id + "--" + hashcode+".json";			
				file = new File(pathEventsJsonFile);
	//			fileWriter = new FileWriter(file);
	//			fileWriter.write(json);
	//			fileWriter.close();
				FileOutputStream fos = new FileOutputStream(file);
				fos.write(jsonBin);
				fos.close();
				System.out.println("Json Log Events created: " + pathEventsJsonFile);
			}
			
//			importJsonEventsLog(jsonBin);
//			startAnimation(FogSimulation.getInstance().getEndClock());
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	private String getOutputPath(String id) {
		String pathResultDir = "./results/" + id + File.separator;
		return pathResultDir;
	}

	public List importJsonEventsLog(byte data[]) throws IOException, JsonParseException, JsonMappingException {
		ObjectMapper mapper = new ObjectMapper();
		String json = new String(data);
		List events = mapper.readValue(json, Vector.class);
		simulationEvents.clear();
		simulationEvents.addAll(events);
		return events;
	}
	
	public List importJsonEventsLog(String path) throws IOException, JsonParseException, JsonMappingException {
		ObjectMapper mapper = new ObjectMapper();
		String json = new String(Files.readAllBytes(Paths.get(path)));
		List events = mapper.readValue(json, Vector.class);
		simulationEvents.clear();
		simulationEvents.addAll(events);
		return events;
	}

	protected void startAnimation() {
		
		updateMapOverlay();

		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				double clock = 0;
				while(true) {
					clock += CLOCK_TICK;
					if(processingPainter != null) {
						processingPainter.setClock(clock);
						// System.out.println(String.format("Clock: %.4f", clock));
						mapViewer.repaint();
						try {
							synchronized (this) {
								this.wait(1);
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					} else {
						try {
							synchronized (this) {
								this.wait(50);
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			}
		});
		thread.start();
	}

	

}
