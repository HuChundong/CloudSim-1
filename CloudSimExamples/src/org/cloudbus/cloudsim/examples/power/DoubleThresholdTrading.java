package org.cloudbus.cloudsim.examples.power;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerDynamicWorkloadFixedTime;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.UtilizationModelStochastic;
import org.cloudbus.cloudsim.UtilizationModelWorkHour;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.examples.UtilizationModelUniform;
import org.cloudbus.cloudsim.network.NetworkConfig;
import org.cloudbus.cloudsim.power.PowerDatacenter;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.PowerPe;
import org.cloudbus.cloudsim.power.PowerVmAllocationPolicyDoubleThreshold;
import org.cloudbus.cloudsim.power.PowerVmAllocationPolicyTrading;
import org.cloudbus.cloudsim.power.models.PowerModelLinear;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

public class DoubleThresholdTrading {

	//parameters
	public  boolean useTrading = false;
	public  int groupNum = 20;
	public  int dtGroupNum = 20;
	public  int hostPerGroup = 10;
	public  boolean workHourLoad = true;
	public  int roughIndex = 3;
	public  boolean isNetworkAware = false;
	public  boolean isTradeWithinGrps = true;
	public boolean disableMainLog = true;
	public boolean generateNewNetCfg = true;
	
	protected  int hostsNumber = hostPerGroup * groupNum;//4 groups;
	protected  int vmsNumber = 2 * hostPerGroup * groupNum;// 6vms per group;	
	protected  final int simLength = 120 * 30; //one hour
	protected  double utilizationThreshold = 0.7;
	private  double utilizationLowThreshold = 0.4;
	protected  double cloudletsNumber = vmsNumber;	
	protected  boolean useSA = false;	
	protected  boolean useAverageUtilization = true;
	private  boolean doubleDC = false;
	
	/** The cloudlet list. */
	protected  List<Cloudlet> cloudletList;
	/** The vm list. */
	protected  List<Vm> vmList;
	//protected  UtilizationModelStochastic utilizationModelWorkHour;
	protected  UtilizationModelUniform utilizationModelUniform;
	protected  UtilizationModelStochastic utilizationModelStochastic;
	
	private long startSimTime = 0;
	static private NetworkConfig networkConfig = null;
	
	public static  void main(String[] args) throws IOException {
		DoubleThresholdTrading sim = new DoubleThresholdTrading();
		sim.startSim();
	}

	public  void startSim() throws IOException {
		hostsNumber = hostPerGroup * groupNum;//4 groups;
		dtGroupNum = groupNum;
		vmsNumber = 2 * hostPerGroup * groupNum;// 6vms per group;			
		
		Log.setOutputFile("C:\\Users\\n7682905\\sim.txt");
		Log.setDisableMainLog(disableMainLog);
		
		try {
			// First step: Initialize the CloudSim package. It should be called
			// before creating any entities. We can't run this example without
			// initializing CloudSim first. We will get run-time exception
			// error.
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace GridSim events

			// Initialize the CloudSim library
			CloudSim.init(num_user, calendar, trace_flag);

			// Second step: Create Datacenters
			// Datacenters are the resource providers in CloudSim. We need at
			// list one of them to run a CloudSim simulation
			PowerDatacenter datacenter0 = createDatacenter("Datacenter_0",0);
			PowerDatacenter datacenter1 = null;
			if (doubleDC)
			datacenter1 = createDatacenter("Datacenter_1",datacenter0.getHostList().size());
			
			// Third step: Create Broker
			DatacenterBroker broker = createBroker();
			int brokerId = broker.getId();

			// Fourth step: Create one virtual machine
			vmList = createVms(brokerId);
			
			if (generateNewNetCfg)
				networkConfig = new NetworkConfig(datacenter0.getHostList().size(), vmList.size());
			datacenter0.setNetworkConfig(networkConfig);
			startSimTime  = (new Date()).getTime();
			// submit vm list to the broker
			broker.submitVmList(vmList);

			// Fifth step: Create one cloudlet
			cloudletList = createCloudletList(brokerId);

			// submit cloudlet list to the broker
			broker.submitCloudletList(cloudletList);

			// Sixth step: Starts the simulation
			double lastClock = CloudSim.startSimulation();

			// Final step: Print results when simulation is over
			List<Cloudlet> newList = broker.getCloudletReceivedList();
			Log.printLine("Received " + newList.size() + " cloudlets");

			CloudSim.stopSimulation();

			printCloudletList(newList);

		    printAllocationStats(datacenter0, lastClock);
		    if (doubleDC)
		    printAllocationStats(datacenter1, lastClock);
		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}
		
		Log.printLine("DoubleThreshold finished!");
		System.out.println("DoubleThreshold finished!");
	}

