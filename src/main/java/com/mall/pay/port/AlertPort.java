package com.mall.pay.port;

/**
 * 支付模块对外告警接口。业务系统提供实现。
 */
public interface AlertPort {
    /** 普通告警 */
    void alert(String title, String content);

    /** 高优先级告警 */
    void urgent(String title, String content);

    /** 严重告警（资金/安全类） */
    void critical(String title, String content, Throwable e);
}