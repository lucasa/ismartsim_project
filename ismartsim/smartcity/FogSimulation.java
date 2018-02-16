import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Vector;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacement;
import org.fog.placement.ModulePlacementMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.Config;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.GeoLocation;
import org.fog.utils.NetworkUsageMonitor;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;
import org.fog.utils.distribution.Distribution;
import org.fog.utils.distribution.NormalDistribution;
import org.jxmapviewer.viewer.GeoPosition;

import util.LogOutputStream;

/**
 * Simulation setup for case study 2 - Intelligent Surveillance
 *
 * @author Lucas Alberto
 *
 */
public class FogSimulation extends Observable {

	private static String APP = "Smart Cameras Fog Simulaton";
	private static List<FogDevice> allSimulatedDevices = new ArrayList<FogDevice>();
	private static SpecialFogDevice cloud = null;
	private static List<Sensor> sensors = new ArrayList<Sensor>();
	private static List<Actuator> actuators = new ArrayList<Actuator>();
	private static List<SpecialFogDevice> allWifiNetworkDevices = new ArrayList<SpecialFogDevice>();
	private static List<SpecialFogDevice> allEdgeDevices = new ArrayList<SpecialFogDevice>();
	
	public static BufferedReader input;

	private static Controller controller;
	private static Application application;
	private static FogBroker broker;
	private static String appId;
	
	// latency ofconnection between wifi and cloud server is 100 milliseconds ms
	public static boolean PROCESS_AT_CLOUD = true;
	public static float WIFI_CLOUD_LATENCY = 100f;
	public static float FOG_TO_WIFI_LATENCY = 10f;
	public static float WIFI_GEO_RANGE = 0.001f; //TODO: calculate the area in meter 
	public static float CLOUD_TO_USER_LATENCY = 100f;

	private static String localEdgeDevice = "local-video-server";
	private static String cloudDevice = "cloud";
	private static String userMobileDevice = "user-mobile-android";
	// private static String actuatorMobileDevice = "DISPLAY";

	private static String localSensorModule = "VIDEOFRAME";
	private static String captureFogModule = "video_motion_detector";
	private static String postProcessingModule = "post-processing";
	private static String highProcessingModule = "car_deep_detector";
	private static String userClientGuiModule = "user_interface";

	private static String TUPLE_FRAME = localSensorModule;

	public static int MAX_SIMULATION_TIME = 300;
		
	public static double FRAMES_PER_SECOND = 2.0; // Camera capture x frames per second
	
	public static double FRAMES_SIZE = 1.5; // // each frame is a 1.5 MB JPEG
	public static double FRAME_MIPS_PREPROCESSING_PHASE = 500; // MIPS consumed
	public static double FRAME_MIPS_POSTPROCESSING_PHASE = 500; // MIPS consumed
	public static double FRAME_MIPS_MACHINE_LEARNING_PHASE = 2984; // MIPS consumed
	// very low CPU, basically networking, cut and pre-processing images
	private static double TUPLE_FOG_MIPS_PER_SEC = FRAMES_PER_SECOND * FRAME_MIPS_PREPROCESSING_PHASE; // 1000 MIPS
	// MIPS for 1 image = 0,51 * 5851 = 2984,01 x 2 Frames/second = ?
	private static double TUPLE_HIGH_MACHINE_LEARNING_MIPS_PER_SEC = FRAMES_PER_SECOND * FRAME_MIPS_MACHINE_LEARNING_PHASE;
	// http://toolstud.io/photo/megapixel.php?width=1161&height=828&compare=photo&calculate=compressed
	private static double TUPLE_NETWORK_LENGTH = FRAMES_SIZE * FRAMES_PER_SECOND;
	
	// Percentage of positive results of ML algorithms
	public static float PERCENTAGE_OF_POSITIVE_TUPLES_SEND_TO_USER = 0.005f;

	public static int FOG_MIPS = 1000;
	public static int FOG_RAM = 1000; // MB
	public static int FOG_UP_BANDWIDTH = 10000; // Mb/s
	public static int FOG_DOWN_BANDWIDTH = 10000; // Mb/s
	
