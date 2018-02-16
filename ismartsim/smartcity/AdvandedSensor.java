import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import org.cloudbus.cloudsim.UtilizationModelFull;
import org.fog.application.AppEdge;
import org.fog.application.Application;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.utils.FogEvents;
import org.fog.utils.FogUtils;
import org.fog.utils.Logger;
import org.fog.utils.distribution.Distribution;

public class AdvandedSensor extends Sensor {

	private double cpuMIPS = 0; // MB
	
	public AdvandedSensor(String name, String tupleType, int userId, String appId, Distribution transmitDistribution, double cpuMIPS) {
		super(name, tupleType, userId, appId, transmitDistribution);
		this.setAppId(appId);
		this.setTransmitDistribution(transmitDistribution);
		setTupleType(tupleType);
		setSensorName(tupleType);
		setUserId(userId);
		this.setCpuMIPS(cpuMIPS);
	}

	public void transmit() {
		AppEdge _edge = null;
		for (AppEdge edge : getApp().getEdges()) {
			if (edge.getSource().equals(getTupleType()))
				_edge = edge;
		}

//		String num = null;
//		try {
//			num = Application.datainput.readLine();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//
//		if (num == null || num.equals("")) {
//			DecimalFormat df = new DecimalFormat("#.000");
//			DecimalFormatSymbols sym = DecimalFormatSymbols.getInstance();
//			sym.setDecimalSeparator('.');
//			df.setDecimalFormatSymbols(sym);
//			if (metodoPreenchimento == 1)
//				num = df.format(last);
//			else if (metodoPreenchimento == 2) {
//				num = df.format((maior + menor) / 2);
//			}
//		}
//		double valor = Double.parseDouble(num);
//		if (metodoPreenchimento == 1)
//			last = valor;
//		else if (metodoPreenchimento == 2) {
//			if (valor > maior) {
//				maior = valor;
//			}
//			if (valor < menor) {
//				menor = valor;
//			}
//		}
//		long cpuLength = (long) valor;
		
		long cpuLength = (long) getCpuMIPS();
		long nwLength = (long) _edge.getTupleNwLength();

		Tuple tuple = new Tuple(getAppId(), FogUtils.generateTupleId(), Tuple.UP, cpuLength, 1, nwLength, outputSize,
				new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
		tuple.setUserId(getUserId());
		tuple.setTupleType(getTupleType());

		tuple.setDestModuleName(_edge.getDestination());
		tuple.setSrcModuleName(getSensorName());
		Logger.debug(getName(), "Sending tuple with tupleId = " + tuple.getCloudletId());

		int actualTupleId = updateTimings(getSensorName(), tuple.getDestModuleName());
		tuple.setActualTupleId(actualTupleId);

		send(gatewayDeviceId, getLatency(), FogEvents.TUPLE_ARRIVAL, tuple);
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

	public double getCpuMIPS() {
		return cpuMIPS;
	}

	public void setCpuMIPS(double cpuMIPS) {
		this.cpuMIPS = cpuMIPS;
	}

}
