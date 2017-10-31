package tw.io.traffic.adp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import tw.hd.base.library.utils.ArithCompute;
import tw.hd.base.library.utils.DateUtils;
import tw.hd.base.library.utils.ListUtils;
import tw.hd.base.library.utils.SingletonUtil;
import tw.hd.base.library.utils.StringUtils;
import tw.hd.base.library.view.Result;
import tw.hd.base.library.view.ResultInfo;
import tw.hd.base.library.view.ResultUtil;
import tw.traffic.base.enums.BlacklistEnum;

/**
 * @author 孙金川
 * @version 创建时间：2017年10月13日
 */
@RestController
public class ADP {

	@Autowired
	NamedParameterJdbcTemplate namedParameterJdbcTemplate;
	@Autowired
	RestTemplate restTemplate;
	@Value("${service_key}")
	String service_key;					//服务密钥
	@Value("${local.keywords}")
	String local_keywords;				//本地车牌关键字
	@Value("${government.keywords}")
	String[] government_keywords;		//政府车辆关键字
	@Value("${sensitive.keywords}")
	String[] sensitive_keywords;		//敏感车辆关键字
	
	@PostMapping("/adp")
	public Result<?> adp(@RequestParam Map<String, Object> param) {
		/**
		 * 一：安全校验与时间处理
		 */
		//1. 安全校验
		if(!param.containsKey("service_key") && !param.get("service_key").equals(service_key)) return ResultInfo.rorbidden();
		//2. 时间处理
		String date = DateUtils.get_y_M_d(5);
		date = date.replace("-", "_");
		/**
		 * 二：SQL对应语句
		 */
		//1. 本校黑名单车辆SQL
		String blacklistSql = 				"SELECT\n" +
											"	plate_code\n" +
											"FROM\n" +
											"	tbl_gs_blacklist_vehicle";
		//2. 本校特许车辆SQL
		String licenseSql = 				"SELECT\n" +
											"	plate_code\n" +
											"FROM\n" +
											"	tbl_gs_license_vehicle";
		//3. 本校注册车辆SQL
		String registerSql = 				"SELECT\n" +
											"	plate_code\n" +
											"FROM\n" +
											"	tbl_gs_reg_vehicle";
		//4. 今日告警车辆车次SQL
		String alarmVehicleFrequencySql = 	"SELECT\n" +
											"	plate_code,\n" +
											"	vehicle_speed,\n" +
											"	limit_speed,\n" +
											"	drive_status\n" +
											"FROM\n" +
											"	tbl_vehicle_alarm_2017_10_26";
//											"	tbl_vehicle_alarm_" + date;		TODO 自动处理
		//5. 今日流动车辆车次SQL
		String recordVehicleFrequencySql = 	"SELECT\n" +
											"	plate_code\n" +
											"FROM\n" +
											"	tbl_vehicle_record_2017_10_26";
//											"	tbl_vehicle_record_" + date;
		
		/**
		 * 三：查询
		 */
		//1. 本校黑名单车辆
		List<Map<String, Object>> blacklist = namedParameterJdbcTemplate.queryForList(blacklistSql, SingletonUtil.MAP);
		//2. 本校特许车辆
		List<Map<String, Object>> license = namedParameterJdbcTemplate.queryForList(licenseSql, SingletonUtil.MAP);
		//3. 本校注册车辆
		List<Map<String, Object>> register = namedParameterJdbcTemplate.queryForList(registerSql, SingletonUtil.MAP);
		//TODO 时间异常处理，无表状态。
		//4. 今日告警车辆车次
		List<Map<String, Object>> alarmVehicleFrequency = namedParameterJdbcTemplate.queryForList(alarmVehicleFrequencySql, SingletonUtil.MAP);
		//5. 今日流动车辆车次
		List<Map<String, Object>> recordVehicleFrequency = namedParameterJdbcTemplate.queryForList(recordVehicleFrequencySql, SingletonUtil.MAP);
		
		
		//1. 今日流动车辆车次（如果SQL查询返回多项值，需要用到ListUtils.distinctCount()根据对应参数去重）
		int recordVehicleFrequencyTotal = recordVehicleFrequency.size();
		List<Map<String, Object>> record = new ArrayList<> (recordVehicleFrequency);
		ListUtils.distinctMap(record, "plate_code");
		//2. 今日告警车辆车次
		int alarmVehicleFrequencyTotal = alarmVehicleFrequency.size();
		//3. 今日告警车辆总数
		List<Map<String, Object>> alarm = new ArrayList<> (alarmVehicleFrequency);
		ListUtils.distinctMap(alarm, "plate_code");
		int alarmTotal = alarm.size();
		
		
		/**
		 * 四：ADP
		 */
		int non_localTotal = 0;					//非本地车辆总数
		int unlicensedVehicleFrequency = 0;		//无牌照车次
		int licenseTotal = 0;					//本校特许车辆总数
		int registerTotal = 0;					//白名单车辆总数
		int governmentTotal = 0;				//政府车辆总数
		List<Map<String, Object>> blacklistList = new ArrayList<Map<String, Object>> ();
		List<Map<String, Object>> sensitiveList = new ArrayList<Map<String, Object>> ();
		List<Map<String, Object>> violate_stopList = new ArrayList<Map<String, Object>> ();
		List<Map<String, Object>> overspeedList = new ArrayList<Map<String, Object>> ();
		List<Map<String, Object>> overspeed_100List = new ArrayList<Map<String, Object>> (); 
		List<Map<String, Object>> other_get_out_of_lineList = new ArrayList<Map<String, Object>> (); 
		List<String> recordList = new ArrayList<String> ();
		List<Map<String, Object>> alarmList = new ArrayList<Map<String, Object>> ();
		
		
		/*
		 * 流动ADP
		 */
		for(Map<String, Object> recordMap : record) {
			String record_code = recordMap.get("plate_code").toString();
			// 有牌照车辆车牌信息
			if(record_code.length() > 3) {
				recordList.add(record_code);
				
				// 非本地车辆、政府车辆、敏感车辆
				if(!record_code.contains(local_keywords)) {
					non_localTotal ++;
					if(StringUtils.containsArray(record_code, government_keywords)) {
						governmentTotal ++;
					}else {
						if(StringUtils.containsArray(record_code, sensitive_keywords)) {
							sensitiveList.add(recordMap);
						}
					}
				}
				
				// 特许（高级白名单）
				for(Map<String, Object> licenseMap : license) {
					String license_code = licenseMap.get("plate_code").toString();
					if(record_code.equals(license_code)) {
						licenseTotal ++;
						break;
					}
				}
				
				// 白名单
				for(Map<String, Object> registerMap : register) {
					String register_code = registerMap.get("plate_code").toString();
					if(record_code.equals(register_code)) {
						registerTotal ++;
						break;
					}
				}
			}
		}
		
		
		/*
		 * 告警ADP
		 */
		alarmVehicleFrequency.forEach(action -> {
			Map<String, Object> alarmMap = action;
			int vehicle_speed = Integer.parseInt(alarmMap.get("vehicle_speed").toString());
			int limit_speed = Integer.parseInt(alarmMap.get("limit_speed").toString());
			int drive_status = Integer.parseInt(alarmMap.get("drive_status").toString());
			
			//违停
			if(vehicle_speed == 0 && drive_status == 1039) {
				Map<String, Object> violate_stop = new HashMap<String, Object>();
				violate_stop.put("plate_code", alarmMap.get("plate_code"));
				violate_stopList.add(violate_stop);
			//超速
			}else if(vehicle_speed > limit_speed && vehicle_speed < (limit_speed * 2)) {
				overspeedList.add(alarmMap);
			//超速100%及以上
			}else if(vehicle_speed >= (limit_speed * 2)) {
				overspeed_100List.add(alarmMap);
			//其他违规
			}else {
				other_get_out_of_lineList.add(alarmMap);
			}
		});
		
		// 今日告警车辆详细信息
		List<Map<String, Object>> temp_alarm = new ArrayList<> (alarm);
		List<String> temp_blacklist = ListUtils.toList(blacklist, "plate_code");
		temp_alarm.addAll(blacklist);
		ListUtils.distinctMap(temp_alarm, "plate_code");

		for(Map<String, Object> alarmMap : temp_alarm) {
			String alarm_code = alarmMap.get("plate_code").toString();
			Map<String, Object> map = new HashMap<String, Object> ();
			if(temp_blacklist.contains(alarm_code)) {
				map.put("blacklist", 1);
				map.put("plate_code", alarm_code);
			}else {
				map.put("blacklist", 0);
				map.put("plate_code", alarm_code);
			}
			alarmList.add(map);
		}
		
		// 黑名单
		for(Map<String, Object> recordMap : recordVehicleFrequency) {
			String record_code = recordMap.get("plate_code").toString();
			
			// 无牌照车次
			if(record_code.length() < 3) {
				unlicensedVehicleFrequency ++;
			}else {
				for(Map<String, Object> blacklistMap : blacklist) {
					String blacklist_code = blacklistMap.get("plate_code").toString();
					if(record_code.equals(blacklist_code)) {
						blacklistList.add(blacklistMap);
						break;
					}
				}
			}
		}
		
		
		//4. 今日有牌照车辆总数
		int licensePlateTotal = recordList.size();
		//5. 今日无牌照车辆车次：unlicensedVehicleFrequency
		//6. 今日本校政府 车辆总数：governmentTotal
		//7. 今日本校特许车辆总数：licenseTotal
		//8. 今日本校白名单车辆总数（注册）：registerTotal
		//9. 今日社会车辆总数
		int societyTotal = (int) ArithCompute.sub(licensePlateTotal, registerTotal, licenseTotal);
		//10. 今日非本地车辆总数：non_localTotal
		//11. 今日本校黑名单车辆详细信息
		LinkedHashMap<String, Object> blacklistMap = new LinkedHashMap<String, Object>();
		ListUtils.distinctCount(blacklistList, "plate_code", 0);
		blacklistMap.put("blacklistTotal", blacklistList.size());
		blacklistMap.put("code", BlacklistEnum.BLACKLIST.getCode());
		blacklistMap.put("msg", BlacklistEnum.BLACKLIST.getMsg());
		blacklistMap.put("blacklistList", blacklistList);
		//12. 今日本校违停车辆详细信息
		LinkedHashMap<String, Object> violate_stopMap = new LinkedHashMap<String, Object> ();
		ListUtils.distinctCount(violate_stopList, "plate_code", 0);
		violate_stopMap.put("violate_stopTotal", violate_stopList.size());
		violate_stopMap.put("code", BlacklistEnum.ILLEGALLY_PARKED.getCode());
		violate_stopMap.put("msg", BlacklistEnum.ILLEGALLY_PARKED.getMsg());
		violate_stopMap.put("violate_stopList", violate_stopList);
		//13. 今日本校超速车辆详细信息
		LinkedHashMap<String, Object> overspeedMap = new LinkedHashMap<String, Object> ();
		ListUtils.distinct_count_sort_selectKeep(overspeedList, "plate_code", 0, "vehicle_speed", 0);
		overspeedMap.put("overspeedTotal", overspeedList.size());
		overspeedMap.put("code", BlacklistEnum.OVERSPEED.getCode());
		overspeedMap.put("msg", BlacklistEnum.OVERSPEED.getMsg());
		overspeedMap.put("overspeedList", overspeedList);
		//14. 今日本校超速100%车辆详细信息
		LinkedHashMap<String, Object> overspeed_100Map = new LinkedHashMap<String, Object> ();
		ListUtils.distinct_count_sort_selectKeep(overspeed_100List, "plate_code", 0, "vehicle_speed", 0);
		overspeed_100Map.put("overspeed_100Total", overspeed_100List.size());
		overspeed_100Map.put("code", BlacklistEnum.OVERSPEED_100.getCode());
		overspeed_100Map.put("msg", BlacklistEnum.OVERSPEED_100.getMsg());
		overspeed_100Map.put("overspeed_100List", overspeed_100List);
		//15. 今日本校其他违规车辆详细信息
		LinkedHashMap<String, Object> other_get_out_of_lineMap = new LinkedHashMap<String, Object> ();
		ListUtils.distinct_count_sort_selectKeep(other_get_out_of_lineList, "plate_code", 0, "vehicle_speed", 0);
		other_get_out_of_lineMap.put("other_get_out_of_lineTotal", other_get_out_of_lineList.size());
		other_get_out_of_lineMap.put("code", BlacklistEnum.OTHER_GET_OUT_OF_LINE.getCode());
		other_get_out_of_lineMap.put("msg", BlacklistEnum.OTHER_GET_OUT_OF_LINE.getMsg());
		other_get_out_of_lineMap.put("other_get_out_of_lineList", other_get_out_of_lineList);
		//16. 今日敏感车辆详细信息
		LinkedHashMap<String, Object> sensitiveMap = new LinkedHashMap<String, Object> ();
		sensitiveMap.put("sensitiveTotal", sensitiveList.size());
		sensitiveMap.put("code", BlacklistEnum.SENSITIVE.getCode());
		sensitiveMap.put("msg", BlacklistEnum.SENSITIVE.getMsg());
		sensitiveMap.put("sensitiveList", sensitiveList);
		//17. 今日流动车辆车牌信息 recordList
		//18. 今日告警车辆详细信息 alarmList
		
		
		System.out.println("今日流动车辆车次：" + recordVehicleFrequencyTotal);
		System.out.println("今日告警车辆车次：" + alarmVehicleFrequencyTotal);
		System.out.println("今日告警车辆总数：" + alarmTotal);
		System.out.println("今日有牌照车辆总数：" + licensePlateTotal);
		System.out.println("今日其他车辆（无牌照等）车次：" + unlicensedVehicleFrequency);
		System.out.println("政府车辆总数" + governmentTotal);
		System.out.println("特许车辆总数" + licenseTotal);
		System.out.println("白名单车辆总数" + registerTotal);
		System.out.println("社会车辆总数" + societyTotal);
		System.out.println("非本地车辆总数" + non_localTotal);
		System.out.println("今日本校黑名单车辆详细信息：" + blacklistMap);
		System.out.println("今日本校违停车辆详细信息：" + violate_stopMap);
		System.out.println("今日本校超速车辆详细信息：" + overspeedMap);
		System.out.println("今日本校超速100%车辆详细信息：" + overspeed_100Map);
		System.out.println("今日本校其他违规车辆详细信息：" + other_get_out_of_lineMap);
		System.out.println("今日敏感车辆详细信息：" + sensitiveMap);
		System.out.println("今日流动车辆车牌信息：" + recordList);
		System.out.println("今日告警车辆详细信息：" + alarmList);
		
		
		LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object> ();
		result.put("recordVehicleFrequencyTotal", recordVehicleFrequencyTotal);		//今日流动车辆车次
		result.put("alarmVehicleFrequencyTotal", alarmVehicleFrequencyTotal);		//今日告警车辆车次
		result.put("alarmTotal", alarmTotal);										//今日告警车辆总数
		result.put("licensePlateTotal", licensePlateTotal);							//今日有牌照车辆总数
		result.put("unlicensedVehicleFrequency", unlicensedVehicleFrequency);		//今日其他车辆（无牌照等）车次
		result.put("governmentTotal", governmentTotal);								//政府车辆总数
		result.put("licenseTotal", licenseTotal);									//特许车辆总数
		result.put("registerTotal", registerTotal);									//白名单车辆总数
		result.put("societyTotal", societyTotal);									//社会车辆总数
		result.put("non_localTotal", non_localTotal);								//非本地车辆总数
		result.put("blacklistMap", blacklistMap);									//今日本校黑名单车辆详细信息
		result.put("violate_stopMap", violate_stopMap);								//今日本校违停车辆详细信息
		result.put("overspeedMap", overspeedMap);									//今日本校超速车辆详细信息
		result.put("overspeed_100Map", overspeed_100Map);							//今日本校超速100%车辆详细信息
		result.put("other_get_out_of_lineMap", other_get_out_of_lineMap);			//今日本校其他违规车辆详细信息
		result.put("sensitiveMap", sensitiveMap);									//今日敏感车辆详细信息
		result.put("recordList", recordList);										//今日流动车辆车牌信息
		result.put("alarmList", alarmList);											//今日告警车辆详细信息
		
		return ResultUtil.success(result);
	}
}