	public static int WIFI_UP_BANDWIDTH = 10000;
	public static int WIFI_DOWN_BANDWIDTH = 10000;
	
	public static int CLOUD_NUMBER_CPUS = 16;
	public static int CLOUD_MIPS = CLOUD_NUMBER_CPUS * 3000; // 3GHz
	public static int CLOUD_RAM = CLOUD_NUMBER_CPUS * 2000;
	public static int CLOUD_UP_BANDWIDTH = 10000;
	public static int CLOUD_DOWN_BANDWIDTH = 10000;

	private static FogSimulation instance;
	private List<SimulationEvent> allLogEvents = new Vector<SimulationEvent>();
	private double endClock = -1;
	private boolean endSimulation = false;
	private long TOTAL_MEMORY_START = 0;

	private FogSimulation() {

	}

	public static FogSimulation getInstance() {
		if (instance == null) {
			instance = new FogSimulation();
		}
		return instance;
	}
	
	public static void newInstance() {
		instance = new FogSimulation();
	}
	
	public String getSimulationIdFromConfig() {
		return "ProcessAt-" + getSimulationProcessingMode() + "_" + "MaxTime-"+MAX_SIMULATION_TIME + "_" + "TotalEdgeDevices-"+allEdgeDevices.size() + "_" + "FramesPerSecond-"+ FRAMES_PER_SECOND;
	}

	public void simulate(GeoPosition geoCloud, GeoPosition geoUserClient, List<GeoPosition> geoCameras,
			List<GeoPosition> wifiSpotsGeoPositions) {
		
		NetworkUsageMonitor.newInstance();
		TimeKeeper.newInstance();
		TOTAL_MEMORY_START = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
		
		// set the max simulation time when simulation will finish
		Config.MAX_SIMULATION_TIME = MAX_SIMULATION_TIME;

		Log.printLine("Simulatin allocation mode: " + getSimulationProcessingMode());
		Log.printLine("Starting " + APP + " ...");

		try {
			File file = new File("entries/data");
			Log.printLine("DATA: " + file.getAbsolutePath());
			Application.datainput = new BufferedReader(new FileReader(file));
		} catch (IOException e) {
			e.printStackTrace();
		}

		try {
			
			Log.disable();
			//Log.enable();
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace events

			CloudSim.init(num_user, calendar, trace_flag);

			// identifier of the application
			appId = "Smart Traffic Signals";

			// broker submits the user application
			broker = new FogBroker("broker");

			application = createApplication(appId, broker.getId());
			application.setUserId(broker.getId());

			// create and connect the cloud data center, intermediate fog
			// devices and sensors
			allSimulatedDevices = createAllFogDevices(broker.getId(), appId, geoCloud, geoUserClient, geoCameras,
					wifiSpotsGeoPositions);
			
			// print some infos
			Log.printLine(getSimulationInfoPre());

			// map the application modules to the cpus
			ModuleMapping moduleMapping = mapModulesToDevices(FogSimulation.PROCESS_AT_CLOUD);

			ModulePlacement modulePlacement = null;
			modulePlacement = new ModulePlacementMapping(allSimulatedDevices, application, moduleMapping);
			// modulePlacement = new ModulePlacementEdgewards(fogDevices,
			// sensors, actuators, application, moduleMapping);

			// if (PROCESS_AT_CLOUD) {
			// moduleMapping = new ModulePlacementMapping(fogDevices,
			// application, moduleMapping);
			// } else {
			// moduleMapping = new ModulePlacementEdgewards(fogDevices, sensors,
			// actuators, application,
			// moduleMapping);
			// }
			
			Log.setOutput(new LogOutputStream());

			controller = new Controller("master-controller", cloudDevice, allSimulatedDevices, sensors, actuators);
			controller.submitApplication(application, modulePlacement);

			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

			allLogEvents = new Vector<SimulationEvent>();
			
			// run simulation
			endClock = CloudSim.startSimulation();
			
			Log.printLine("-------------------- Devices Processing Information --------------------");
			for (FogDevice device : controller.getFogDevices()) {
				SpecialFogDevice d = (SpecialFogDevice) device;
				Log.printLine("Fog Device: " + d.getId() + " - " + d.getName());
				long tuplesCountUp = NetworkUsageMonitor.getInstance().getFogDeviceTuplesTransmitedUp(d);
				long tuplesCountDown = NetworkUsageMonitor.getInstance().getFogDeviceTuplesTransmitedDown(d);
				long tuplesCountReceived = NetworkUsageMonitor.getInstance().getFogDeviceTuplesTransmitedReceived(d);
				Log.printLine("\ttuples down = " + tuplesCountDown);
				Log.printLine("\ttuples up = " + tuplesCountUp);
				Log.printLine("\ttuples received = " + tuplesCountReceived);
				Log.printLine();

				List<SimulationEvent> loggedEvents = d.getLoggedEvents();
				allLogEvents.addAll(loggedEvents);
			}
			Log.printLine("------------------------------------------------------------------------");

			Collections.sort(allLogEvents);
			
			for (SimulationEvent sv : allLogEvents) {
				if (sv.tuple != null) {
					int sourceDeviceId = sv.srcFogDeviceId;
					FogDevice srcFogDevice = getFogDeviceById(sourceDeviceId);
					if (srcFogDevice != null) {
						sv.srcDevice = srcFogDevice.getName();
					}
					int destDeviceId = sv.destFogDeviceId;
					FogDevice destFogDevice = getFogDeviceById(destDeviceId);
					if (destFogDevice != null) {
						sv.destDevice = destFogDevice.getName();
					}
				}

				if(Config.DEBUG) Log.printLine(sv);
				
			}
			
			Log.printLine(APP + " finished at clock " + endClock + " !");
		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors!!!");
		}

	}

