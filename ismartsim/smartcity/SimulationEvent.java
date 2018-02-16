import java.io.Serializable;

import org.fog.entities.Tuple;

public class SimulationEvent implements Serializable, Comparable<SimulationEvent> {
	public int tag;
	public int type;
	public int destFogDeviceId;
	public int srcFogDeviceId;
	public String srcDevice;
	public String destDevice;
	public String eventName;
	public double clock;
	public Tuple tuple;
	public double transmissionTime;
	public double executionTime;
	

	@Override
	public int compareTo(SimulationEvent o) {
		if (this.clock < o.clock)
			return -1;
		if (this.clock > o.clock)
			return 1;

		return 0;
	}
	
	@Override
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("\n\nEvent: " + this.clock);
		buffer.append("\nName: " + this.eventName);
		buffer.append("\nFrom device: " + this.srcDevice);
		buffer.append("\nTo device: " + this.destDevice);
		if(tuple!=null) {
			buffer.append("\n\tTuple: " + tuple.getCloudletFileSize());
			buffer.append("\n\tDirection: " + (tuple.getDirection() == Tuple.UP ? "UP" : "DOWN"));
			buffer.append("\n\tFrom module: " + tuple.getSrcModuleName());
			buffer.append("\n\tTo module: " + tuple.getDestModuleName());
			buffer.append("\n\tTransmission time: " + transmissionTime);
			buffer.append("\n\tExecution time: " + executionTime);
		}
		return buffer.toString();
	}
		
}
