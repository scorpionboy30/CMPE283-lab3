package com.sjsu.vmservices;

import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;

import com.vmware.vim25.PerfCounterInfo;
import com.vmware.vim25.PerfEntityMetric;
import com.vmware.vim25.PerfEntityMetricBase;
import com.vmware.vim25.PerfMetricIntSeries;
import com.vmware.vim25.PerfMetricSeries;
import com.vmware.vim25.PerfMetricSeriesCSV;
import com.vmware.vim25.PerfProviderSummary;
import com.vmware.vim25.PerfQuerySpec;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.PerformanceManager;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.VirtualMachine;

public class VMPerformanceCollector {

	private static Logger logger=Logger.getLogger("");
	private static HashMap<Integer, PerfCounterInfo> headerInfo = new HashMap<Integer, PerfCounterInfo>();

	
	private int maxSamples;
	private String username;
	private String password;
	private URL url;
	private List<VirtualMachine> vmList;

	public VMPerformanceCollector(URL url, String username, String password,
			int maxSamples) throws RemoteException, MalformedURLException {
		this.url = url;
		this.username = username;
		this.password = password;
		this.maxSamples = maxSamples;
		this.vmList = new ArrayList<VirtualMachine>();
		ServiceInstance si = new ServiceInstance(url, username, password,true);
		Folder rootFolder = si.getRootFolder();

		//Make a list of all the VMs
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
			String vmName) throws Exception {

		ServiceInstance serviceInstance = new ServiceInstance(url, username,
				password,true);
		InventoryNavigator inventoryNavigator = new InventoryNavigator(
				serviceInstance.getRootFolder());
		VirtualMachine virtualMachine = (VirtualMachine) inventoryNavigator
				.searchManagedEntity("VirtualMachine", vmName);
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
					value = String.valueOf(result);// + " "
							//+ info.getUnitInfo().getLabel();
					//System.out.println("If Valuuee--->"+value);
				} else if (pm instanceof PerfMetricSeriesCSV) {
					PerfMetricSeriesCSV seriesCsv = (PerfMetricSeriesCSV) pm;
					value = seriesCsv.getValue() + " in "
							+ info.getUnitInfo().getLabel();
					//System.out.println("Else Value--->"+value);
				}

				HashMap<String, String> properties;
				if (propertyGroups.containsKey(info.getGroupInfo().getKey())) {
					//System.out.println("info.getGroupInfo().getKey())==>"+info.getGroupInfo().getKey());
					properties = propertyGroups.get(info.getGroupInfo().getKey());
				} else {
					properties = new HashMap<String, String>();
					propertyGroups.put(info.getGroupInfo().getKey(), properties);
					//System.out.println("Else -- info.getGroupInfo().getKey())==>"+info.getGroupInfo().getKey());
				}

				/*String propName = String.format("[%s.%s]", info.getNameInfo().getKey(), 
						info.getRollupType());*/
				String propName =  String.format("%s_%s", info.getGroupInfo().getKey(), info.getNameInfo().getKey());
				//System.out.println("Property Name:"+propName);
				properties.put(propName, value);
			}
		}
		return propertyGroups;

	}

	public static void main(String[] args) throws Exception {
			VMPerformanceCollector perColl = new VMPerformanceCollector(
					new URL(ConstantUtil.URL), ConstantUtil.ADMIN_USER_NAME,
					ConstantUtil.ADMIN_PASSWORD, 3);
			while (true) {
			for (VirtualMachine vm : perColl.getVmList()) {
				//System.out.println("--->"+vm.getName());
				StringBuffer str = new StringBuffer();
				str.append(vm.getName());
				if ("VM02".equals(vm.getName())
						|| "VM01".equals(vm.getName())) {

					HashMap<String, HashMap<String, String>> metricsMap = perColl
							.getPerformanceMetrics(vm.getName());
					/*for (String metricName : metricsMap.keySet()) {
						System.out.println(metricName);
						HashMap<String, String> metricProps = metricsMap.get(metricName);
						str.append(metricName+"::");
						for (String p : metricProps.keySet()) {
							//System.out.println("P is "+p);
							Map<String, String> propList = new ArrayList<>();
							if(ConstantUtil.paramterList.contains(p)){
								propList.add
								str.append("\t" + p + ": " + metricProps.get(p));
								System.out.println("\t" + p + ": " + metricProps.get(p));
							}
						}
					}*/

					////new
					for (String metricNam : ConstantUtil.METRIC_LIST) {
						//System.out.println(metricNam);
						HashMap<String, String> metricProps = metricsMap
								.get(metricNam);
						//str.append(" ");
						for (String p : metricProps.keySet()) {
							//System.out.println("P is "+p);
							if (ConstantUtil.PARAMETER_LIST.contains(p)) {
								str.append(" " + p + ":" + metricProps.get(p));
								//System.out.println("\t" + p + ": " + metricProps.get(p));
							}
						}
					}
					logger.info(str);
				}
			}//end of for --> vmList
			Thread.currentThread().sleep(10000);
		}//end of while loop
	}
	

	/**
	 * @return the vmList
	 */
	public List<VirtualMachine> getVmList() {
		return vmList;
	}

	/**
	 * @param vmList the vmList to set
	 */
	public void setVmList(List<VirtualMachine> vmList) {
		this.vmList = vmList;
	}
}