	public static String getSimulationProcessingMode() {
		return FogSimulation.PROCESS_AT_CLOUD ? "CLOUD-CENTRAL-PROCESSING" : "EDGE-DISTRUBUTED-PROCESSING";
	}

	/**
	 * Creates the fog devices in the physical topology of the simulation.
	 * 
	 * @param geoUserClient
	 * @return
	 */
	private static List<FogDevice> createAllFogDevices(int userId, String appId, GeoPosition geoCloud,
			GeoPosition geoUserClient, List<GeoPosition> geoSensors, List<GeoPosition> geoWifiSpots) {
		List<FogDevice> allFogCloudDevices = new ArrayList<FogDevice>();

		// create the user device that receives the application results 
		FogDevice mobileUserPhone = addMobileUserDevice(userId, appId, geoUserClient, null);
		allFogCloudDevices.add(mobileUserPhone);

		// create the cloud datacenter
		FogDevice router = createCloudDataCenter(geoCloud, allFogCloudDevices, mobileUserPhone.getId());

		// create all the private network wifi spots
		allWifiNetworkDevices = createWifiSpots(userId, appId, geoWifiSpots, router);
		allFogCloudDevices.addAll(allWifiNetworkDevices);

		// create all the edge fog devices (camera + fog node), connect it to the local detected wifi
		allEdgeDevices = createCaptureDevices(userId, appId, geoSensors, geoWifiSpots,
				allWifiNetworkDevices);
		allFogCloudDevices.addAll(allEdgeDevices);

		return allFogCloudDevices;
	}

	private static List<SpecialFogDevice> createWifiSpots(int userId, String appId, List<GeoPosition> geoWifiSpots,
			FogDevice cloudRouter) {		
		List<SpecialFogDevice> wifiSpots = new ArrayList<SpecialFogDevice>();
		int i = 0;
		// cerate a wifi network device at each geo local point
		for (GeoPosition geoWifi : geoWifiSpots) {
			SpecialFogDevice wifiRouter = createFogDevice(FOG_DEVICE_TYPE.WIFI_ROUTER, "wifi-router-" + i, 2800, 4000, WIFI_UP_BANDWIDTH,
					WIFI_DOWN_BANDWIDTH, 2, 0.0, 107.339, 83.4333);
			// wifiRouter.setWifiDistanceRange(WIFI_GEO_RANGE);
			wifiRouter.setGeoLocation(new GeoLocation(geoWifi.getLatitude(), geoWifi.getLongitude()));
			wifiRouter.setUplinkLatency(WIFI_CLOUD_LATENCY);
			wifiRouter.setParentId(cloudRouter.getId());
			wifiSpots.add(wifiRouter);
			i++;
		}
		return wifiSpots;
	}

