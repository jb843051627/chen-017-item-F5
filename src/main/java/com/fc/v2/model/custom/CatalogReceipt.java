package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 整册处理回执。册共几行、收进几行、推出几行、挂上几行、挂住几行，
 * 都在服务层整册处理入口同一回计算里给回，页面不得自数。
 *
 * @author fuce
 * @date 2026-10-02
 */
@ApiModel(value = "CatalogReceipt", description = "上级目录下发册整册处理回执")
public class CatalogReceipt {

    /** 整册处理结局 PROCESSED落账/RECHECKED重复册次只复核/EMPTY空册/REJECTED越线整册退回 */
    public static final String OUTCOME_PROCESSED = "PROCESSED";
    public static final String OUTCOME_RECHECKED = "RECHECKED";
    public static final String OUTCOME_EMPTY = "EMPTY";
    public static final String OUTCOME_REJECTED = "REJECTED";

    /** 回执字样：空册只落「空册」，不许写核讫；重复册次只落「复核」 */
    public static final String MARK_CHECKED = "核讫";
    public static final String MARK_RECHECK = "复核";
    public static final String MARK_EMPTY = "空册";
    public static final String MARK_REJECTED = "整册退回";

    @ApiModelProperty(value = "下发册次号")
    private String batchNo;

    @ApiModelProperty(value = "核对期号")
    private Integer periodNo;

    @ApiModelProperty(value = "处理结局")
    private String outcome;

    @ApiModelProperty(value = "回执字样 核讫/空册/整册退回")
    private String mark;

    @ApiModelProperty(value = "本册共几行（原册物理行）")
    private int totalRows;

    @ApiModelProperty(value = "收进几行（挂上加挂住）")
    private int acceptedRows;

    @ApiModelProperty(value = "推出几行（越线整册退回时等于册行数，否则0）")
    private int rejectedRows;

    @ApiModelProperty(value = "挂上几行（已挂接）")
    private int linkedRows;

    @ApiModelProperty(value = "挂住几行（挂起）")
    private int heldRows;

    @ApiModelProperty(value = "本次进门新挂起几行")
    private int newHeldRows;

    @ApiModelProperty(value = "本回处理挪入往期册页的挂起笔数")
    private int archivedRows;

    @ApiModelProperty(value = "同码双行并掉的次行行次")
    private List<Integer> mergedRowNos = new ArrayList<Integer>();

    @ApiModelProperty(value = "重复册次复核逐行比对差异（不改写任何一行）")
    private List<String> recheckDiffs = new ArrayList<String>();

    @ApiModelProperty(value = "挂住加挂上是否等于本册行数（同一回计算内校验）")
    private boolean balanced;

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

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getMark() {
        return mark;
    }

    public void setMark(String mark) {
        this.mark = mark;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getAcceptedRows() {
        return acceptedRows;
    }

    public void setAcceptedRows(int acceptedRows) {
        this.acceptedRows = acceptedRows;
    }

    public int getRejectedRows() {
        return rejectedRows;
    }

    public void setRejectedRows(int rejectedRows) {
        this.rejectedRows = rejectedRows;
    }

    public int getLinkedRows() {
        return linkedRows;
    }

    public void setLinkedRows(int linkedRows) {
        this.linkedRows = linkedRows;
    }

    public int getHeldRows() {
        return heldRows;
    }

    public void setHeldRows(int heldRows) {
        this.heldRows = heldRows;
    }

    public int getNewHeldRows() {
        return newHeldRows;
    }

    public void setNewHeldRows(int newHeldRows) {
        this.newHeldRows = newHeldRows;
    }

    public int getArchivedRows() {
        return archivedRows;
    }

    public void setArchivedRows(int archivedRows) {
        this.archivedRows = archivedRows;
    }

    public List<Integer> getMergedRowNos() {
        return mergedRowNos;
    }

    public void setMergedRowNos(List<Integer> mergedRowNos) {
        this.mergedRowNos = mergedRowNos;
    }

    public List<String> getRecheckDiffs() {
        return recheckDiffs;
    }

    public void setRecheckDiffs(List<String> recheckDiffs) {
        this.recheckDiffs = recheckDiffs;
    }

    public boolean isBalanced() {
        return balanced;
    }

    public void setBalanced(boolean balanced) {
        this.balanced = balanced;
    }
}
