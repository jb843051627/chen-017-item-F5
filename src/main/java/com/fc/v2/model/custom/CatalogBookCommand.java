package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 整册进门命令（整册处理入口的新一种递法）：册次号、核对期号、本册表头（有几栏）、
 * 缺项校验规则代号、逐行内容，都随本册来。
 *
 * @author fuce
 * @date 2026-10-02
 */
@ApiModel(value = "CatalogBookCommand", description = "上级目录下发册整册进门命令")
public class CatalogBookCommand {

    /** 上级目录下发册次号（一册一号） */
    @ApiModelProperty(value = "上级目录下发册次号")
    private String batchNo;

    /** 本册进门所属核对期号；不传由服务层按在册最大期顺延 */
    @ApiModelProperty(value = "进门所属核对期号")
    private Integer periodNo;

    /** 本册表头栏位代号（至少须含 item_code、qty），缺项按哪套规则点随表头来 */
    @ApiModelProperty(value = "本册表头栏位代号")
    private List<String> headers = new ArrayList<String>();

    /** 缺项校验规则代号，缺省 DEFAULT（四项挂起情形：码不在名录/项数空/零或负/上一册未结）；未知代号整册不进门 */
    @ApiModelProperty(value = "缺项校验规则代号（缺省DEFAULT）")
    private String ruleCode;

    /** 本册各行，进门先后即原册行次，进门照先后定死 */
    @ApiModelProperty(value = "本册各行（按原册先后）")
    private List<com.fc.v2.model.auto.TItemCatalogRow> rows = new ArrayList<com.fc.v2.model.auto.TItemCatalogRow>();

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public Integer getPeriodNo() {
        return periodNo;
    }

    public void setPeriodNo(Integer periodNo) {
        this.periodNo = periodNo;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public List<com.fc.v2.model.auto.TItemCatalogRow> getRows() {
        return rows;
    }

    public void setRows(List<com.fc.v2.model.auto.TItemCatalogRow> rows) {
        this.rows = rows;
    }
}
