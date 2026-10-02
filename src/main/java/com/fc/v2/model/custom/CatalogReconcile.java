package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 全辖对账结果：按册回执与逐行加总同一回算出，接不平时把差在哪几行当场点出来，
 * 不许拿一个成数摊平。
 *
 * @author fuce
 * @date 2026-10-02
 */
@ApiModel(value = "CatalogReconcile", description = "全辖下发册对账结果")
public class CatalogReconcile {

    @ApiModelProperty(value = "全辖现行册数")
    private int bookCount;

    @ApiModelProperty(value = "全辖现行总行数（逐册相加）")
    private int totalRows;

    @ApiModelProperty(value = "全辖已挂接总行数（逐行加出）")
    private int linkedRows;

    @ApiModelProperty(value = "全辖挂起总行数（逐行加出）")
    private int heldRows;

    @ApiModelProperty(value = "逐行加总是否接平（挂上加挂住等于总行数）")
    private boolean balanced;

    @ApiModelProperty(value = "接不平的差行明细（册次号+原册行次+主键+缘故）")
    private List<String> diffs = new ArrayList<String>();

    public int getBookCount() {
        return bookCount;
    }

    public void setBookCount(int bookCount) {
        this.bookCount = bookCount;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
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

    public boolean isBalanced() {
        return balanced;
    }

    public void setBalanced(boolean balanced) {
        this.balanced = balanced;
    }

    public List<String> getDiffs() {
        return diffs;
    }

    public void setDiffs(List<String> diffs) {
        this.diffs = diffs;
    }
}
