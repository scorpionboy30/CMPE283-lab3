package com.sjsu.vmservices;

import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.vmware.vim25.ComputeResourceConfigSpec;
import com.vmware.vim25.HostConnectSpec;
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
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.PerformanceManager;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;

public class VHostCPUMonitor_DRS2 {

	private static HashMap<Integer, PerfCounterInfo> headerInfo = new HashMap<Integer, PerfCounterInfo>();
	private String username;
	private String password;
	private URL url;
	private List<HostSystem> hostList;
	private List<VirtualMachine> vmList;
	private static VHostCPUMonitor_DRS2 vhostMonitor;
	private ServiceInstance si;
	private Folder rootFolder;
	private HostSystem minUsageHost;

	// List<HostSystem> sortedHostList;

	public VHostCPUMonitor_DRS2(URL url, String username, String password)
			throws RemoteException, MalformedURLException {
		this.url = url;
		this.username = username;
		this.password = password;
		this.hostList = new ArrayList<HostSystem>();
		this.vmList = new ArrayList<VirtualMachine>();
		this.si = new ServiceInstance(url, username, password, true);
		this.rootFolder = si.getRootFolder();

		// Make a list of all the vHosts
		ManagedEntity[] hostsEntity = new InventoryNavigator(rootFolder)
				.searchManagedEntities("HostSystem");
		for (int i = 0; i < hostsEntity.length; i++) {
			hostList.add((HostSystem) hostsEntity[i]);
		}

		// Make a list of all the vms
		ManagedEntity[] vmsEntity = new InventoryNavigator(rootFolder)
				.searchManagedEntities("VirtualMachine");
		for (int i = 0; i < vmsEntity.length; i++) {
			vmList.add((VirtualMachine) vmsEntity[i]);
		}

		PerformanceManager performanceManager = si.getPerformanceManager();
		PerfCounterInfo[] infos = performanceManager.getPerfCounter();
		for (PerfCounterInfo info : infos) {
			headerInfo.put(new Integer(info.getKey()), info);
		}
	}

