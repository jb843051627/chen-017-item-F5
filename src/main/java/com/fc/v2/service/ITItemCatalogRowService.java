package com.fc.v2.service;

import java.util.List;

import com.fc.v2.model.auto.TItemCatalogRow;
import com.fc.v2.model.auto.TItemChangeBill;
import com.fc.v2.model.custom.CatalogBookCommand;
import com.fc.v2.model.custom.CatalogReceipt;
import com.fc.v2.model.custom.CatalogReconcile;

import java.math.BigDecimal;

/**
 * 上级目录下发册核对行 Service接口。
 * 一册自进门到落账只有一条道：{@link #processBook} 整册处理入口。某行挂上、挂住，
 * 还是随整册被推回，一律在此一处判定；册共几行、收进几行、推出几行也在此一处给回。
 * 入口两种递法：旧递法（册次号+行）照旧使得；新递法（整册进门命令，表头/规则随册）并排同名。
 *
 * @author fuce
 * @date 2026-10-02
 */
public interface ITItemCatalogRowService {

    /** 按主键回查单行（翻挂起笔时由行直接带出原册行次 rowNo） */
    TItemCatalogRow selectTItemCatalogRowById(Long id);

    /**
     * 整册处理入口（旧递法）：册次号 + 逐行，按默认表头（item_code、qty）与缺省校验规则点。
     */
    CatalogReceipt processBook(String batchNo, List<TItemCatalogRow> rows);

    /**
     * 整册处理入口（新递法）：册次号、核对期号、本册表头（有几栏）、缺项校验规则代号、
     * 逐行内容都随本册命令来。
     */
    CatalogReceipt processBook(CatalogBookCommand command);

    /** 某册现行入账行（不含并掉的次行与往期沉底行），按原册行次升序 */
    List<TItemCatalogRow> listByBatch(String batchNo);

    /** 挂起层：现行挂住的笔（每笔自带原册行次，不劳人翻纸页重数） */
    List<TItemCatalogRow> listHeld();

    /** 往期册页：挂起满两个核对期仍未落地、被挪沉底的行 */
    List<TItemCatalogRow> listArchived();

    /**
     * 全辖对账：册级回执与逐行加总同一回算出。screenTotalRows 为全辖屏上自数的总行数，
     * 传非空时若与逐行加出的数接不平，差在哪几行当场点出，不拿成数摊平。
     */
    CatalogReconcile reconcile(Integer screenTotalRows);

    /**
     * 复核发现项数确要改：不在复核里改写落地行，走要素调整核签单那一路（县核/市核/省定逐级签）。
     */
    TItemChangeBill requestQtyChange(Long rowId, BigDecimal newQty, String reason);
}