	private  void printAllocationStats(PowerDatacenter datacenter,
			double lastClock) throws Exception {
		int totalTotalRequested = 0;
		int totalTotalAllocated = 0;
		ArrayList<Double> sla = new ArrayList<Double>();
		int numberOfAllocations = 0;
		for (Entry<String, List<List<Double>>> entry : datacenter.getUnderAllocatedMips().entrySet()) {
		    List<List<Double>> underAllocatedMips = entry.getValue();
		    double totalRequested = 0;
		    double totalAllocated = 0;
		    for (List<Double> mips : underAllocatedMips) {
		    	if (mips.get(0) != 0) {
		    		numberOfAllocations++;
		    		totalRequested += mips.get(0);
		    		totalAllocated += mips.get(1);
		    		double _sla = (mips.get(0) - mips.get(1)) / mips.get(0) * 100;
		    		if (_sla > 0) {
		    			sla.add(_sla);
		    		}
		    	}
			}
		    totalTotalRequested += totalRequested;
		    totalTotalAllocated += totalAllocated;
		}

		double averageSla = 0;
		if (sla.size() > 0) {
		    double totalSla = 0;
		    for (Double _sla : sla) {
		    	totalSla += _sla;
			}
		    averageSla = totalSla / sla.size();
		}

		Log.printLine();
		Log.printLine(String.format("Total simulation time: %.2f sec", lastClock));
		Log.printLine(String.format("Energy consumption: %.4f kWh", datacenter.getPower() / (3600 * 1000)));
		Log.printLine(String.format("networkCost: %.4f", datacenter.getNetworkCost()));
		Log.printLine(String.format("Number of VM migrations: %d", datacenter.getMigrationCount()));
		Log.printLine(String.format("Number of SLA violations: %d", sla.size()));
		Log.printLine(String.format("SLA violation percentage: %.2f%%", (double) sla.size() * 100 / numberOfAllocations));
		Log.printLine(String.format("Average SLA violation: %.2f%%", averageSla));
		Log.printLine(String.format("Turn On times: %d", datacenter.getTurnOnTimes()));
		Log.printLine(String.format("Turn Off times: %d", datacenter.getTurnOffTimes()));
		Log.printLine(String.format("satisfaction rate: %.2f%%", totalTotalAllocated*1.0/totalTotalRequested*100));
		Log.printLine();
		
		String memViolation = printMemViolation(datacenter) + printSimParams();
		
		Log.printLineToInfoFile(datacenter.getVmAllocationPolicy().getPolicyDesc(),simLength, 
				datacenter.getMigrationCount(),
				(double) sla.size() * 100 / numberOfAllocations,
				averageSla,
				datacenter.getPower() / (3600 * 1000),
				datacenter.getNetworkCost(),memViolation);
		utilizationModelStochastic.saveHistory("c:\\users\\n7682905\\simWorkload.txt");
		
		
	}
	
	protected String printSimParams(){
		String s = ",params:";
		s += ",useTrading=" + useTrading;
		s += ",groupNum=" + groupNum ;
		s += ",dtGroupNum=" + dtGroupNum ;
		s += ",hostPerGroup=" + hostPerGroup ;
		s += ",workHourLoad=" + workHourLoad;
		s += ",roughIndex=" + roughIndex;
		s += ",isNetworkAware=" + isNetworkAware;
		s += ",isTradeWithinGrps=" + isTradeWithinGrps;
		s += ",runTime=" + ((new Date()).getTime() - startSimTime);
		return s;
	}
	