	// add new vHost
	public void addVHost() {
		try {
			HostConnectSpec hcs = new HostConnectSpec();
			hcs.setHostName("130.65.132.143");
			hcs.setPassword("12!@qwQW");
			hcs.setUserName("root");
			hcs.setSslThumbprint(ConstantUtil.SSL_THUMBPRINT_HOST_143);

			// vcenter ip
			hcs.setManagementIp("130.65.132.140");

			ManagedEntity[] dcs = new InventoryNavigator(si.getRootFolder())
					.searchManagedEntities("Datacenter");

			ComputeResourceConfigSpec ccr = new ComputeResourceConfigSpec();

			// gethostfolder to add hosts
			Task task1 = ((Datacenter) dcs[0]).getHostFolder()
					.addStandaloneHost_Task(hcs, ccr, true);

			if (task1.waitForTask() == Task.SUCCESS) {
				System.out.println("Host Added");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
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
					.getHostPerfMetrics(vHost.getName());

			for (String metricNam : ConstantUtil.METRIC_LIST) {
				HashMap<String, String> metricProps = metricsMap.get(metricNam);

				for (String p : metricProps.keySet()) {
					if (ConstantUtil.PROJECT_PARAMETER_LIST_DRS2.contains(p)) {
						str.append(", " + p + ": " + metricProps.get(p));
						selectedVHost.put(Integer.parseInt(metricProps.get(p)),
								key);
					}
				}
			}
			System.out.println(str);
		}
		return selectedVHost;
	}

	/**
	 * This method will retrieve the List of all Hosts in ascending order based
	 * on the usage
	 * 
	 * @return
	 * @throws Exception
	 */
	private List<HostSystem> getHostAndUsage() throws Exception {
		Map<Integer, String> vHostMap = new TreeMap<Integer, String>(
				vhostMonitor.findVHostsMap());
		List<HostSystem> sortedHostList = new ArrayList<HostSystem>();

		Map<String, HostSystem> vHostList = new HashMap<String, HostSystem>();
		List<HostSystem> vHosts = vhostMonitor.getHostList();

		for (int i = 0; i < vHostMap.keySet().size(); i++) {
			if ((Float.parseFloat(""
					+ (Integer) (vHostMap.keySet().toArray()[0])) / 100) < 30.00) {
				String hostname = vHostMap.get(vHostMap.keySet().toArray()[i]);
				for (HostSystem vHost : vHosts) {
					if (vHost.getName().equalsIgnoreCase(hostname)) {
						vHostList.put(hostname, vHost);
						sortedHostList.add(vHost);
					}
				}
			}
		}

		return sortedHostList;
	}

	/**
	 * This method will retrieve the VM with minimum usage from the given Host
	 * 
	 * @param host
	 * @return
	 * @throws Exception
	 */
	private String getMinUsageVM(HostSystem host) throws Exception {
		Map<String, Integer> selectedVM = new HashMap<String, Integer>();
		// check usage of VMs
		VirtualMachine minUsageVM = null;
		int vmStat = -1;
		if (host.getVms() != null && host.getVms().length > 0) {
			for (VirtualMachine vmObj : host.getVms()) {
				HashMap<String, HashMap<String, String>> metricsMap = vhostMonitor
						.collectVMMetrics(vmObj.getName());
				for (String metricNam : ConstantUtil.METRIC_LIST) {
					HashMap<String, String> metricProps = metricsMap
							.get(metricNam);

					for (String p : metricProps.keySet()) {
						if (ConstantUtil.PROJECT_PARAMETER_LIST_DRS2.contains(p)) {
							if (vmStat == -1) {
								vmStat = Integer.parseInt(metricProps.get(p));
								minUsageVM = vmObj;
							}

							if (vmStat > Integer.parseInt(metricProps.get(p))) {
								vmStat = Integer.parseInt(metricProps.get(p));
								minUsageVM = vmObj;
								System.out.println("Vm name is--->"
										+ vmObj.getName());
								selectedVM.put(vmObj.getName(),
										Integer.parseInt(metricProps.get(p)));
							}
						}
					}// end of For metric
				}
			}// end of VMList for
		}
		// migrate Vms to Balance the load
		System.out.println("vHost is -->" + host.getName() +" VM to be migrated is-->" + minUsageVM.getName());
		return minUsageVM.getName();
	}
	/**
	 * This method will collect the Metrics for Virtual Machines
	 * 
	 * @param vmName
	 * @return
	 * @throws Exception
	 */
	public HashMap<String, HashMap<String, String>> collectVMMetrics(
			String vmName) throws Exception {
		int maxSamples = 3;
		ServiceInstance serviceInstance = new ServiceInstance(url, username,
				password, true);
		InventoryNavigator inventoryNavigator = new InventoryNavigator(
				serviceInstance.getRootFolder());
		VirtualMachine virtualMachine = (VirtualMachine) inventoryNavigator
				.searchManagedEntity("VirtualMachine", vmName);
		System.out.println("Collecting metrics for VM--->" + vmName);
		if (virtualMachine == null) {
			throw new Exception("Virtual Machine '" + vmName + "' not found.");
		}

		PerformanceManager performanceManager = serviceInstance
				.getPerformanceManager();

		PerfQuerySpec perfQuerySpec = new PerfQuerySpec();
		perfQuerySpec.setEntity(virtualMachine.getMOR());
		perfQuerySpec.setMaxSample(new Integer(maxSamples));
		perfQuerySpec.setFormat("normal");

		PerfProviderSummary pps = performanceManager
				.queryPerfProviderSummary(virtualMachine);
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
	/**
	 * This method will retrieve the Host Performance Metrics
	 * 
	 * @param vHostmName
	 * @return
	 * @throws Exception
	 */
	protected HashMap<String, HashMap<String, String>> getHostPerfMetrics(
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
	/**
	 * This method will generate the Performance Results based on the performance metrics
	 * @param pValues
	 * @return
	 */
	private HashMap<String, HashMap<String, String>> generatePerformanceResult(PerfEntityMetricBase[] pValues) {
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

	/**
	 * This method will migrate the specified VM to the given Host
	 * 
	 * @param vmName
	 * @param newHostName
	 */
	public void migrateVM(String vmName, String newHostName) {
		boolean flag = true;

		try {
			Folder rootFolder = si.getRootFolder();

			VirtualMachine vm = (VirtualMachine) new InventoryNavigator(
					rootFolder).searchManagedEntity("VirtualMachine", vmName);

			HostSystem newHost = (HostSystem) new InventoryNavigator(rootFolder)
					.searchManagedEntity("HostSystem", newHostName);
			newHost.getName();
			ComputeResource cr = (ComputeResource) newHost.getParent();

			String[] checks = new String[] { "cpu", "software" };
			HostVMotionCompatibility[] vmcs = si.queryVMotionCompatibility(vm,
					new HostSystem[] { newHost }, checks);

			String[] comps = vmcs[0].getCompatibility();
			if (checks.length != comps.length) {
				System.out.println("CPU/software NOT compatible. Exit.");
				flag = false;
			}

			if (flag) {
				Task task = null;
				if (VirtualMachinePowerState.poweredOn.equals(vm.getRuntime()
						.getPowerState())) {
					// live migration
					task = vm.migrateVM_Task(cr.getResourcePool(), newHost,
							VirtualMachineMovePriority.highPriority,
							VirtualMachinePowerState.poweredOn);
				} else {
					// cold migration
					task = vm.migrateVM_Task(cr.getResourcePool(), newHost,
							VirtualMachineMovePriority.highPriority,
							VirtualMachinePowerState.poweredOff);
				}

				if (task.waitForTask() == Task.SUCCESS) {
					System.out.println("VM Migrated Successfully..!");
				}
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @return the headerInfo
	 */
	public static HashMap<Integer, PerfCounterInfo> getHeaderInfo() {
		return headerInfo;
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

	/**
	 * @return the vmList
	 */
	public List<VirtualMachine> getVmList() {
		return vmList;
	}

	/**
	 * @param vmList
	 *            the vmList to set
	 */
	public void setVmList(List<VirtualMachine> vmList) {
		this.vmList = vmList;
	}

	/**
	 * @return the minUsageHost
	 */
	public HostSystem getMinUsageHost() {
		return minUsageHost;
	}

	/**
	 * @param minUsageHost
	 *            the minUsageHost to set
	 */
	public void setMinUsageHost(HostSystem minUsageHost) {
		this.minUsageHost = minUsageHost;
	}

	public static void main(String[] args) throws Exception {
		vhostMonitor = new VHostCPUMonitor_DRS2(new URL(ConstantUtil.URL),
				ConstantUtil.ADMIN_USER_NAME, ConstantUtil.ADMIN_PASSWORD);

		// add a new Host to the system
		vhostMonitor.addVHost();

		// find host with max usage
		// this list contains host with minimum usage at 1st place
		List<HostSystem> hostUsageList = vhostMonitor.getHostAndUsage();

		// name of the new Host
		String newHost = "130.65.132.143";

		if (hostUsageList != null && !hostUsageList.isEmpty()) {
			int j = 0;
			for (int i = hostUsageList.size() - 1; i > 0; i--) {
				if (j > 0) {
					newHost = hostUsageList.get(0).getName();
				}
				HostSystem hostName = hostUsageList.get(i);
				// check if the host has only one VM
				if (hostName.getVms().length <= 1) {
					System.out
							.println("This host has only one VM. So migration is not performed.");
				} else {
					// get the name of the VM to be migrated to the new Host
					String vmName = vhostMonitor.getMinUsageVM(hostName);
					if (vmName != null) {
						// migrate the VM to new Host
						vhostMonitor.migrateVM(vmName, newHost);
						j++;
						if (j > 1) {
							break;
						}
					}//end of if vmName
				}
			}//end of for
		}//end of if hostUsageList

	}

}
