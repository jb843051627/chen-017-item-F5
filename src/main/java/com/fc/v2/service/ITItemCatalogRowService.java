package com.fc.v2.service;

import java.util.List;

import com.fc.v2.model.auto.TItemCatalogRow;
import com.fc.v2.model.custom.CatalogBooklet;
import com.fc.v2.model.custom.CatalogReceipt;
import com.fc.v2.model.custom.ReconcileResult;

/**
 * 上级目录下发册核对行 Service接口（batch-process 形状：整册提交，无单条增删改）
 *
 * 一册自进门到落账只有一条道，通到本接口的整册处理入口：某行算挂上、算挂住，
 * 还是随整册被推回，一律在这一处判定；册共几行、收进几行、推出几行，也在同一处给回，
 * 页面上自己数的那份不作准，也没有哪只手能往里补一行。
 *
 * 两种递法并排同名：原先的递法（已解析的行）照旧使得；新添的使法（整册递上，
 * 表头加原始行）就在同名底下并排再添一个。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITItemCatalogRowService {

    /** 按主键回查单行 */
    TItemCatalogRow selectTItemCatalogRowById(Long id);

    /**
     * 整册处理入口·原先的递法：已解析的行照先后递上。
     * 逐行判定落点（待核/已挂接/挂起）：合法的入账，不合的只挂那一行（保留行号与缘故），
     * 同册其余各行照常进账。返回挂上行数（收进：待核＋已挂接）。
     * 同一下发册次号重复提交只当复核走过一遍，不改写任何一行、不再添新行，
     * 返回照库里逐行算出的挂上行数；行数越过单册上限整册退回、一行不留，返回 -1；
     * 空册返回 0。
     */
    int submitBatch(String batchNo, List<TItemCatalogRow> rows);

    /**
     * 整册处理入口·新添的使法：整册（册次号＋表头＋原始行）递上。
     * 册进门先随本册表头过解析（几栏、缺项按哪一套校验规则点都照表头定），
     * 再走同一处判定落账。回执一次给回：册共几行、收进几行、推出几行，
     * 与库里逐行算出来的出自同一回计算；空册回执只落「空册」，不写核讫。
     */
    CatalogReceipt submitBatch(CatalogBooklet booklet);

    /**
     * 回执重算：与落账那回同一口径，从库里逐行加出那三个数。
     * 整册退回的册一行不留，重算只见空册。
     */
    CatalogReceipt receipt(String batchNo);

    /** 该册挂起层明细（按行号升序，屏上直接带出行序号，不劳重新数纸页） */
    List<TItemCatalogRow> listErrors(String batchNo);

    /** 往期册页：连着满两个核对期仍未落地的挂起行挪在此处，现行册不再摆它，事后翻得着 */
    List<TItemCatalogRow> listArchived();

    /**
     * 全辖对账：屏上自称的三个数与库里逐行加出的数对一遍，
     * 接不平的差在哪几行当场点出来，不拿一个成数摊平。
     * batchNo 为空则全辖一起对。
     */
    ReconcileResult reconcile(String batchNo, Integer claimedTotal, Integer claimedAccepted, Integer claimedSuspended);
}