	private static List<SpecialFogDevice> createCaptureDevices(int userId, String appId, List<GeoPosition> geoCameras,
			List<GeoPosition> networkGeoPositions, List<SpecialFogDevice> networkDevices) {
		List<SpecialFogDevice> sensorsDevices = new ArrayList<SpecialFogDevice>();
		int i = 0;
		// adding a smart sensors to the physical topology. Smart sensors
		// have been modeled as a sentor + a fog device.
		for (GeoPosition geo : geoCameras) {
			// detect which one wifi cell can be connected by the sensor
			GeoPosition wifiCellGeoPositon = getWifiSpotByCoverageSignalArea(networkGeoPositions, geo);
			FogDevice gateway = null;
			// if the sensor is inside a wifi cell
			for (SpecialFogDevice netDevice : networkDevices) {
				if (netDevice.getGeoPosition().equals(wifiCellGeoPositon)) {
					gateway = netDevice;
					break;
				}
			}
			// connect with the wifi cell detected and do not simulate offline sensors
			if (gateway != null) {
				SpecialFogDevice camera = addCamera(i, userId, appId, gateway.getId(), geo);
				// and router is 2 ms
				sensorsDevices.add(camera);
			}

			i++;
		}
		return sensorsDevices;
	}

	private static GeoPosition getWifiSpotByCoverageSignalArea(List<GeoPosition> networkGeoPositions,
			GeoPosition geoSensor) {
		for (GeoPosition geoNetDevice : networkGeoPositions) {
			if (Math.abs(geoNetDevice.getLatitude() - geoSensor.getLatitude()) < WIFI_GEO_RANGE
					&& Math.abs(geoNetDevice.getLongitude() - geoSensor.getLongitude()) < WIFI_GEO_RANGE) {
				return geoNetDevice;
			}
		}
		return null;
	}

	private static SpecialFogDevice createCloudDataCenter(GeoPosition geo, List<FogDevice> fogDevices,
			Integer parentId) {		
		cloud = createFogDevice(FOG_DEVICE_TYPE.CLOUD, cloudDevice, CLOUD_MIPS, CLOUD_RAM, CLOUD_UP_BANDWIDTH, CLOUD_DOWN_BANDWIDTH, 0, 0.01, CLOUD_NUMBER_CPUS * 107.339,
				CLOUD_NUMBER_CPUS * 83.25);
		if (parentId != null) {
			cloud.setParentId(parentId);
		} else {
			cloud.setParentId(-1);
		}
		cloud.setUplinkLatency(CLOUD_TO_USER_LATENCY);
		cloud.setGeoLocation(new GeoLocation(geo.getLatitude(), geo.getLongitude()));
		fogDevices.add(cloud);
		
		SpecialFogDevice proxy = createFogDevice(FOG_DEVICE_TYPE.NETWORKING, "cloud-proxy-server", 2800, 4000, CLOUD_UP_BANDWIDTH, CLOUD_DOWN_BANDWIDTH, 1, 0.0, 107.339,
				83.4333);
		proxy.setParentId(cloud.getId());
		// latency of connection between proxy server and cloud is 100 ms
		proxy.setUplinkLatency(5f);
		proxy.setGeoLocation(new GeoLocation(geo.getLatitude(), geo.getLongitude()));
		fogDevices.add(proxy);

		SpecialFogDevice router = createFogDevice(FOG_DEVICE_TYPE.NETWORKING, "cloud-router", 2800, 4000, CLOUD_UP_BANDWIDTH, CLOUD_DOWN_BANDWIDTH, 1, 0.0, 107.339, 83.4333);
		fogDevices.add(router);
		// latency of connection between router and proxy server is 2 ms
		router.setUplinkLatency(5f);
		router.setParentId(proxy.getId());
		router.setGeoLocation(new GeoLocation(geo.getLatitude(), geo.getLongitude()));
		
		return router;
	}

