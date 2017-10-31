//package tw.io.traffic.aspect;
//
//import java.net.URI;
//import java.net.URISyntaxException;
//
//import org.aspectj.lang.annotation.AfterReturning;
//import org.aspectj.lang.annotation.AfterThrowing;
//import org.aspectj.lang.annotation.Aspect;
//import org.aspectj.lang.annotation.Before;
//import org.aspectj.lang.annotation.Pointcut;
//import org.json.simple.JSONObject;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.HttpMethod;
//import org.springframework.http.ResponseEntity;
//import org.springframework.stereotype.Component;
//import org.springframework.web.client.RestTemplate;
//
//import lombok.extern.slf4j.Slf4j;
//import tw.io.traffic.servicelink.ServiceLink;
//
///**
// * @author  孙金川
// * @version 创建时间：2017年10月14日
// */
//@Component
//@Aspect
//@Slf4j
//public class ADPAspect {
//	
//	@Autowired
//	RestTemplate restTemplate;
//	@Autowired
//	ServiceLink serviceLink;
//	boolean loginStatus = false;
//	boolean logoutStatus = false;
//
//	@Pointcut("execution(public * tw.io.traffic.adp.ADP.*(..))")
//	public void ADPVaild() {
//
//	}
//
//	@Before("ADPVaild()")
//	public void doBefore() {
//		if(!loginStatus){
//			log.info("登录AOP");
//			loginStatus = true;
//			login();
//		}
//	}
//	
//	@AfterReturning(returning = "response", pointcut = "ADPVaild()")
//	public void doAfterReturning(ResponseEntity<?> response) throws InterruptedException {
//		String body = response.getBody().toString();
//		String contentType = response.getHeaders().getContentType().toString();
//		if(contentType.contains("html")) {
//			log.info("返回AOP：登录失败...");
//			Thread.sleep(5000);
//			if(!logoutStatus) {
//				logoutStatus = true;
//				login();
//				logoutStatus = false;
//			}
//			Thread.sleep(5000);
////			if(loginStatus) {
////				loginStatus = false;
////			}else{
////				Thread.sleep(120000);
////			}
//		}else{
//			log.info(body);
//		}
//	}
//	
//	@AfterThrowing(throwing = "exception", pointcut = "ADPVaild()")
//	public void doAfterThrowing() {
//		log.info("有异常...");
//	}
//	
//	public void login() {
//		String imgCode = serviceLink.readImgCode();
//
//		URI uri = null;
//		try {
//			uri = new URI("http://fire.vcarecity.com/login/login_login?tm=" + System.currentTimeMillis() + "&loginname=jyxt&password=000000&code=" + imgCode);
//		} catch (URISyntaxException e) {
//			e.printStackTrace();
//		}
//
//		ResponseEntity<JSONObject> response = restTemplate.exchange(uri, HttpMethod.GET, serviceLink.HttpEntityJSON(), JSONObject.class);
//		JSONObject json = response.getBody();
//		if(json.containsValue("success")){
//			log.info("登录成功：{}", json.toString());
//		}else{
//			login();
//		}
//	}
//	
//}
