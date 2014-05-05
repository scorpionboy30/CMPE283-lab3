package com.sjsu.vmservices;

import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.vmware.vim25.HostVMotionCompatibility;
import com.vmware.vim25.PerfCounterInfo;
import com.vmware.vim25.PerfEntityMetric;
import com.vmware.vim25.PerfEntityMetricBase;
import com.vmware.vim25.PerfMetricIntSeries;
import com.vmware.vim25.PerfMetricSeries;
import com.vmware.vim25.PerfMetricSeriesCSV;
import com.vmware.vim25.PerfProviderSummary;
import com.vmware.vim25.PerfQuerySpec;
import com.vmware.vim25.VirtualMachineMovePriority;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.mo.ComputeResource;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.PerformanceManager;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;

public class VHostPowerMonitor_DPM {
	// get cpu_usage values for all vHosts
	private static HashMap<Integer, PerfCounterInfo> headerInfo = new HashMap<Integer, PerfCounterInfo>();
	private String username;
	private String password;
	private URL url;
	private List<HostSystem> hostList;
	private static VHostPowerMonitor_DPM vhostMonitor;
	private ServiceInstance si;
	private Folder rootFolder;
	private static boolean shouldStart = false;

	public VHostPowerMonitor_DPM(URL url, String username, String password)
			throws RemoteException, MalformedURLException {
		this.url = url;
		this.username = username;
		this.password = password;
		
		this.si = new ServiceInstance(url, username, password, true);
		this.rootFolder = si.getRootFolder();

		PerformanceManager performanceManager = si.getPerformanceManager();
		PerfCounterInfo[] infos = performanceManager.getPerfCounter();
		for (PerfCounterInfo info : infos) {
			headerInfo.put(new Integer(info.getKey()), info);
		}
	}

	protected HashMap<String, HashMap<String, String>> getPerformanceMetrics(
			String vHostmName) throws Exception {

		ServiceInstance serviceInstance = new ServiceInstance(url, username,
				password, true);
		InventoryNavigator inventoryNavigator = new InventoryNavigator(
				serviceInstance.getRootFolder());
		HostSystem vHost = (HostSystem) inventoryNavigator.searchManagedEntity(
				"HostSystem", vHostmName);
		if (vHost == null) {
			throw new Exception("vHost '" + vHostmName + "' not found.");
		}

		PerformanceManager performanceManager = serviceInstance
				.getPerformanceManager();

		PerfQuerySpec perfQuerySpec = new PerfQuerySpec();
		perfQuerySpec.setEntity(vHost.getMOR());
		perfQuerySpec.setMaxSample(new Integer(3));
		perfQuerySpec.setFormat("normal");

		PerfProviderSummary pps = performanceManager
				.queryPerfProviderSummary(vHost);
		perfQuerySpec
				.setIntervalId(new Integer(pps.getRefreshRate().intValue()));

		PerfEntityMetricBase[] pValues = performanceManager
				.queryPerf(new PerfQuerySpec[] { perfQuerySpec });

		if (pValues != null) {
			return generatePerformanceResult(pValues);
		} else {
			throw new Exception("No values found!");
		}
	}

	private HashMap<String, HashMap<String, String>> generatePerformanceResult(
			PerfEntityMetricBase[] pValues) {
		HashMap<String, HashMap<String, String>> propertyGroups = new HashMap<String, HashMap<String, String>>();
		for (PerfEntityMetricBase p : pValues) {
			PerfEntityMetric pem = (PerfEntityMetric) p;
			PerfMetricSeries[] pms = pem.getValue();
			for (PerfMetricSeries pm : pms) {
				int counterId = pm.getId().getCounterId();
				PerfCounterInfo info = headerInfo.get(new Integer(counterId));

				String value = "";

				if (pm instanceof PerfMetricIntSeries) {
					PerfMetricIntSeries series = (PerfMetricIntSeries) pm;
					long[] values = series.getValue();
					long result = 0;
					for (long v : values) {
						result += v;
					}
					result = (long) (result / values.length);
					value = String.valueOf(result);
				} else if (pm instanceof PerfMetricSeriesCSV) {
					PerfMetricSeriesCSV seriesCsv = (PerfMetricSeriesCSV) pm;
					value = seriesCsv.getValue() + " in "
							+ info.getUnitInfo().getLabel();
				}

				HashMap<String, String> properties;
				if (propertyGroups.containsKey(info.getGroupInfo().getKey())) {
					properties = propertyGroups.get(info.getGroupInfo()
							.getKey());
				} else {
					properties = new HashMap<String, String>();
					propertyGroups
							.put(info.getGroupInfo().getKey(), properties);
				}

				String propName = String.format("%s_%s", info.getGroupInfo()
						.getKey(), info.getNameInfo().getKey());
				properties.put(propName, value);
			}
		}
		return propertyGroups;
	}

