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

import com.vmware.vim25.PerfCounterInfo;
import com.vmware.vim25.PerfEntityMetric;
import com.vmware.vim25.PerfEntityMetricBase;
import com.vmware.vim25.PerfMetricIntSeries;
import com.vmware.vim25.PerfMetricSeries;
import com.vmware.vim25.PerfMetricSeriesCSV;
import com.vmware.vim25.PerfProviderSummary;
import com.vmware.vim25.PerfQuerySpec;
import com.vmware.vim25.VirtualMachineConfigSpec;
import com.vmware.vim25.VirtualMachineFileInfo;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.PerformanceManager;
import com.vmware.vim25.mo.ResourcePool;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;

public class VHostCPUMonitor_DRS1 {
	private static HashMap<Integer, PerfCounterInfo> headerInfo = new HashMap<Integer, PerfCounterInfo>();
	private String username;
	private String password;
	private URL url;
	private List<HostSystem> hostList;
	private List<VirtualMachine> vmList;
	private static VHostCPUMonitor_DRS1 vhostMonitor;
	private ServiceInstance si;
	private Folder rootFolder;

	public VHostCPUMonitor_DRS1(URL url, String username, String password)
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

	// function adds new VM.
	private void addVM() throws Exception {
		Map<String, Integer> vHostMap = vhostMonitor.findVHostsMap();
		int vHostCPU_141 = vHostMap.get("130.65.132.141");
		int vHostCPU_142 = vHostMap.get("130.65.132.142");
		ResourcePool rp = null;
		String targetHostName;

		String dcName = "Datacenter";
		Datacenter dc = (Datacenter) new InventoryNavigator(
				vhostMonitor.rootFolder).searchManagedEntity("Datacenter",
				dcName);

		if (vHostCPU_141 < vHostCPU_142) {
			targetHostName = "130.65.132.141";
			rp = (ResourcePool) new InventoryNavigator(dc)
					.searchManagedEntities("ResourcePool")[0];

		} else {
			targetHostName = "130.65.132.142";
			rp = (ResourcePool) new InventoryNavigator(dc)
					.searchManagedEntities("ResourcePool")[1];
		}

		String vmName = "newlyAddedVM";
		long memorySizeMB = 512;
		int cupCount = 1;
		String guestOsId = "ubuntuGuest";
		String datastoreName = "nfs1team02";

		// create vm config spec
		VirtualMachineConfigSpec vmSpec = new VirtualMachineConfigSpec();
		vmSpec.setName(vmName);
		vmSpec.setAnnotation("VirtualMachine Annotation");
		vmSpec.setMemoryMB(memorySizeMB);
		vmSpec.setNumCPUs(cupCount);
		vmSpec.setGuestId(guestOsId);
		
		// create vm file info for the vmx file
	    VirtualMachineFileInfo vmfi = new VirtualMachineFileInfo();
	    vmfi.setVmPathName("["+ datastoreName +"]");
	    vmSpec.setFiles(vmfi);

		Folder vmFolder = dc.getVmFolder();
		Task task = vmFolder.createVM_Task(vmSpec, rp, null);

		
/*		VirtualMachineCloneSpec cloneSpec = 
			      new VirtualMachineCloneSpec();
		VirtualMachineRelocateSpec vmRelocate = new VirtualMachineRelocateSpec();
		vmRelocate.setHost(targetHost);
			    cloneSpec.setLocation(vmRelocate);
			    cloneSpec.setPowerOn(false);
			    cloneSpec.setTemplate(false);

			    Task task = vm.cloneVM_Task((Folder) vm.getParent(), 
			        cloneName, cloneSpec);*/
		
		// code to add new VM
		String result = task.waitForMe();
		if (result == Task.SUCCESS) {
			System.out.println("VM Created Sucessfully");
		} else {
			System.out.println("VM could not be created. ");
		}
	}

	// Check the VHost CPU usage at the very moment when we need to add a new VM
	private Map<String, Integer> findVHostsMap() throws Exception {
		Map<String, Integer> selectedVHost = new HashMap<String, Integer>();

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
					if (ConstantUtil.PROJECT_PARAMETER_LIST_DRS1.contains(p)) {
						// vHostCPU.add(Integer.parseInt(metricProps.get(p)));
						str.append(", " + p + ": " + metricProps.get(p));
						selectedVHost.put(key,
								Integer.parseInt(metricProps.get(p)));
					}
				}
			}
			System.out.println(str);
		}
		return selectedVHost;
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

	public static void main(String[] args) throws Exception {
		vhostMonitor = new VHostCPUMonitor_DRS1(new URL(ConstantUtil.URL),
				ConstantUtil.ADMIN_USER_NAME, ConstantUtil.ADMIN_PASSWORD);

		// add new VM thru DRS1
		vhostMonitor.addVM();
	}
}
