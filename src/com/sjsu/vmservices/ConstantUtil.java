package com.sjsu.vmservices;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConstantUtil {

	public static final List<String> METRIC_LIST = new ArrayList<String>(
			Arrays.asList("cpu","datastore","disk","mem","net","power","sys"));
	public static String URL = "https://130.65.132.140/sdk";
	public static String ADMIN_USER_NAME = "administrator";
	public static String ADMIN_PASSWORD = "12!@qwQW";
	public static List<String> PARAMETER_LIST = new ArrayList<String>(
			Arrays.asList("cpu_usage","cpu_usagemhz",
					"datastore_totalWriteLatency", "datastore_totalReadLatency",
					"disk_write", "disk_read", "disk_maxTotalLatency",  "disk_usage",
					"mem_granted", "mem_consumed","mem_active","mem_vmmemctl",
					"net_usage","net_received","net_transmitted",
					"power_power",
					"sys_uptime"));
	
	public static List<String> PROJECT_PARAMETER_LIST_DRS1 = new ArrayList<String>(
			Arrays.asList("cpu_usagemhz"));
	
	public static List<String> PROJECT_PARAMETER_LIST_DRS2 = new ArrayList<String>(
			Arrays.asList("cpu_usage"));
	
	public static String SSL_THUMBPRINT_HOST_143 = "48:D7:11:E0:09:4E:24:5F:99:C6:5B:4C:62:12:48:65:3A:3A:DD:80";
}
