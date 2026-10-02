package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.List;

/**
 * 上级目录下发册（进门时的原始样子：册次号 + 表头 + 原始行）。
 *
 * 一册一个下发册次号；表头决定这一册有几栏、缺项按哪一套校验规则点；
 * 原始行照纸上先后排，进门那一刻行序号就按这个先后定死。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class CatalogBooklet implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 上级目录下发册次号（一册一个号，各行归各行） */
    private String batchNo;

    /** 本册表头行（栏名照抄册上字样，以逗号/竖线/制表符分栏） */
    private String header;

    /** 原始数据行（不含表头，按纸上先后排） */
    private List<String> lines;

    public CatalogBooklet() {
    }

    public CatalogBooklet(String batchNo, String header, List<String> lines) {
        this.batchNo = batchNo;
        this.header = header;
        this.lines = lines;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public List<String> getLines() {
        return lines;
    }

    public void setLines(List<String> lines) {
        this.lines = lines;
    }
}