	// Check the VHost CPU usage at the very moment when we need to add a new VM
	private Map<Integer, String> findVHostsMap() throws Exception {
		Map<Integer, String> selectedVHost = new HashMap<Integer, String>();

		// determine the value of the cpu usage for every vHost.
		for (HostSystem vHost : vhostMonitor.getHostList()) {
			Date date = new Date(System.currentTimeMillis());
			SimpleDateFormat format = new SimpleDateFormat(
					"yyyy/MM/dd hh:mm:ss");
			StringBuffer str = new StringBuffer();
			str.append("timestamp: " + format.format(date));
			str.append(" ,vHostName: " + vHost.getName());
			String key = vHost.getName();
			HashMap<String, HashMap<String, String>> metricsMap = vhostMonitor
					.getPerformanceMetrics(vHost.getName());

			for (String metricNam : ConstantUtil.METRIC_LIST) {
				HashMap<String, String> metricProps = metricsMap.get(metricNam);

				for (String p : metricProps.keySet()) {
					if (ConstantUtil.PROJECT_PARAMETER_LIST_DRS2.contains(p)) {
						str.append(", " + p + ": " + metricProps.get(p));
						selectedVHost.put(Integer.parseInt(metricProps.get(p)),key);
						if((Float.parseFloat(metricProps.get(p))/100) <  30)
						{
							shouldStart = true;
						}
					}
				}
			}
			System.out.println(str);
		}
		return selectedVHost;
	}

	// sort vHosts from lower to higher usage
	private Map<String, HostSystem> getHostAsPerUsage() throws Exception {
		Map<Integer, String> vHostMap = new TreeMap<Integer, String>(
				vhostMonitor.findVHostsMap());

		Map<String, HostSystem> vHostList = new LinkedHashMap<String, HostSystem>();
		List<HostSystem> vHosts = vhostMonitor.getHostList();

		for (int i = 0; i < vHostMap.keySet().size(); i++) {
			String hostname = vHostMap.get(vHostMap.keySet().toArray()[i]);
			for (HostSystem vHost : vHosts) {
				if (vHost.getName().equalsIgnoreCase(hostname)) {
					vHostList.put(hostname, vHost);
				}
			}
			
		}
		return vHostList;
	}

	// migrate VMs from the vHost with usage less than 30% and then destroy the
	// vHost
	private void startConsolidation() throws Exception {
		// get list of vHosts with their cpu_usage
		Map<String, HostSystem> vHostList = vhostMonitor.getHostAsPerUsage();
		boolean valueSet = true;
		if (vHostList != null && !vHostList.isEmpty()) {
			// identify vHost whose VMs need to be migrated
			if(shouldStart)
			{
			HostSystem leastUsageHost = vHostList.get((vHostList.keySet()
					.toArray()[0]));
			VirtualMachine[] vms = leastUsageHost.getVms();

			// now start migrating the above VMs to second host
			for (int i = 0; i < vms.length; i++) {
				VirtualMachine vm = vms[i];
				HostSystem newHost = vHostList.get((vHostList.keySet()
						.toArray()[1]));

				System.out.println(newHost.getName());
				ComputeResource cr = (ComputeResource) newHost.getParent();

				String[] checks = new String[] { "cpu", "software" };
				HostVMotionCompatibility[] vmcs = si.queryVMotionCompatibility(
						vm, new HostSystem[] { newHost }, checks);

				String[] comps = vmcs[0].getCompatibility();
				if (checks.length != comps.length) {
					System.out.println("CPU/software NOT compatible. Exit.");
					valueSet = false;
				}

				if (valueSet) {
					Task task = null;
					if (VirtualMachinePowerState.poweredOn.equals(vm
							.getRuntime().getPowerState())) {
						// live migration
						System.out
								.println("Live Migration of: " + vm.getName());
						task = vm.migrateVM_Task(cr.getResourcePool(), newHost,
								VirtualMachineMovePriority.highPriority,
								VirtualMachinePowerState.poweredOn);
					} else {
						// cold migration
						System.out
								.println("Cold Migration of: " + vm.getName());
						task = vm.migrateVM_Task(cr.getResourcePool(), newHost,
								VirtualMachineMovePriority.highPriority,
								VirtualMachinePowerState.poweredOff);
					}

					if (task.waitForTask() == Task.SUCCESS) {
						System.out.println("VM Migrated Successfully..!");

					}
				}
			}
			// delete the leastUsageHost
			ComputeResource cr = (ComputeResource) vHostList.get(
					(vHostList.keySet().toArray()[0])).getParent();
			System.out.println("Entering the delete phase of host "
					+ vHostList.get((vHostList.keySet().toArray()[0]))
							.getName());
			Task task = cr.destroy_Task();
			if (task.waitForTask() == Task.SUCCESS) {
				System.out.println("Deleted ");
			}
			}
			else
			{
				System.out.println("Usage for all vHosts above 30%. DPM not necessary");
			}
		} else {
			System.out.println("No need to perform DPM");
		}
	}

	/**
	 * @return the hostList
	 */
	public List<HostSystem> getHostList() {
		return hostList;
	}

	/**
	 * @param hostList
	 *            the hostList to set
	 */
	public void setHostList(List<HostSystem> hostList) {
		this.hostList = hostList;
	}

	public static void main(String[] args) throws Exception {
		vhostMonitor = new VHostPowerMonitor_DPM(new URL(ConstantUtil.URL),
				ConstantUtil.ADMIN_USER_NAME, ConstantUtil.ADMIN_PASSWORD);

		// consolidate VMs and vHosts thru DPM
		while (true) {
			// Make a list of all the vHosts in the vCenter
			ManagedEntity[] hostsEntity = new InventoryNavigator(vhostMonitor.rootFolder).searchManagedEntities("HostSystem");
			vhostMonitor.hostList = new ArrayList<HostSystem>();
			vhostMonitor.shouldStart = false;
			for (int i = 0; i < hostsEntity.length; i++) {
				vhostMonitor.hostList.add((HostSystem) hostsEntity[i]);
			}

			if (hostsEntity.length > 1) {
				vhostMonitor.startConsolidation();
			} else {
				break;
			}
		}
	}

}