	protected  String printMemViolation(PowerDatacenter datacenter){
		 int totalTotalRequested = 0;
		    int totalTotalAllocated = 0;
		    ArrayList<Double> sla = new ArrayList<Double>();
		    int numberOfAllocations = 0;
			for (Entry<String, List<List<Double>>> entry : datacenter.getUnderAllocatedMem().entrySet()) {
			    List<List<Double>> underAllocatedMem = entry.getValue();
			    double totalRequested = 0;
			    double totalAllocated = 0;
			    for (List<Double> mips : underAllocatedMem) {
			    	if (mips.get(0) != 0) {
			    		numberOfAllocations++;
			    		totalRequested += mips.get(0);
			    		totalAllocated += mips.get(1);
			    		double _sla = (mips.get(0) - mips.get(1)) / mips.get(0) * 100;
			    		if (_sla > 0.01) {
			    			sla.add(_sla);
			    		}
			    	}
				}
			    totalTotalRequested += totalRequested;
			    totalTotalAllocated += totalAllocated;
			}

			double averageSla = 0;
			if (sla.size() > 0) {
			    double totalSla = 0;
			    for (Double _sla : sla) {
			    	totalSla += _sla;
				}
			    averageSla = totalSla / sla.size();
			}

			printLine("");
			Log.printLine(String.format("Number of SLA violations: %d", sla.size()));
			Log.printLine(String.format("SLA violation percentage: %.2f%%", (double) sla.size() * 100 / numberOfAllocations));
			Log.printLine(String.format("Average SLA violation: %.2f%%", averageSla));			
			Log.printLine(String.format("satisfaction rate: %.2f%%", totalTotalAllocated*1.0/totalTotalRequested*100));
			printLine("");
			return String.format(" %.4f,%.4f", (double) sla.size() * 100 / numberOfAllocations,averageSla );
	}
	
	private  void printLine(String s){
		System.out.println(s);
	}
	
	protected  PowerDatacenter createDatacenter(String name, int startingHostNumber) throws Exception {
		// Here are the steps needed to create a PowerDatacenter:
		// 1. We need to create an object of HostList2 to store
		// our machine
		List<PowerHost> hostList = new ArrayList<PowerHost>();

		double maxPower = 250; // 250W
		double PowerPercent = 0.7; // 70%

		//int[] mips = { 1000,1000,1000,1000,1000, 2000,2000,2000,2000,2000, 3000,3000,3000,3000,3000 };
		int[] mipsType = {1000,2000,3000,4000,5000};
		int[] mips = new int[groupNum * hostPerGroup];
		for (int i=0;i<groupNum * hostPerGroup;i++){
			int j = i / hostPerGroup;
			int mip = mipsType[j%mipsType.length];
			mips[i] = mip;
		}
		//int[] mips = { 3000,3000,3000,2000,2000,2000, 1000,1000,1000, 1000,1000,1000 };
		//int[] mips = { 1000,200,3000, 1000,2000,3000, 1000,2000,3000,1000,2000,3000 };
		int ram = 3000; // host memory (MB)
		long storage = 1000000; // host storage
		int bw = 100000;

		for (int i = startingHostNumber; i < hostsNumber+startingHostNumber; i++) {
			// 2. A Machine contains one or more PEs or CPUs/Cores.
			// In this example, it will have only one core.
			// 3. Create PEs and add these into an object of PowerPeList.
			List<PowerPe> peList = new ArrayList<PowerPe>();
			peList.add(new PowerPe(0, new PeProvisionerSimple(mips[i % mips.length]), new PowerModelLinear(maxPower+ 100 *  (mips[i % mips.length]-1000)/1000 + i, PowerPercent))); // need to store PowerPe id and MIPS Rating

			// 4. Create PowerHost with its id and list of PEs and add them to the list of machines
			hostList.add(
				new PowerHost(
					i,
					new RamProvisionerSimple(mips[i]),//new RamProvisionerSimple(ram),
					new BwProvisionerSimple(bw),
					storage,
					peList,
					new VmSchedulerTimeShared(peList)
				)
			); // This is our machine
		}

		// 5. Create a DatacenterCharacteristics object that stores the
		// properties of a Grid resource: architecture, OS, list of
		// Machines, allocation policy: time- or space-shared, time zone
		// and its price (G$/PowerPe time unit).
		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
										// resource
		double costPerBw = 0.0; // the cost of using bw in this resource

		DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
				arch, os, vmm, hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw);

