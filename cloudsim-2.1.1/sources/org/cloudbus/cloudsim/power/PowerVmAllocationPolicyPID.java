package org.cloudbus.cloudsim.power;

import java.awt.image.SampleModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.lists.PowerVmList;

public class PowerVmAllocationPolicyPID extends
		PowerVmAllocationPolicySingleThreshold {

	/** The utilization threshold. */
	private double utilizationLowThreshold = 0.5;

	public PowerVmAllocationPolicyPID(
			List<? extends PowerHost> list, double utilizationThreshold,
			double utilizationLowThreshold) {
		super(list, utilizationThreshold);
		setUtilizationLowThreshold(utilizationLowThreshold);
	}

	@Override
	public List<Map<String, Object>> optimizeAllocation(
			List<? extends Vm> vmList) {
		List<Map<String, Object>> migrationMap = new ArrayList<Map<String, Object>>();
		if (vmList.isEmpty()) {
			return migrationMap;
		}
		saveAllocation(vmList);
		List<Vm> vmsToRestore = new ArrayList<Vm>();
		vmsToRestore.addAll(vmList);

		Map<Integer, Host> inMigrationHosts = new HashMap<Integer, Host>();
		List<Vm> vmsToMigrate = new ArrayList<Vm>();
		for (Vm vm : vmList) {
			if (vm.isInMigration()) {
				inMigrationHosts.put(getVmTable().get(vm.getUid()).getId(),
						getVmTable().get(vm.getUid()));
				continue;
			}
			if (inMigrationHosts.containsKey( getVmTable().get(vm.getUid()).getId()) ) {
				continue;
			}
			if (vm.isRecentlyCreated()) {
				continue;
			}

			/*
			if (((PowerHost) vm.getHost()).getMaxUtilization(true) < this
					.getUtilizationThreshold()
					&& ((PowerHost) vm.getHost())
							.getMaxUtilization(false) > this
							.getUtilizationLowThreshold()) {
				continue;
			}*/
			//decide the Migration Emergency Level
			int mustMigrateLevel = 1;
			if ( ((PowerHost) vm.getHost()).getMaxUtilization(true) > (getUtilizationThreshold())){
				double err = ((PowerHost) vm.getHost()).getMaxUtilization(true) - getUtilizationThreshold();
				mustMigrateLevel = (int)( err*10 );
			}
			
			boolean migrateOut = true;
			if ( ((PowerHost) vm.getHost()).getMaxUtilization(false) < getUtilizationThreshold() ){
				double err =  getUtilizationThreshold() - ((PowerHost) vm.getHost()).getMaxUtilization(false);
				mustMigrateLevel = (int)( err*10) ;
				migrateOut = false;
			} 
			// find the smallest vm to migrate out
			Vm vmToMigrate = findVmToMigrate(vmList, vm, mustMigrateLevel,migrateOut);
			if (vmToMigrate != null) {
				vmsToMigrate.add(vmToMigrate);
				inMigrationHosts.put(vmToMigrate.getHost().getId(),
						vmToMigrate.getHost());
				vmToMigrate.getHost().vmDestroy(vmToMigrate);
				vmToMigrate.setLastMigrationTime(CloudSim.clock());
			}
		}
		PowerVmList.sortByCpuUtilization(vmsToMigrate);

		for (PowerHost host : this.<PowerHost> getHostList()) {
			host.reallocateMigratingVms();
		}

		for (Vm vm : vmsToMigrate) {
			PowerHost oldHost = (PowerHost) getVmTable().get(vm.getUid());
			PowerHost allocatedHost = findHostForVm(vm);
			if (allocatedHost != null) {
				allocatedHost.vmCreate(vm);
				Log.printLine("VM #" + vm.getId() + " allocated to host #"
						+ allocatedHost.getId());
				if (allocatedHost.getId() != oldHost.getId()) {
					Map<String, Object> migrate = new HashMap<String, Object>();
					migrate.put("vm", vm);
					migrate.put("host", allocatedHost);
					migrationMap.add(migrate);
				}
			}
		}

		restoreAllocation(vmsToRestore, getHostList());

		return migrationMap;
	}

	// find the smallest vm in the same host
	private Vm findVmToMigrate(List<? extends Vm> vmList, Vm vm, int mustMigrateLevel,boolean migrateOut) {
		Vm smallestVm = vm;

		for (Vm vmTmp : vmList) {
			if (IsOntheSameHost(vmTmp, vm)) {
				if (smallestVm.getRam() > vmTmp.getRam()) {
					smallestVm = vmTmp;
				}
			}
		}
		
		if (migrateOut && smallestVm.getLastMigrationTime()==0)
			return smallestVm;
		
		if ( smallestVm.getRecommendMigrationInterval()/mustMigrateLevel + smallestVm.getLastMigrationTime() < CloudSim.clock())
			return smallestVm;
		else
			return null;
	}

	private boolean IsOntheSameHost(Vm vmTmp, Vm vm) {
		Host vmHost = getVmTable().get(vm.getUid());
		boolean result = false;
		if (vmHost!=null){
			result = (getVmTable().get(vmTmp.getUid()) == vmHost);
		}
		return result;
	}

	public void setUtilizationLowThreshold(double utilizationLowThreshold) {
		this.utilizationLowThreshold = utilizationLowThreshold;
	}
	@Override
	protected double getUtilizationThreshold() {
		double result = super.getUtilizationThreshold();

		return result;
	}

	public double getUtilizationLowThreshold() {
		double result = utilizationLowThreshold;
		return result;
	}

	@Override
	public String getPolicyDesc() {
		String rst = String.format("PID%.2f-%.2f", getUtilizationThreshold(),
				utilizationLowThreshold);
		return rst;
	}
}
