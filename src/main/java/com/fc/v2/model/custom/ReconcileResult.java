package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fc.v2.model.auto.TItemCatalogRow;

/**
 * 全辖对账结果。
 *
 * 屏上自称的三个数（本册共几行、挂上几行、挂住几行）与库里逐行加出来的数对一遍；
 * 接不平的，差在哪几行当场点出来（挂上的那几行、挂住的那几行都列在此），
 * 不拿一个成数摊平。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class ReconcileResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 对到哪一册；空表示全辖一起对 */
    private String batchNo;

    /** 接得平则为真：屏上自称的三个数与库里逐行加出的数一一对上 */
    private boolean balanced;

    /** 屏上自称：本册共几行 */
    private Integer claimedTotal;

    /** 屏上自称：挂上几行 */
    private Integer claimedAccepted;

    /** 屏上自称：挂住几行 */
    private Integer claimedSuspended;

    /** 库里逐行加出：本册共几行 */
    private int actualTotal;

    /** 库里逐行加出：挂上几行 */
    private int actualAccepted;

    /** 库里逐行加出：挂住几行 */
    private int actualSuspended;

    /** 库里挂上的那几行（差就差在这几行，当场点出） */
    private List<TItemCatalogRow> acceptedRows = new ArrayList<TItemCatalogRow>();

    /** 库里挂住的那几行（差就差在这几行，当场点出） */
    private List<TItemCatalogRow> suspendedRows = new ArrayList<TItemCatalogRow>();

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public boolean isBalanced() {
        return balanced;
    }

    public void setBalanced(boolean balanced) {
        this.balanced = balanced;
    }

    public Integer getClaimedTotal() {
        return claimedTotal;
    }

    public void setClaimedTotal(Integer claimedTotal) {
        this.claimedTotal = claimedTotal;
    }

    public Integer getClaimedAccepted() {
        return claimedAccepted;
    }

    public void setClaimedAccepted(Integer claimedAccepted) {
        this.claimedAccepted = claimedAccepted;
    }

    public Integer getClaimedSuspended() {
        return claimedSuspended;
    }

    public void setClaimedSuspended(Integer claimedSuspended) {
        this.claimedSuspended = claimedSuspended;
    }

    public int getActualTotal() {
        return actualTotal;
    }

    public void setActualTotal(int actualTotal) {
        this.actualTotal = actualTotal;
    }

    public int getActualAccepted() {
        return actualAccepted;
    }

    public void setActualAccepted(int actualAccepted) {
        this.actualAccepted = actualAccepted;
    }

    public int getActualSuspended() {
        return actualSuspended;
    }

    public void setActualSuspended(int actualSuspended) {
        this.actualSuspended = actualSuspended;
    }

    public List<TItemCatalogRow> getAcceptedRows() {
        return acceptedRows;
    }

    public void setAcceptedRows(List<TItemCatalogRow> acceptedRows) {
        this.acceptedRows = acceptedRows;
    }

    public List<TItemCatalogRow> getSuspendedRows() {
        return suspendedRows;
    }

    public void setSuspendedRows(List<TItemCatalogRow> suspendedRows) {
        this.suspendedRows = suspendedRows;
    }
}