		// 6. Finally, we need to create a PowerDatacenter object.
		PowerDatacenter powerDatacenter = null;
		VmAllocationPolicy policy =null;
		if (!useTrading)
			policy = new PowerVmAllocationPolicyDoubleThreshold(hostList, utilizationThreshold,utilizationLowThreshold, dtGroupNum);
		else
			policy =new PowerVmAllocationPolicyTrading(hostList, utilizationThreshold,utilizationLowThreshold, groupNum);
		try {
			powerDatacenter = new PowerDatacenter(
					name,
					characteristics,
					policy,
					new LinkedList<Storage>(),
					5.0);
			if (policy instanceof PowerVmAllocationPolicyTrading){
				((PowerVmAllocationPolicyTrading) policy).setPowerDatacenter(powerDatacenter);
				((PowerVmAllocationPolicyTrading) policy).setNetworkAware(isNetworkAware);
				((PowerVmAllocationPolicyTrading) policy).setTradeWithinGrps(isTradeWithinGrps);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return powerDatacenter;
	}
	
protected  List<Cloudlet> createCloudletList(int brokerId) {
		
		if (workHourLoad)
			utilizationModelStochastic = new UtilizationModelWorkHour(roughIndex);
		else
			utilizationModelStochastic = new UtilizationModelStochastic(roughIndex);
		utilizationModelUniform = new UtilizationModelUniform() ;
		
		List<Cloudlet> list = new ArrayList<Cloudlet>();

		long length = 150000; // 10 min on 250 MIPS
		int pesNumber = 1;
		long fileSize = 300;
		long outputSize = 300;

		for (int i = 0; i < cloudletsNumber; i++) {
			Cloudlet cloudlet = null;
			if (i==0){
				cloudlet = new Cloudlet(i, length, pesNumber, fileSize, outputSize, utilizationModelStochastic, new UtilizationModelStochastic(roughIndex), new UtilizationModelStochastic(roughIndex));
			}else{
				if (workHourLoad)
					cloudlet = new Cloudlet(i, length, pesNumber, fileSize, outputSize, new UtilizationModelWorkHour(roughIndex), new UtilizationModelWorkHour(roughIndex), new UtilizationModelStochastic(roughIndex));
				else
					cloudlet = new Cloudlet(i, length, pesNumber, fileSize, outputSize, new UtilizationModelStochastic(roughIndex), new UtilizationModelStochastic(roughIndex), new UtilizationModelStochastic(roughIndex));
			}
			cloudlet.setUserId(brokerId);
			cloudlet.setVmId(i);
			cloudlet.setCloudletDuration(simLength); // 20 minutes
			list.add(cloudlet);
		}

		return list;
	}

	/**
	 * Creates the vms.
	 *
	 * @param brokerId the broker id
	 *
	 * @return the list< vm>
	 */
	protected  List<Vm> createVms(int brokerId) {
		List<Vm> vms = new ArrayList<Vm>();

		// VM description
		int[] mips = { 250, 500, 750, 1000 }; // MIPSRating
		int pesNumber = 1; // number of cpus
		int[] rams =    { 250, 500, 750, 1000 };//{128, 256, 374, 512 }; // vm memory (MB)
		long bw = 250; // bandwidth
		long size = 2500; // image size (MB)
		String vmm = "Xen"; // VMM name

		for (int i = 0; i < vmsNumber; i++) {
			vms.add(
				new Vm(i, brokerId, mips[i % mips.length], pesNumber, rams[i % mips.length], bw, size, vmm, new CloudletSchedulerDynamicWorkloadFixedTime(mips[i % mips.length], pesNumber,rams[i % mips.length]))
			);
		}

		return vms;
	}
	
	protected  DatacenterBroker createBroker() {
		DatacenterBroker broker = null;
		try {
			broker = new DatacenterBroker("Broker");
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return broker;
	}

	protected  void printCloudletList(List<Cloudlet> list) {
		int size = list.size();
		Cloudlet cloudlet;

		String indent = "\t";
		Log.printLine();
		Log.printLine("========== OUTPUT ==========");
		Log.printLine("Cloudlet ID" + indent + "STATUS" + indent
				+ "Resource ID" + indent + "VM ID" + indent + "Time" + indent
				+ "Start Time" + indent + "Finish Time");

		DecimalFormat dft = new DecimalFormat("###.##");
		for (int i = 0; i < size; i++) {
			cloudlet = list.get(i);
			Log.print(indent + cloudlet.getCloudletId());

			if (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS) {
				Log.printLine(indent + "SUCCESS"
					+ indent + indent + cloudlet.getResourceId()
					+ indent + cloudlet.getVmId()
					+ indent + dft.format(cloudlet.getActualCPUTime())
					+ indent + dft.format(cloudlet.getExecStartTime())
					+ indent + indent + dft.format(cloudlet.getFinishTime())
				);
			}
		}
	}
}
