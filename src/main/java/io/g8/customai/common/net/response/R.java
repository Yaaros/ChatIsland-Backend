package io.g8.customai.common.net.response;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class R {

    // Getter 和 Setter 方法

    private Boolean success;

    private Integer code;

    private String message;

    //这里之前有个bug，不添加get方法，response就没有data:{token:jwt}这一项
    private Map<String,Object> data = new HashMap<>();

    // 私有化构造方法
    private R() {}

    private R(Boolean success, Integer code, String message) {
        this.success = success;
        this.code = code;
        this.message = message;
    }

    public static R ok(){
        R r = new R();
        r.setSuccess(true);
        r.setCode(ResultCode.SUCCESS);
        r.setMessage("成功");
        return r;
    }


    public static R error(){
        R r = new R();
        r.setSuccess(false);
        r.setCode(ResultCode.ERROR);
        r.setMessage("失败");
        return r;
    }


    // 静态方法用于快速创建对象
    public R success() {
        this.setSuccess(true);
        return this;
    }

    public R failure() {
        this.setSuccess(false);
        return this;
    }
    public R message(String message) {
        this.setMessage(message);
        return this;
    }
    public R code(Integer code) {
        this.setCode(code);
        return this;
    }
    public R data(String key, Object value) {
        this.data.put(key, value);
        return this;
    }
    public R data(Map<String, Object> map) {
        this.setData(map);
        return this;
    }

    private void setData(Map<String, Object> map) {
        this.data = map;
    }

    @Override
    public String toString() {
        return "Response:{" +
                "success=" + success +
                ", code=" + code +
                ", message='" + message + '\'' +
                ", data=" + data +
                '}';
    }
}