	private static SpecialFogDevice addCamera(int i, int userId, String appId, int parentId, GeoPosition geo) {
		SpecialFogDevice localEdgeDevice = createFogDevice(FOG_DEVICE_TYPE.FOG, FogSimulation.localEdgeDevice + "-" + i, FOG_MIPS, FOG_RAM,
				FOG_UP_BANDWIDTH, FOG_DOWN_BANDWIDTH, 3, 0, 87.53, 82.44);
		localEdgeDevice.setParentId(parentId);
		localEdgeDevice.setUplinkLatency(FOG_TO_WIFI_LATENCY);
		localEdgeDevice.setGeoPosition(geo);

		Distribution distribution = new NormalDistribution(FRAMES_PER_SECOND, 0.1);
		{
			// inter-transmission time of camera (sensor) follows a distribution
			//Distribution distribution =  new DeterministicDistribution(FRAMES_PER_SECOND);
		}
		Sensor sensor = new AdvandedSensor("sensor-" + i, FogSimulation.TUPLE_FRAME, userId, appId,
				distribution, FRAME_MIPS_PREPROCESSING_PHASE);
		GeoLocation geoLocation = new GeoLocation(geo.getLatitude(), geo.getLongitude());
		// latency of connection between camera (sensor) and the parent Smart
		// Camera is 1 ms
		sensor.setGeoLocation(geoLocation);
		sensor.setGatewayDeviceId(localEdgeDevice.getId());
		sensor.setLatency(1.0);
		sensors.add(sensor);
		return localEdgeDevice;
	}

	private static FogDevice addMobileUserDevice(int userId, String appId, GeoPosition geo, Integer parentId) {
		int USER_MIPS = 1000;
		int USER_RAM = 1000;
		int USER_UP_BANDWIDTH = 10000;
		int USER_DOWN_BANDWIDTH = 10000;
		
		SpecialFogDevice mobileClient = createFogDevice(FOG_DEVICE_TYPE.MOBILE, userMobileDevice, USER_MIPS, USER_RAM, USER_UP_BANDWIDTH, USER_DOWN_BANDWIDTH, 3, 0, 87.53, 82.44);
		if (parentId != null) {
			mobileClient.setParentId(parentId);
		} else {
			mobileClient.setParentId(-1);
		}
		mobileClient.setUplinkLatency(5f);
		mobileClient.setGeoLocation(new GeoLocation(geo.getLatitude(), geo.getLongitude()));
		allSimulatedDevices.add(mobileClient);

		// Actuator display = new Actuator(actuatorMobileDevice, userId, appId,
		// actuatorMobileDevice);
		// // display.setGeoLocation(FogSimulation.);
		// display.setGatewayDeviceId(mobileClient.getId());
		// display.setLatency(1.0); // latency of connection between Display
		// // actuator and the parent Smartphone is 1
		// // ms
		// actuators.add(display);

		return mobileClient;
	}

	/**
	 * Creates a vanilla fog device
	 *
	 * @param nodeName name of the device to be used in simulation
	 * @param mips MIPS
	 * @param ram RAM
	 * @param upBw uplink bandwidth
	 * @param downBw downlink bandwidth
	 * @param level hierarchy level of the device
	 * @param ratePerMips cost rate per MIPS used
	 * @param busyPower
	 * @param idlePower
	 * @return
	 */
	public static SpecialFogDevice createFogDevice(FOG_DEVICE_TYPE type, String nodeName, long mips, int ram,
			long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {

		List<Pe> peList = new ArrayList<Pe>();

		// 3. Create PEs and add these into a list.
		// need to store Pe id and MIPS Rating
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));

		int hostId = FogUtils.generateEntityId();
		long storage = 1000000; // host storage
		int bw = 10000;

