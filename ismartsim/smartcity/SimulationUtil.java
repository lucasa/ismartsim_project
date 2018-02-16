
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.GeoJsonObject;
import org.geojson.LngLatAlt;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.LocalResponseCache;
import org.jxmapviewer.viewer.TileFactory;
import org.jxmapviewer.viewer.TileFactoryInfo;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SimulationUtil {
	
	public static List<ParticleTuple> createParticleSystem(SmartCityPlayer simulation, List<LinkedHashMap> events) {
		List<ParticleTuple> particles = new ArrayList<ParticleTuple>();
		int i = 0;
		for (LinkedHashMap ev : events) {
			int destFogDeviceId = (int) ev.get("destFogDeviceId");
			SpecialFogDevice destDevice = (SpecialFogDevice) FogSimulation.getInstance()
					.getFogDeviceById(destFogDeviceId);
			GeoPosition destGeo = destDevice.getGeoPosition();
			if (ev.get("eventName").equals("TUPLE_ARRIVAL")) {
				int srcFogDeviceId = (int) ev.get("srcFogDeviceId");
				SpecialFogDevice srcDevice = (SpecialFogDevice) FogSimulation.getInstance()
						.getFogDeviceById(srcFogDeviceId);
				if (srcDevice != null) {
					GeoPosition srcGeo = srcDevice.getGeoPosition();
					if (!srcGeo.equals(destGeo)) {
						double clock = (double) ev.get("clock");
						double transmissionTime = (double) ev.get("transmissionTime");
						particles.add(new ParticleTuple(simulation, i, clock - transmissionTime, transmissionTime, srcGeo, destGeo,
								srcDevice.getName().contains("cloud")));
					}
				}
			}
			i++;
		}
		return particles;
	}

	public static List<GeoPosition> importPositionsFromGeoJson(String geoJSonPath) {
		InputStream inputStream;
		List<GeoPosition> positions = new ArrayList<GeoPosition>();
		try {
			inputStream = new FileInputStream(geoJSonPath); // traffic_signals_poa.geojson
			ObjectMapper objectMapper = new ObjectMapper();
			objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			GeoJsonObject object = objectMapper.readValue(inputStream, GeoJsonObject.class);
			if (object instanceof FeatureCollection) {
				List<Feature> features = ((FeatureCollection) object).getFeatures();
				for (Feature feature : features) {
					GeoJsonObject geometry = feature.getGeometry();
					if (geometry instanceof org.geojson.Point) {
						LngLatAlt coordinates = ((org.geojson.Point) geometry).getCoordinates();
						double latitude = coordinates.getLatitude();
						double longitude = coordinates.getLongitude();
						positions.add(new GeoPosition(latitude, longitude));
					}
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return positions;
	}
	
	public static TileFactory createMapTileFactory(String cityName) {
		// Create a TileFactoryInfo for Virtual Earth
		// TileFactoryInfo info = new
		// VirtualEarthTileFactoryInfo(VirtualEarthTileFactoryInfo.MAP);
		//TileFactoryInfo info = new OSMTileFactoryInfo();
		TileFactoryInfo info = new MapBoxTileFactoryInfo();
		TileFactory factory = new DefaultTileFactory(info);

		// Setup local file cache
		// File cacheDir = new File(System.getProperty("user.home") +
		// File.separator + ".jxmapviewer2");
		File cacheDir = new File("output" + File.separator + "mapcache-" + cityName);
		LocalResponseCache.installResponseCache(info.getBaseURL(), cacheDir, false);
		return factory;
	}

	public static TileFactory createMapTileFactoryOffline() {
		TileFactoryInfo info = new TileFactoryInfo(0, // min level
				8, // max allowed level
				9, // max level
				256, // tile size
				true, true, // x/y orientation is normal
				"http://wesmilepretty.com/gmap2/", // base url
				"x", "y", "z" // url args for x, y and z
		) {
			public String getTileUrl(int x, int y, int zoom) {
				int wow_zoom = 9 - zoom;
				String url = this.baseURL;
				if (y >= Math.pow(2, wow_zoom - 1)) {
					url = "http://int2e.com/gmapoutland2/";
				}
				return url + "zoom" + wow_zoom + "maps/" + x + "_" + y + "_" + wow_zoom + ".jpg";
			}
		};
		DefaultTileFactory defaultTileFactory = new DefaultTileFactory(info);
		return defaultTileFactory;
	}
	
	/**
	 * @param values
	 *            { x0, y0, r0, x1, y1, r1 }
	 * @return true if circles is intersected Check if circle is intersect to
	 *         another circle
	 */
	public static boolean isCircleIntersect(double... values) {
		/*
		 * check using mathematical relation: ABS(R0-R1) <=
		 * SQRT((x0-x1)^2+(y0-y1)^2) <= (R0+R1)
		 */
		if (values.length == 6) {
			/* get values from first circle */
			double x0 = values[0];
			double y0 = values[1];
			double r0 = values[2];
			/* get values from second circle */
			double x1 = values[3];
			double y1 = values[4];
			double r1 = values[5];
			/* return result */
			return (Math.abs(r0 - r1) <= Math.sqrt(Math.pow((x0 - x1), 2) + Math.pow((y0 - y1), 2)))
					&& (Math.sqrt(Math.pow((x0 - x1), 2) + Math.pow((y0 - y1), 2)) <= (r0 + r1));
		} else {
			/* return default result */
			return false;
		}
	}
}
