package com.sjsu.vmservices;

import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.HashMap;
import com.vmware.vim25.PerfCounterInfo;
import com.vmware.vim25.PerfEntityMetric;
import com.vmware.vim25.PerfEntityMetricBase;
import com.vmware.vim25.PerfMetricIntSeries;
import com.vmware.vim25.PerfMetricSeries;
import com.vmware.vim25.PerfMetricSeriesCSV;
import com.vmware.vim25.PerfProviderSummary;
import com.vmware.vim25.PerfQuerySpec;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.PerformanceManager;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.VirtualMachine;

public class VMPerformanceCollector {

	private static HashMap<Integer, PerfCounterInfo> headerInfo = new HashMap<Integer, PerfCounterInfo>();

	
	private int maxSamples;
	private String username;
	private String password;
	private URL url;

	public VMPerformanceCollector(URL url, String username, String password,
			int maxSamples) throws RemoteException, MalformedURLException {
		this.url = url;
		this.username = username;
		this.password = password;
		this.maxSamples = maxSamples;
		ServiceInstance si = new ServiceInstance(url, username, password,true);
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
					value = String.valueOf(result) + " "
							+ info.getUnitInfo().getLabel();
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

				String propName = String.format("[%s.%s]", info.getNameInfo()
						.getKey(), info.getRollupType());
				properties.put(propName, value);
			}
		}
		return propertyGroups;

	}

	public static void main(String[] args) throws Exception {
		VMPerformanceCollector c = new VMPerformanceCollector(new URL("https://130.65.132.140/sdk"),
				"administrator", "12!@qwQW", 3);
		HashMap<String, HashMap<String, String>> h = c
				.getPerformanceMetrics("T02-VM02");
		for (String s : h.keySet()) {
			System.out.println(s);
			HashMap<String, String> props = h.get(s);
			for (String p : props.keySet()) {
				System.out.println("\t" + p + ": " + props.get(p));
			}
		}
	}
}
