package tw.io.base.enums;

/**
 * @author  孙金川
 * @version 创建时间：2017年10月8日
 */
public enum ResultEnum {
	USERISNULL(100, "用户不存在"),
    PASSWORDERR(101, "密码错误"),
    USERPAWDISNULL(102, "账户或密码为空"),
    PHONEEXIST(103, "手机号已被注册"),
    RANDCODE(104, "验证码错误"),
    ORDERPASSWORD(105, "原始密码不能为空"),
    ORDERPWDERR(106, "原密码错误"),
    NEWPWDERR(107, "新密码不能为空"),
    SUCCESS(200, "成功"),
    FAIL(201, "失败,系统错误"),
    FAIL_SAVE(202, "保存失败"),
    FAIL_PARAMS(203, "参数传递失败"),
    LOGINTIMEOUT(204, "登录过期"),
    FAILROLE(205, "无权限");
	
	private Integer code;
	private String msg;

	ResultEnum(Integer code, String msg) {
		this.code = code;
		this.msg = msg;
	}

	public Integer getCode() {
		return code;
	}

	public String getMsg() {
		return msg;
	}
}
