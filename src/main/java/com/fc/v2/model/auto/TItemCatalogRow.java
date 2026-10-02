package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 上级目录下发册核对行对象 t_item_catalog_row
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_item_catalog_row")
@ApiModel(value = "TItemCatalogRow", description = "上级目录下发册核对行")
public class TItemCatalogRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 上级目录下发册次号 */
    @TableField("batch_no")
    @ApiModelProperty(value = "上级目录下发册次号")
    private String batchNo;

    /** 原册行次 */
    @TableField("row_no")
    @ApiModelProperty(value = "原册行次")
    private Integer rowNo;

    /** 被挑出的前置项代号 */
    @TableField("item_code")
    @ApiModelProperty(value = "被挑出的前置项代号")
    private String itemCode;

    /** 本行待核要素项数（同码双行只在靠前行记一次） */
    @TableField("qty")
    @ApiModelProperty(value = "本行待核要素项数")
    private BigDecimal qty;

    /** 截至本行挂下未核余额（上一册挂下来、本期没核完的数） */
    @TableField("carry_qty")
    @ApiModelProperty(value = "截至本行挂下未核余额")
    private BigDecimal carryQty;

    /** 行落地情形 0待核 1已挂接 2挂起 */
    @TableField("status")
    @ApiModelProperty(value = "行落地情形 0待核 1已挂接 2挂起")
    private Integer status;

    /** 挂起缘故代号 ITEM_NOT_FOUND/QTY_BLANK/QTY_NONPOSITIVE/PRIOR_UNRESOLVED */
    @TableField("hold_reason")
    @ApiModelProperty(value = "挂起缘故代号 ITEM_NOT_FOUND/QTY_BLANK/QTY_NONPOSITIVE/PRIOR_UNRESOLVED")
    private String holdReason;

    /** 进门所属核对期号 */
    @TableField("period_no")
    @ApiModelProperty(value = "进门所属核对期号")
    private Integer periodNo;

    /** 挂起已历核对期数 */
    @TableField("hold_periods")
    @ApiModelProperty(value = "挂起已历核对期数")
    private Integer holdPeriods;

    /** 是否挪入往期册页 0现行 1往期 */
    @TableField("archived")
    @ApiModelProperty(value = "是否挪入往期册页 0现行 1往期")
    private Integer archived;

    /** 同码双行并入的主行id */
    @JsonSerialize(using = ToStringSerializer.class)
    @TableField("merged_into")
    @ApiModelProperty(value = "同码双行并入的主行id")
    private Long mergedInto;

    /** 主行并掉的次行原册行次 */
    @TableField("dup_row_no")
    @ApiModelProperty(value = "主行并掉的次行原册行次")
    private Integer dupRowNo;

    /** 引擎内部：次行所并主行的原册行次（不落库，落库前由服务层换成 merged_into 主行id） */
    @TableField(exist = false)
    private transient Integer mergeRowNo;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getCarryQty() {
        return carryQty;
    }

    public void setCarryQty(BigDecimal carryQty) {
        this.carryQty = carryQty;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getHoldReason() {
        return holdReason;
    }

    public void setHoldReason(String holdReason) {
        this.holdReason = holdReason;
    }

    public Integer getPeriodNo() {
        return periodNo;
    }

    public void setPeriodNo(Integer periodNo) {
        this.periodNo = periodNo;
    }

    public Integer getHoldPeriods() {
        return holdPeriods;
    }

    public void setHoldPeriods(Integer holdPeriods) {
        this.holdPeriods = holdPeriods;
    }

    public Integer getArchived() {
        return archived;
    }

    public void setArchived(Integer archived) {
        this.archived = archived;
    }

    public Long getMergedInto() {
        return mergedInto;
    }

    public void setMergedInto(Long mergedInto) {
        this.mergedInto = mergedInto;
    }

    public Integer getDupRowNo() {
        return dupRowNo;
    }

    public void setDupRowNo(Integer dupRowNo) {
        this.dupRowNo = dupRowNo;
    }

    public Integer getMergeRowNo() {
        return mergeRowNo;
    }

    public void setMergeRowNo(Integer mergeRowNo) {
        this.mergeRowNo = mergeRowNo;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
