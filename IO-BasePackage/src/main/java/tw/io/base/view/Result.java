package tw.io.base.view;

import lombok.Getter;
import lombok.ToString;

/**
 * 结果返回，最顶层。
 * 
 * @author 孙金川
 * @version 创建时间：2017年10月8日
 */
@Getter
@ToString
public class Result<T> {

	/* 状态码 */
	private Integer code;
	/* 提示信息 */
	private String msg;
	/* 数据 */
	private T data;
	/* 状态 */
	private Boolean flag;
	/* token */
	private String token;

	public Result() {
	}

	public Result(Integer code, String msg, Boolean flag) {
		this.code = code;
		this.msg = msg;
		this.flag = flag;
	}

	public Result(Integer code, String msg, T data, Boolean flag) {
		this.code = code;
		this.msg = msg;
		this.data = data;
		this.flag = flag;
	}

	public Result(Integer code, String msg, T data, Boolean flag, String token) {
		this.code = code;
		this.msg = msg;
		this.data = data;
		this.flag = flag;
		this.token = token;
	}

}
