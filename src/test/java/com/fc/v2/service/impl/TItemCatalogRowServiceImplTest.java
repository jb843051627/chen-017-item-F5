package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fc.v2.common.support.CatalogRules;
import com.fc.v2.model.auto.TItemCatalogRow;
import com.fc.v2.model.auto.TItemChangeBill;
import com.fc.v2.model.custom.CatalogBookCommand;
import com.fc.v2.model.custom.CatalogReceipt;
import com.fc.v2.model.custom.CatalogReconcile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 整册处理入口端到端自测：内存假持久层，所有进门一律走 processBook（两种递法），
 * 覆盖空册/越线退/重复册次复核/跨期挂下封顶/双行合并落库/挂起两期沉底/对账三数。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class TItemCatalogRowServiceImplTest {

    /** 内存版整册服务：只覆写持久化挂钩，判定逻辑一行不改 */
    private static class InMemoryService extends TItemCatalogRowServiceImpl {
        private final List<TItemCatalogRow> store = new ArrayList<TItemCatalogRow>();
        private final List<TItemChangeBill> bills = new ArrayList<TItemChangeBill>();
        private final Set<String> cityCodes = CatalogRules.codeSet("ITEM-1001", "ITEM-1002", "ITEM-1003");
        private long seq = 1000L;

        @Override
        public TItemCatalogRow selectTItemCatalogRowById(Long id) {
            for (TItemCatalogRow r : store) {
                if (r.getId() != null && r.getId().equals(id)) {
                    return r;
                }
            }
            return null;
        }

        @Override
        public List<TItemCatalogRow> listByBatch(String batchNo) {
            List<TItemCatalogRow> list = new ArrayList<TItemCatalogRow>();
            for (TItemCatalogRow r : store) {
                if (batchNo.equals(r.getBatchNo()) && (r.getDelFlag() == null || r.getDelFlag() == 0)) {
                    list.add(r);
                }
            }
            list.sort(Comparator.comparing(TItemCatalogRow::getPeriodNo).thenComparing(TItemCatalogRow::getRowNo));
            return list;
        }

        @Override
        public List<TItemCatalogRow> listHeld() {
            List<TItemCatalogRow> list = new ArrayList<TItemCatalogRow>();
            for (TItemCatalogRow r : store) {
                if (r.getStatus() != null && r.getStatus() == CatalogRules.STATUS_HOLD
                        && (r.getArchived() == null || r.getArchived() == 0)) {
                    list.add(r);
                }
            }
            return list;
        }

        @Override
        public List<TItemCatalogRow> listArchived() {
            List<TItemCatalogRow> list = new ArrayList<TItemCatalogRow>();
            for (TItemCatalogRow r : store) {
                if (r.getArchived() != null && r.getArchived() == 1) {
                    list.add(r);
                }
            }
            return list;
        }

        @Override
        protected List<TItemCatalogRow> listCurrentRows() {
            List<TItemCatalogRow> list = new ArrayList<TItemCatalogRow>();
            for (TItemCatalogRow r : store) {
                if ((r.getDelFlag() == null || r.getDelFlag() == 0)
                        && (r.getArchived() == null || r.getArchived() == 0)) {
                    list.add(r);
                }
            }
            list.sort(Comparator.comparing(TItemCatalogRow::getBatchNo).thenComparing(TItemCatalogRow::getRowNo));
            return list;
        }

        @Override
        protected void persistLines(String batchNo, List<TItemCatalogRow> rows) {
            Map<Integer, Long> idByRowNo = new HashMap<Integer, Long>();
            for (TItemCatalogRow r : rows) {
                if (r.getMergeRowNo() == null) {
                    r.setId(Long.valueOf(++seq));
                    r.setBatchNo(batchNo);
                    r.setDelFlag(0);
                    idByRowNo.put(r.getRowNo(), r.getId());
                }
            }
            for (TItemCatalogRow r : rows) {
                if (r.getMergeRowNo() != null) {
                    r.setId(Long.valueOf(++seq));
                    r.setBatchNo(batchNo);
                    r.setDelFlag(0);
                    r.setMergedInto(idByRowNo.get(r.getMergeRowNo()));
                }
                store.add(r);
            }
            for (TItemCatalogRow r : rows) {
                if (r.getMergeRowNo() != null) {
                    for (TItemCatalogRow p : rows) {
                        if (r.getMergeRowNo().equals(p.getRowNo())) {
                            p.setDupRowNo(r.getRowNo());
                        }
                    }
                }
            }
        }

        @Override
        protected int ageCurrentHolds(int periodNo) {
            List<TItemCatalogRow> held = listHeld();
            return CatalogRules.ageHeldRows(periodNo, held);
        }

        @Override
        protected Set<String> loadCityItemCodes() {
            return cityCodes;
        }

        @Override
        protected Map<String, BigDecimal> loadCarryByItem(String currentBatchNo) {
            List<TItemCatalogRow> prior = new ArrayList<TItemCatalogRow>();
            for (TItemCatalogRow r : store) {
                if (!currentBatchNo.equals(r.getBatchNo())
                        && (r.getArchived() == null || r.getArchived() == 0)
                        && r.getMergedInto() == null) {
                    prior.add(r);
                }
            }
            prior.sort(Comparator.comparing((TItemCatalogRow r) -> r.getPeriodNo() == null ? 0 : r.getPeriodNo())
                    .thenComparing(TItemCatalogRow::getId));
            Map<String, BigDecimal> carry = new HashMap<String, BigDecimal>();
            for (TItemCatalogRow r : prior) {
                if (r.getItemCode() != null && r.getCarryQty() != null
                        && r.getCarryQty().compareTo(BigDecimal.ZERO) > 0) {
                    carry.put(r.getItemCode(), r.getCarryQty());
                }
            }
            return carry;
        }

        @Override
        protected Set<String> loadPriorUnresolved(String currentBatchNo) {
            Set<String> codes = new HashSet<String>();
            for (TItemCatalogRow r : store) {
                if (!currentBatchNo.equals(r.getBatchNo())
                        && r.getStatus() != null && r.getStatus() == CatalogRules.STATUS_HOLD
                        && (r.getArchived() == null || r.getArchived() == 0)
                        && r.getMergedInto() == null && r.getItemCode() != null) {
                    codes.add(r.getItemCode());
                }
            }
            return codes;
        }

        @Override
        protected int resolvePeriodNo(Integer specified) {
            if (specified != null && specified > 0) {
                return specified;
            }
            int max = 1;
            for (TItemCatalogRow r : store) {
                if (r.getPeriodNo() != null) {
                    max = Math.max(max, r.getPeriodNo());
                }
            }
            return max;
        }

        @Override
        protected void insertChangeBill(TItemChangeBill bill) {
            bill.setId(Long.valueOf(++seq));
            bills.add(bill);
        }
    }

    private InMemoryService svc;

    private TItemCatalogRow row(String code, String qty) {
        TItemCatalogRow r = new TItemCatalogRow();
        r.setItemCode(code);
        if (qty != null) {
            r.setQty(new BigDecimal(qty));
        }
        return r;
    }

    private CatalogBookCommand command(String batchNo, Integer period, TItemCatalogRow... rows) {
        CatalogBookCommand cmd = new CatalogBookCommand();
        cmd.setBatchNo(batchNo);
        cmd.setPeriodNo(period);
        List<String> headers = new ArrayList<String>();
        headers.add("item_code");
        headers.add("qty");
        cmd.setHeaders(headers);
        List<TItemCatalogRow> list = new ArrayList<TItemCatalogRow>();
        for (TItemCatalogRow r : rows) {
            list.add(r);
        }
        cmd.setRows(list);
        return cmd;
    }

    @BeforeEach
    public void setUp() {
        svc = new InMemoryService();
    }

    @Test
    public void emptyBookOnlyMarksEmptyNeverChecked() {
        CatalogReceipt receipt = svc.processBook(command("B-EMPTY", 1));
        assertEquals(CatalogReceipt.OUTCOME_EMPTY, receipt.getOutcome());
        assertEquals(CatalogReceipt.MARK_EMPTY, receipt.getMark());
        assertEquals(0, receipt.getTotalRows());
        assertEquals(0, svc.listByBatch("B-EMPTY").size());
    }

    @Test
    public void overLimitBookRejectedWholeWithNoLineKept() {
        TItemCatalogRow[] rows = new TItemCatalogRow[501];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = row("ITEM-1001", "1");
        }
        CatalogReceipt receipt = svc.processBook(command("B-BIG", 1, rows));
        assertEquals(CatalogReceipt.OUTCOME_REJECTED, receipt.getOutcome());
        assertEquals(501, receipt.getTotalRows());
        assertEquals(501, receipt.getRejectedRows());
        assertEquals(0, svc.listByBatch("B-BIG").size());
    }

    @Test
    public void normalBookReceiptBalancedAndOnlyOneWayIn() {
        CatalogReceipt receipt = svc.processBook("B1", java.util.Arrays.asList(
                row("ITEM-1001", "30"),
                row("ITEM-7777", "5"),
                row("ITEM-1002", null)));
        assertEquals(CatalogReceipt.OUTCOME_PROCESSED, receipt.getOutcome());
        assertEquals(CatalogReceipt.MARK_CHECKED, receipt.getMark());
        assertEquals(3, receipt.getTotalRows());
        assertEquals(1, receipt.getLinkedRows());
        assertEquals(2, receipt.getHeldRows());
        assertEquals(3, receipt.getAcceptedRows());
        assertTrue(receipt.isBalanced());
        // 行序号进门定死，挂起笔直接带出原册行次
        List<TItemCatalogRow> held = svc.listHeld();
        assertEquals(2, held.size());
        assertEquals(2, held.get(0).getRowNo().intValue());
        assertEquals(CatalogRules.REASON_ITEM_NOT_FOUND, held.get(0).getHoldReason());
        assertEquals(3, held.get(1).getRowNo().intValue());
        assertEquals(CatalogRules.REASON_QTY_BLANK, held.get(1).getHoldReason());
    }

    @Test
    public void carriedDownThirtyCapsNextBookCheckedQty() {
        svc.processBook(command("B1", 1, row("ITEM-1001", "30")));
        CatalogReceipt r2 = svc.processBook(command("B2", 2, row("ITEM-1001", "35")));
        assertEquals(1, r2.getLinkedRows());
        TItemCatalogRow line = svc.listByBatch("B2").get(0);
        assertEquals(0, new BigDecimal("30").compareTo(line.getQty()), "最多核三十，多出的一截不入本行");
        assertEquals(0, BigDecimal.ZERO.compareTo(line.getCarryQty()));
    }

    @Test
    public void priorBookUnresolvedHoldsThisBookLine() {
        svc.processBook(command("B1", 1, row("ITEM-1002", null))); // 挂起
        CatalogReceipt r2 = svc.processBook(command("B2", 2, row("ITEM-1002", "8")));
        assertEquals(0, r2.getLinkedRows());
        assertEquals(1, r2.getHeldRows());
        assertEquals(CatalogRules.REASON_PRIOR_UNRESOLVED, svc.listByBatch("B2").get(0).getHoldReason());
    }

    @Test
    public void duplicateBatchOnlyRechecksNeverRewritesOrAdds() {
        svc.processBook(command("B1", 1,
                row("ITEM-1001", "10"),
                row("ITEM-1002", "5")));
        int rowsAfterFirst = svc.listByBatch("B1").size();

        // 第二次递同一册次号：行2项数改成9、行3多添一行——一律不改写、不添新行
        CatalogReceipt again = svc.processBook(command("B1", 1,
                row("ITEM-1001", "10"),
                row("ITEM-1002", "9"),
                row("ITEM-1003", "1")));
        assertEquals(CatalogReceipt.OUTCOME_RECHECKED, again.getOutcome());
        assertEquals(rowsAfterFirst, svc.listByBatch("B1").size());
        TItemCatalogRow line2 = svc.listByBatch("B1").get(1);
        assertEquals(0, new BigDecimal("5").compareTo(line2.getQty()), "库里落地项数不得被复核改写");
        assertFalse(again.getRecheckDiffs().isEmpty());
        assertTrue(again.isBalanced(), "回执三数仍从库里逐行重算");
        assertEquals(2, again.getTotalRows());
        assertEquals(2, again.getLinkedRows());
        assertEquals(0, again.getHeldRows());

        // 项数确要改：走要素调整核签单那一路
        TItemChangeBill bill = svc.requestQtyChange(line2.getId(), new BigDecimal("9"), "省厅勘误");
        assertNotNull(bill);
        assertEquals(0, bill.getNodeNo().intValue());
        assertTrue(bill.getRemark().contains("5改为9"));
        assertEquals(0, new BigDecimal("5").compareTo(svc.selectTItemCatalogRowById(line2.getId()).getQty()),
                "起了核签单，原行依旧不改");
    }

    @Test
    public void duplicateBatchSameContentHasNoDiff() {
        svc.processBook(command("B1", 1, row("ITEM-1001", "10")));
        CatalogReceipt again = svc.processBook(command("B1", 1, row("ITEM-1001", "10")));
        assertEquals(CatalogReceipt.OUTCOME_RECHECKED, again.getOutcome());
        assertTrue(again.getRecheckDiffs().isEmpty());
    }

    @Test
    public void doubleLineSameCodeMergesOneCountAndPersistsLink() {
        CatalogReceipt receipt = svc.processBook(command("B1", 1,
                row("ITEM-1001", "10"),
                row("ITEM-1001", "88")));
        assertEquals(2, receipt.getTotalRows());
        assertEquals(2, (int) receipt.getMergedRowNos().get(0), "并掉的是靠后的第2行");
        List<TItemCatalogRow> stored = svc.listByBatch("B1");
        TItemCatalogRow primary = stored.get(0);
        TItemCatalogRow secondary = stored.get(1);
        assertEquals(1, primary.getRowNo().intValue());
        assertEquals(2, secondary.getRowNo().intValue());
        assertEquals(primary.getId(), secondary.getMergedInto());
        assertEquals(2, primary.getDupRowNo().intValue());
        assertNull(secondary.getQty());
        assertEquals(0, new BigDecimal("10").compareTo(primary.getQty()));
    }

    @Test
    public void heldTwoPeriodsMovesToArchivedAndStopsBlocking() {
        svc.processBook(command("B1", 1, row("ITEM-1002", null))); // 第1期挂起
        svc.processBook(command("B2", 2, row("ITEM-1001", "3")));  // 第2期：挂起笔历1期，仍在现行
        assertEquals(1, svc.listHeld().size());
        assertEquals(0, svc.listArchived().size());

        svc.processBook(command("B3", 3, row("ITEM-1001", "3")));  // 第3期：满两个核对期沉底
        assertEquals(0, svc.listHeld().size(), "现行册不再摆它");
        assertEquals(1, svc.listArchived().size(), "事后翻得着");

        // 来年再核不受它挡：沉底后该事项视同新挂下，不再 PRIOR_UNRESOLVED
        CatalogReceipt nextYear = svc.processBook(command("B4", 4, row("ITEM-1002", "6")));
        assertEquals(1, nextYear.getLinkedRows());
        TItemCatalogRow line = svc.listByBatch("B4").get(0);
        assertEquals(CatalogRules.STATUS_LINKED, line.getStatus().intValue());
        assertEquals(0, new BigDecimal("6").compareTo(line.getCarryQty()));
    }

    @Test
    public void reconcileBalancedAndScreenMismatchPointsOut() {
        svc.processBook(command("B1", 1,
                row("ITEM-1001", "10"),
                row("ITEM-7777", "2")));
        CatalogReconcile ok = svc.reconcile(null);
        assertTrue(ok.isBalanced());
        assertEquals(1, ok.getBookCount());
        assertEquals(2, ok.getTotalRows());
        assertEquals(1, ok.getLinkedRows());
        assertEquals(1, ok.getHeldRows());

        // 界面自数的总数对不上：不拿成数摊平，当场点明差额
        CatalogReconcile bad = svc.reconcile(Integer.valueOf(99));
        assertFalse(bad.isBalanced());
        assertFalse(bad.getDiffs().isEmpty());
        assertTrue(bad.getDiffs().get(0).contains("99"));
    }

    @Test
    public void blankBatchNoRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> svc.processBook(command("  ", 1, row("ITEM-1001", "1"))));
    }
}
