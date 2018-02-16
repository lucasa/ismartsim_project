import org.jxmapviewer.viewer.GeoPosition;

public class ExperimentoPDP extends SmartCitySimulation {

	private static int REPETICOES = 30;

	public static void main(String[] args) {		
		String cityName = "Porto Alegre";
		cityGeoPosition = new GeoPosition(-30.04717901252634, -51.21553659439087);
		// Procempa Data Center
		cloudGeoPosition = new GeoPosition(-30.043709, -51.215665);
		userGeoPosition = new GeoPosition(-30.04709078575702, -51.21131479740143);
		
		FogSimulation.PROCESS_AT_CLOUD = false;
		FogSimulation.MAX_SIMULATION_TIME = 60 * 60; // 1h
		
		SmartCitySimulation smartCityGeoSimulation = new SmartCitySimulation();
		smartCityGeoSimulation.loadSensorGeoPositionsFromFile(cloudGeoPosition, cloudGeoPosition, "speed_camera_poa.geojson");		
//		smartCityGeoSimulation.loadSensorGeoPositionsFromFile(cloudGeoPosition, cloudGeoPosition, "traffic_signals_poa.geojson");
		smartCityGeoSimulation.printSimulationGeoInfos();
		
		if(args.length == 1) {
			String logsPath = args[0];
			try {
				smartCityGeoSimulation.createMapInterface(cityName, cityGeoPosition, cloudGeoPosition, userGeoPosition);
				smartCityGeoSimulation.importJsonEventsLog(logsPath);
				smartCityGeoSimulation.startAnimation();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {			
			for (int i = 0; i < REPETICOES; i++) {
				FogSimulation simulation = smartCityGeoSimulation.newSimulation();
				simulation.simulate(cloudGeoPosition, userGeoPosition, sensorsGeoPositions,
						wifiSpotsGeoPositions);
				smartCityGeoSimulation.extractSimulationInfos(false);
				System.out.println("End simulation " + (i+1));
			}
			
		}
			
		
	}

}