		PowerHost host = new PowerHost(hostId, new RamProvisionerSimple(ram), new BwProvisionerOverbooking(bw), storage,
				peList, new StreamOperatorScheduler(peList), new FogLinearPowerModel(busyPower, idlePower));

		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);

		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
		// resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>();
		// we are not adding SAN devices by now
		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(arch, os, vmm, host, time_zone, cost,
				costPerMem, costPerStorage, costPerBw);

		SpecialFogDevice fogdevice = null;
		try {
			fogdevice = new SpecialFogDevice(type, nodeName, characteristics, new AppModuleAllocationPolicy(hostList),
					storageList, 10, upBw, downBw, 0, ratePerMips);
		} catch (Exception e) {
			e.printStackTrace();
		}

		fogdevice.setLevel(level);

		return fogdevice;
	}

	/**
	 * Function to create the Intelligent Surveillance application in the DDF
	 * model.
	 *
	 * @param appId
	 *            unique identifier of the application
	 * @param userId
	 *            identifier of the user of the application
	 * @return
	 */
	@SuppressWarnings({ "serial" })
	private static Application createApplication(String appId, int userId) {

		Application application = Application.createApplication(appId, userId);
		/*
		 * Adding modules (vertices) to the application model (directed graph)
		 */
		application.addAppModule(FogSimulation.captureFogModule, 10);
		application.addAppModule(FogSimulation.highProcessingModule, 10);
		application.addAppModule(FogSimulation.postProcessingModule, 10);		
		application.addAppModule(FogSimulation.userClientGuiModule, 10);

		application.addAppEdge(localSensorModule, FogSimulation.captureFogModule, 
				FRAME_MIPS_PREPROCESSING_PHASE, TUPLE_NETWORK_LENGTH, localSensorModule, Tuple.UP, AppEdge.SENSOR);
		application.addAppEdge(FogSimulation.captureFogModule, FogSimulation.highProcessingModule,
				TUPLE_FOG_MIPS_PER_SEC, TUPLE_NETWORK_LENGTH, FogSimulation.TUPLE_FRAME, Tuple.UP, AppEdge.MODULE);
//		application.addAppEdge(FogSimulation.highProcessingModule, userClientGuiModule, 
//				TUPLE_HIGH_MACHINE_LEARNING_MIPS_PER_SEC, TUPLE_NETWORK_LENGTH, TUPLE_FRAME, Tuple.UP, AppEdge.MODULE);
		
		application.addAppEdge(FogSimulation.highProcessingModule, FogSimulation.postProcessingModule, 
				TUPLE_HIGH_MACHINE_LEARNING_MIPS_PER_SEC, TUPLE_NETWORK_LENGTH, TUPLE_FRAME, Tuple.UP, AppEdge.MODULE);
		application.addAppEdge(FogSimulation.postProcessingModule, userClientGuiModule, 
				FRAME_MIPS_POSTPROCESSING_PHASE, TUPLE_NETWORK_LENGTH, TUPLE_FRAME, Tuple.UP, AppEdge.MODULE);

		application.addTupleMapping(FogSimulation.captureFogModule, FogSimulation.TUPLE_FRAME,
				FogSimulation.TUPLE_FRAME, new FractionalSelectivity(0.98)); // ~1.0
		application.addTupleMapping(FogSimulation.highProcessingModule, FogSimulation.TUPLE_FRAME,
				FogSimulation.TUPLE_FRAME, new FractionalSelectivity(PERCENTAGE_OF_POSITIVE_TUPLES_SEND_TO_USER));
		application.addTupleMapping(FogSimulation.postProcessingModule, FogSimulation.TUPLE_FRAME,
				FogSimulation.TUPLE_FRAME, new FractionalSelectivity(0.98));

		// application.addTupleMapping(FogSimulation.userClientGuiModule,
		// FogSimulation.TUPLE_FRAME,
		// FogSimulation.TUPLE_FRAME, new FractionalSelectivity(1.0));

		/*
		 * Defining application loops (maybe incomplete loops) to monitor the
		 * latency of. Here, we add two loops for monitoring : Motion Detector
		 * -> Object Detector -> Object Tracker and Object Tracker -> ACTUATOR
		 * Control
		 */
		final AppLoop loop1 = new AppLoop(new ArrayList<String>() {
			{
				// add(FogSimulation.localSensorModule);
				// add(FogSimulation.captureFogModule);
			}
		});
		final AppLoop loop2 = new AppLoop(new ArrayList<String>() {
			{
				// add(FogSimulation.captureFogModule);
				// add(FogSimulation.highProcessingModule);
			}
		});
		final AppLoop loop3 = new AppLoop(new ArrayList<String>() {
			{
				// add(FogSimulation.highProcessingModule);
				// add(FogSimulation.userClientGuiModule);
			}
		});
		final AppLoop loop4 = new AppLoop(new ArrayList<String>() {
			{
				// add(FogSimulation.highProcessingModule);
				// add(FogSimulation.actuatorMobileDevice);
			}
		});
		final AppLoop loop5 = new AppLoop(new ArrayList<String>() {
			{
				add(FogSimulation.localSensorModule);
				add(FogSimulation.captureFogModule);
				add(FogSimulation.highProcessingModule);
				add(FogSimulation.postProcessingModule);
				add(FogSimulation.userClientGuiModule);
			}
		});
		List<AppLoop> loops = new ArrayList<AppLoop>() {
			{
				add(loop1);
				add(loop2);
				add(loop3);
				add(loop4);
				add(loop5);
			}
		};

		application.setLoops(loops);
		return application;
	}

	private static ModuleMapping mapModulesToDevices(boolean highProcessAtcloud) {
		// initializing a module mapping
		ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
		for (FogDevice device : allSimulatedDevices) {
			// for all cameras
			if (device.getName().startsWith(localEdgeDevice)) {
				// fixing 1instance of the Motion Detector module to each Smart Camera
				moduleMapping.addModuleToDevice(FogSimulation.captureFogModule, device.getName());

				// if processing at FOG place module to edge devices 
				if (!highProcessAtcloud) {
					moduleMapping.addModuleToDevice(highProcessingModule, device.getName());
				}
			}
		}

		// if processing at CLOUD place module to the cloud center
		if (highProcessAtcloud) {
			// if the mode of deployment is cloud-based placing all of
			// Object Detector module in the Cloud
			moduleMapping.addModuleToDevice(highProcessingModule, cloudDevice);
		}

		moduleMapping.addModuleToDevice(postProcessingModule, cloudDevice);
		
		// fixing instances of User Interface module in the user mobile device
		moduleMapping.addModuleToDevice(userClientGuiModule, userMobileDevice);

		return moduleMapping;
	}

	public FogDevice getFogDeviceById(int id) {
		for (FogDevice fogDevice : allSimulatedDevices) {
			if (id == fogDevice.getId())
				return fogDevice;
		}
		return null;
	}

	public static FogDevice getFogDeviceByGeoPosition(GeoPosition geo) {
		for (FogDevice d : allSimulatedDevices) {
			GeoPosition geoPosition = ((SpecialFogDevice) d).getGeoPosition();
			if (geoPosition != null && geoPosition.equals(geo)) {
				return d;
			}
		}
		return null;
	}

	public static Long getFogDeviceNetworkUsageByGeoPosition(GeoPosition geo) {
		SpecialFogDevice d = (SpecialFogDevice) getFogDeviceByGeoPosition(geo);
		if (d != null) {
			return NetworkUsageMonitor.getInstance().getMapFogDeviceNetworkUsage().get(d);
		} else {
			return null;
		}
	}

	public List<SimulationEvent> getAllLogEvents() {
		return allLogEvents;
	}

	public double getEndClock() {
		return endClock;
	}

