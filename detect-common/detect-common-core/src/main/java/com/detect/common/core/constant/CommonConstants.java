package com.detect.common.core.constant;

/**
 * 全平台通用常量。
 */
public interface CommonConstants {

    /** 成功响应码(Python 端据 code==0 判定投递成功) */
    int SUCCESS = 0;
    /** 通用失败响应码 */
    int FAIL = 1;

    /** @Inner 内部调用请求头名 */
    String FROM = "from";
    /** @Inner 内部调用请求头值 */
    String FROM_IN = "Y";

    /** 逻辑删除标记：正常 */
    String DEL_FLAG_NORMAL = "0";
    /** 逻辑删除标记：已删除 */
    String DEL_FLAG_DELETED = "1";

    /** 统一时间格式(东八区) */
    String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
}
