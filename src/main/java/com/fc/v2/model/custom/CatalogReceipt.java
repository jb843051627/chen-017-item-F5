package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fc.v2.model.auto.TItemCatalogRow;

/**
 * 整册处理回执。
 *
 * 回执上那三个数（本册共几行、挂上几行、挂住几行）与库里逐行算出来的出自同一回计算，
 * 挂住的加挂上的等于本册行数；界面自己数出来的不作数。
 * 一行也没有的空册，回执只落「空册」两字，不许写核讫。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class CatalogReceipt implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 落账结局：照常落账（回执落「核讫」） */
    public static final int OUTCOME_BOOKED = 1;
    /** 落账结局：一行也没有的空册（回执只落「空册」，不许写核讫） */
    public static final int OUTCOME_EMPTY = 2;
    /** 落账结局：越过单册行数上限，整册退回，一行不留 */
    public static final int OUTCOME_RETURNED = 3;
    /** 落账结局：同一下发册次号重复递入，只当复核走过一遍，不改写任何一行、不再添新行 */
    public static final int OUTCOME_REVIEW = 4;

    /** 上级目录下发册次号 */
    private String batchNo;

    /** 落账结局（见 OUTCOME_* 常量） */
    private int outcome;

    /** 进门原始行数（纸上几行就是几行） */
    private int rawRows;

    /** 同一事项码在本册占了双行而并掉的行数（项数只记一次，行次取靠前的那一个） */
    private int mergedRows;

    /** 本册落账行数＝挂上行数＋挂住行数 */
    private int totalRows;

    /** 挂上行数（收进：待核＋已挂接） */
    private int acceptedRows;

    /** 挂住行数（推出：挂起） */
    private int suspendedRows;

    /** 逐行落地情形（屏上要的行序号就在各行 rowNo 上，不劳重新数纸页） */
    private List<TItemCatalogRow> rows = new ArrayList<TItemCatalogRow>();

    /** 附记（复核时逐行比对出的差异等） */
    private List<String> notes = new ArrayList<String>();

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public int getOutcome() {
        return outcome;
    }

    public void setOutcome(int outcome) {
        this.outcome = outcome;
    }

    /**
     * 回执上落的两个字（或四个字），由结局一处派生，不许另写：
     * 空册就是「空册」，照常落账才是「核讫」。
     */
    public String getOutcomeText() {
        switch (outcome) {
            case OUTCOME_BOOKED:
                return "核讫";
            case OUTCOME_EMPTY:
                return "空册";
            case OUTCOME_RETURNED:
                return "整册退回";
            case OUTCOME_REVIEW:
                return "复核";
            default:
                return "未知";
        }
    }

    public int getRawRows() {
        return rawRows;
    }

    public void setRawRows(int rawRows) {
        this.rawRows = rawRows;
    }

    public int getMergedRows() {
        return mergedRows;
    }

    public void setMergedRows(int mergedRows) {
        this.mergedRows = mergedRows;
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

    public int getSuspendedRows() {
        return suspendedRows;
    }

    public void setSuspendedRows(int suspendedRows) {
        this.suspendedRows = suspendedRows;
    }

    public List<TItemCatalogRow> getRows() {
        return rows;
    }

    public void setRows(List<TItemCatalogRow> rows) {
        this.rows = rows;
    }

    public List<String> getNotes() {
        return notes;
    }

    public void setNotes(List<String> notes) {
        this.notes = notes;
    }
}