//	public int getLogEventsCount() {
//		int i = 0;
//		if (CloudSim.running()) {
//			for (SimEntity entity : CloudSim.getEntityList()) {
//				if (entity instanceof SpecialFogDevice) {
//					SpecialFogDevice sf = (SpecialFogDevice) entity;
//					i += sf.getLoggedEvents().size();
//				}
//			}
//		}
//		return i;
//	}

	public synchronized boolean isEndSimulation() {
		return endSimulation;
	}
	
	public String getSimulationInfoPre() {
		StringBuffer str = new StringBuffer();
		str.append("\n--- Information Setup BEFORE Execution ---");
		str.append("\nTotal Connected Fog Devices [1 sensor + 1 edge device]: " + allEdgeDevices.size());
		str.append("\nTotal Connected Network Devices [1 wifi 100 meters²]: " + allWifiNetworkDevices.size());
		str.append("\nTotal Cloud DataCenters: 1");
		str.append("\n");
		str.append("\nTotal Devices: " + allSimulatedDevices.size());
		return str.toString();
	}
	
	public String getFinalSimulationInfoCSV(boolean withHeader) {
		Map<String, Object> header = new LinkedHashMap<String, Object>();
		
		double time = (Calendar.getInstance().getTimeInMillis() - TimeKeeper.getInstance().getSimulationStartTime())/1000;
		header.put("Total Execution Time(s)", time);
		
		header.put("Logged Events", getAllLogEvents().size());
		
		long TOTAL_MEMORY = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) - TOTAL_MEMORY_START;
		double kb = TOTAL_MEMORY / 1024;
		header.put("Total Used Memory(GB)", kb / 1024 / 1024);
		
		header.put("Total Connected Fog Devices", allEdgeDevices.size());
		header.put("Total Connected Network Devices", allEdgeDevices.size());
		header.put("Total Connected Network Devices", allWifiNetworkDevices.size());
		header.put("Total Cloud DataCenters", 1);
		header.put("Total network Usage(MB)", BigDecimal.valueOf(NetworkUsageMonitor.getInstance().getNetworkUsage()/Config.MAX_SIMULATION_TIME));
		
		BigDecimal totalPower = BigDecimal.ZERO;
		for(FogDevice fogDevice : allSimulatedDevices) {
			totalPower = totalPower.add((BigDecimal.valueOf(fogDevice.getEnergyConsumption())));
		}		
		header.put("Total All Power Consumed(Watt)", totalPower);
		
		header.put("Total Power Consumed by Cloud(Watt)", BigDecimal.valueOf(cloud.getEnergyConsumption()));		
		header.put("Total Cost of Cloud Services", BigDecimal.valueOf(cloud.getTotalCost()));
		
		BigDecimal totalEdgePower = BigDecimal.ZERO;
		BigDecimal totalEdgeCost = BigDecimal.ZERO;
		for(FogDevice fogDevice : allSimulatedDevices) {
			totalEdgePower = totalEdgePower.add((BigDecimal.valueOf(fogDevice.getEnergyConsumption())));
			totalEdgeCost = totalEdgeCost.add((BigDecimal.valueOf(fogDevice.getTotalCost())));
		}		
		header.put("Total Power Consumed by Edge Device(Watt)", totalEdgePower);
		header.put("Total Cost of Edge Devices", totalEdgeCost);
		
		
		for(Integer loopId : TimeKeeper.getInstance().getLoopIdToTupleIds().keySet()){
			/*double average = 0, count = 0;
			for(int tupleId : TimeKeeper.getInstance().getLoopIdToTupleIds().get(loopId)){
				Double startTime = 	TimeKeeper.getInstance().getEmitTimes().get(tupleId);
				Double endTime = 	TimeKeeper.getInstance().getEndTimes().get(tupleId);
				if(startTime == null || endTime == null)
					break;
				average += endTime-startTime;
				count += 1;				
			}
			System.out.println(getStringForLoopId(loopId) + " ---> "+(average/count));*/
			header.put("Loop Delay Average - "+controller.getStringForLoopId(loopId), TimeKeeper.getInstance().getLoopIdToCurrentAverage().get(loopId));
		}
		
		
		StringBuffer headerStr = new StringBuffer();
		StringBuffer rowStr = new StringBuffer();
		for (String property : header.keySet()) {
			if(headerStr.length() > 0) {
				headerStr.append(";");
				rowStr.append(";");
			}
			
			headerStr.append(property);			
			rowStr.append(header.get(property));
		}
		
		if(withHeader)
			return headerStr.append("\n").append(rowStr).toString();
		else
			return "\n"+rowStr.toString();
	}
}




