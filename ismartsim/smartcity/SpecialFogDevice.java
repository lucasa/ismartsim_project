import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Vector;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Tuple;
import org.fog.utils.Config;
import org.fog.utils.FogEvents;
import org.fog.utils.GeoLocation;
import org.fog.utils.Logger;
import org.fog.utils.NetworkUsageMonitor;
import org.fog.utils.TimeKeeper;
import org.jxmapviewer.viewer.GeoPosition;

public class SpecialFogDevice extends FogDevice {

	private static final List MONITORED_EVENTS = Arrays.asList(
		FogEvents.TUPLE_ARRIVAL
	);
	private GeoLocation geoLocation;
	private List<SimulationEvent> monitoredFogEvents = new Vector<SimulationEvent>();
	private FOG_DEVICE_TYPE type;
	private double wifiAreaDiamater = -1; 

	public double getWifiAreaDiamater() {
		return wifiAreaDiamater;
	}

	public void setWifiAreaDiamater(double wifiAreaDiamater) {
		this.wifiAreaDiamater = wifiAreaDiamater;
	}

	public SpecialFogDevice(FOG_DEVICE_TYPE type, String name, FogDeviceCharacteristics characteristics,
			VmAllocationPolicy vmAllocationPolicy, List<Storage> storageList, double schedulingInterval,
			double uplinkBandwidth, double downlinkBandwidth, double uplinkLatency, double ratePerMips)
			throws Exception {
		super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval, uplinkBandwidth,
				downlinkBandwidth, uplinkLatency, ratePerMips);
		this.type = type;
	}

	public GeoLocation getGeoLocation() {
		return geoLocation;
	}

	public void setGeoLocation(GeoLocation geoLocation) {
		this.geoLocation = geoLocation;
	}

	public void setGeoPosition(GeoPosition geoPosition) {
		this.geoLocation = new GeoLocation(geoPosition.getLatitude(), geoPosition.getLongitude());
	}

	public GeoPosition getGeoPosition() {
		return geoLocation != null ? new GeoPosition(geoLocation.getLatitude(), geoLocation.getLongitude()) : null;
	}

	@Override
	public String toString() {
		return this.getName();
	}

	@Override
	protected void processOtherEvent(SimEvent ev) {
		logSimulationEvent(ev);
		super.processOtherEvent(ev);
	}

	private void logSimulationEvent(SimEvent ev) {
		if (ev.getSource() != ev.getDestination()) {

			if (MONITORED_EVENTS.contains(ev.getTag())) {
				SimulationEvent sv = new SimulationEvent();
				sv.srcFogDeviceId = ev.getSource();
				sv.destFogDeviceId = ev.getDestination();
				sv.clock = CloudSim.clock();
				Tuple tuple = (Tuple) ev.getData();
				sv.tuple = tuple;
				sv.srcDevice = CloudSim.getEntityName(sv.srcFogDeviceId);
				sv.destDevice = CloudSim.getEntityName(sv.destFogDeviceId);

				if (ev.getTag() == FogEvents.TUPLE_ARRIVAL) {
					sv.eventName = "TUPLE_ARRIVAL";
					sv.transmissionTime = tuple.getLastTransmitTime();
					getLoggedEvents().add(sv);
				}
				if (ev.getTag() == FogEvents.TUPLE_FINISHED) {
					sv.eventName = "TUPLE_FINISHED";
					sv.executionTime = tuple.getActualCPUTime();
				}
				
				getLoggedEvents().add(sv);
				//System.out.println(sv);
			}

		}
	}

	public List<SimulationEvent> getLoggedEvents() {
		return monitoredFogEvents;
	}

	@Override
	protected void checkCloudletCompletion() {
		boolean cloudletCompleted = false;
		List<? extends Host> list = getVmAllocationPolicy().getHostList();
		for (int i = 0; i < list.size(); i++) {
			Host host = list.get(i);
			for (Vm vm : host.getVmList()) {
				while (vm.getCloudletScheduler().isFinishedCloudlets()) {
					Cloudlet cl = vm.getCloudletScheduler().getNextFinishedCloudlet();
					if (cl != null) {

						cloudletCompleted = true;
						Tuple tuple = (Tuple) cl;
						TimeKeeper.getInstance().tupleEndedExecution(tuple);
						Application application = getApplicationMap().get(tuple.getAppId());
						Logger.debug(getName(), "Completed execution of tuple " + tuple.getCloudletId() + "on "
								+ tuple.getDestModuleName());

						{
							SimulationEvent sv = new SimulationEvent();
							sv.destFogDeviceId = this.getId();
							sv.clock = CloudSim.clock();
							sv.tuple = tuple;
							sv.eventName = "TUPLE_EXECUTE";
							// getLoggedEvents().add(sv);
						}

						List<Tuple> resultantTuples = application.getResultantTuples(tuple.getDestModuleName(), tuple,
								getId(), vm.getId());
						for (Tuple resTuple : resultantTuples) {
							resTuple.setModuleCopyMap(new HashMap<String, Integer>(tuple.getModuleCopyMap()));
							resTuple.getModuleCopyMap().put(((AppModule) vm).getName(), vm.getId());
							updateTimingsOnSending(resTuple);
							sendToSelf(resTuple);
						}
						sendNow(cl.getUserId(), CloudSimTags.CLOUDLET_RETURN, cl);
					}
				}
			}
		}
		if (cloudletCompleted)
			updateAllocatedMips(null);
	}

	@Override
	protected void sendUpFreeLink(Tuple tuple) {
		double networkDelay = tuple.getCloudletFileSize() / getUplinkBandwidth();
		setNorthLinkBusy(true);
		send(getId(), networkDelay, FogEvents.UPDATE_NORTH_TUPLE_QUEUE);
		send(parentId, networkDelay + getUplinkLatency(), FogEvents.TUPLE_ARRIVAL, tuple);
		NetworkUsageMonitor.getInstance().sendingTuple(getUplinkLatency(), tuple.getCloudletFileSize());
		NetworkUsageMonitor.getInstance().addUpTuple(this, tuple);
	}

	@Override
	public void schedule(int dest, double delay, int tag, Object data) {
		if(data instanceof Tuple) {
			int srcId = getId();
			if (dest != srcId) {// does not delay self messages
				Tuple tuple = (Tuple) data;
				tuple.setLastTransmitTime(delay);
			}
		}
		super.schedule(dest, delay, tag, data);
	}

	@Override
	protected void sendDownFreeLink(Tuple tuple, int childId) {
		double networkDelay = tuple.getCloudletFileSize() / getDownlinkBandwidth();
		// Logger.debug(getName(),"Sending tuple with tupleType = "+tuple.getTupleType()+" DOWN");
		setSouthLinkBusy(true);
		double latency = getChildToLatencyMap().get(childId);
		send(getId(), networkDelay, FogEvents.UPDATE_SOUTH_TUPLE_QUEUE);
		send(childId, networkDelay + latency, FogEvents.TUPLE_ARRIVAL, tuple);
		NetworkUsageMonitor.getInstance().sendingTuple(latency, tuple.getCloudletFileSize());
		NetworkUsageMonitor.getInstance().addDownTuple(this, tuple);
	}

	public FOG_DEVICE_TYPE getType() {
		return type;
	}
	
	@Override
	public void startEntity() {
		super.startEntity();
		// initial delay
		double delay = 50 * Math.random();
		System.out.println("Entity "+ getName() +" STARTING AT " + delay);
		pause(delay);
	}
}
