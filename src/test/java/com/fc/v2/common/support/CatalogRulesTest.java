package com.fc.v2.common.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fc.v2.model.auto.TItemCatalogRow;
import org.junit.jupiter.api.Test;

/**
 * 下发册纯规则引擎自测：四种挂起、核减封顶、双行合并、行次定死、挂起两期沉底、三数守恒。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class CatalogRulesTest {

    private static final Set<String> CITY = CatalogRules.codeSet("ITEM-1001", "ITEM-1002", "ITEM-1003");

    private TItemCatalogRow row(String code, String qty) {
        TItemCatalogRow r = new TItemCatalogRow();
        r.setItemCode(code);
        if (qty != null) {
            r.setQty(new BigDecimal(qty));
        }
        return r;
    }

    private List<TItemCatalogRow> book(TItemCatalogRow... rows) {
        List<TItemCatalogRow> list = new ArrayList<TItemCatalogRow>();
        for (TItemCatalogRow r : rows) {
            list.add(r);
        }
        return list;
    }

    private int[] eval(int period, List<TItemCatalogRow> rows, Map<String, BigDecimal> carry, Set<String> prior) {
        return CatalogRules.evaluateLines(period, rows, CITY,
                carry == null ? new HashMap<String, BigDecimal>() : carry,
                prior == null ? new java.util.HashSet<String>() : prior, true);
    }

    @Test
    public void itemNotOnCityCatalogHolds() {
        List<TItemCatalogRow> rows = book(row("ITEM-7777", "5"));
        int[] r = eval(1, rows, null, null);
        assertEquals(0, r[0]);
        assertEquals(1, r[1]);
        assertEquals(CatalogRules.STATUS_HOLD, rows.get(0).getStatus().intValue());
        assertEquals(CatalogRules.REASON_ITEM_NOT_FOUND, rows.get(0).getHoldReason());
        assertTrue(rows.get(0).getRemark().contains("不在本市名录"));
    }

    @Test
    public void blankQtyHolds() {
        List<TItemCatalogRow> rows = book(row("ITEM-1002", null));
        int[] r = eval(1, rows, null, null);
        assertEquals(0, r[0]);
        assertEquals(1, r[1]);
        assertEquals(CatalogRules.REASON_QTY_BLANK, rows.get(0).getHoldReason());
    }

    @Test
    public void zeroOrNegativeQtyHolds() {
        List<TItemCatalogRow> rows = book(row("ITEM-1002", "0"), row("ITEM-1003", "-3"));
        int[] r = eval(1, rows, null, null);
        assertEquals(0, r[0]);
        assertEquals(2, r[1]);
        assertEquals(CatalogRules.REASON_QTY_NONPOSITIVE, rows.get(0).getHoldReason());
        assertEquals(CatalogRules.REASON_QTY_NONPOSITIVE, rows.get(1).getHoldReason());
    }

    @Test
    public void priorUnresolvedHolds() {
        List<TItemCatalogRow> rows = book(row("ITEM-1002", "8"));
        Set<String> prior = CatalogRules.codeSet("ITEM-1002");
        int[] r = eval(2, rows, null, prior);
        assertEquals(0, r[0]);
        assertEquals(1, r[1]);
        assertEquals(CatalogRules.REASON_PRIOR_UNRESOLVED, rows.get(0).getHoldReason());
        // 报数原样留证，不被改成0或抹掉
        assertEquals(0, new BigDecimal("8").compareTo(rows.get(0).getQty()));
    }

    @Test
    public void singleBadLineDoesNotRejectWholeBook() {
        // 旧"一行出错整册抱回"已废：坏行挂起，其余各行照常进账
        List<TItemCatalogRow> rows = book(
                row("ITEM-1001", "10"),
                row("ITEM-7777", "5"),
                row("ITEM-1002", "4"));
        int[] r = eval(1, rows, null, null);
        assertEquals(2, r[0]);
        assertEquals(1, r[1]);
        assertTrue(CatalogRules.balanced(rows.size(), r[0], r[1]));
    }

    @Test
    public void rowNoFixedAtIntakeByArrivalOrder() {
        TItemCatalogRow first = row("ITEM-1001", "10");
        first.setRowNo(Integer.valueOf(99)); // 纸面上自带的行号不作数
        List<TItemCatalogRow> rows = book(first, row("ITEM-1002", "5"), row("ITEM-1003", "2"));
        eval(1, rows, null, null);
        assertEquals(1, rows.get(0).getRowNo().intValue());
        assertEquals(2, rows.get(1).getRowNo().intValue());
        assertEquals(3, rows.get(2).getRowNo().intValue());
    }

    @Test
    public void firstSightCarriesDownAsPendingBalance() {
        List<TItemCatalogRow> rows = book(row("ITEM-1001", "30"));
        int[] r = eval(1, rows, null, null);
        assertEquals(1, r[0]);
        assertEquals(0, new BigDecimal("30").compareTo(rows.get(0).getCarryQty()));
    }

    @Test
    public void checkedQtyMustNotExceedCarriedDown() {
        // 上一册挂下30，本期报核35：最多核30，多出的一截不入本行、不抵邻行
        Map<String, BigDecimal> carry = new HashMap<String, BigDecimal>();
        carry.put("ITEM-1001", new BigDecimal("30"));
        List<TItemCatalogRow> rows = book(row("ITEM-1001", "35"));
        int[] r = eval(2, rows, carry, null);
        assertEquals(1, r[0]);
        assertEquals(0, r[1]);
        assertEquals(0, new BigDecimal("30").compareTo(rows.get(0).getQty()));
        assertEquals(0, BigDecimal.ZERO.compareTo(rows.get(0).getCarryQty()));
        assertNotNull(rows.get(0).getRemark());
        assertTrue(rows.get(0).getRemark().contains("多出的一截不入本行"));

        // 不抵邻行：邻行 ITEM-1002 首见，项数照常挂下，与 1001 多出的5无关
        List<TItemCatalogRow> two = book(row("ITEM-1001", "35"), row("ITEM-1002", "7"));
        eval(2, two, carry, null);
        assertEquals(0, new BigDecimal("7").compareTo(two.get(1).getQty()));
        assertEquals(0, new BigDecimal("7").compareTo(two.get(1).getCarryQty()));
    }

    @Test
    public void sameItemCodeTwiceMergesIntoOneCount() {
        // 同事项码本册占双行：并成一行，项数只记靠前一次，行次取靠前的
        List<TItemCatalogRow> rows = book(
                row("ITEM-1001", "10"),
                row("ITEM-1002", "5"),
                row("ITEM-1001", "99"));
        int[] r = eval(1, rows, null, null);
        assertEquals(3, r[0]); // 物理三行都有落点
        TItemCatalogRow primary = rows.get(0);
        TItemCatalogRow secondary = rows.get(2);
        assertEquals(1, secondary.getMergeRowNo().intValue());
        assertEquals(3, primary.getDupRowNo().intValue());
        assertNull(secondary.getQty(), "次行项数不记");
        assertEquals(0, new BigDecimal("10").compareTo(primary.getQty()));
    }

    @Test
    public void mergedSecondaryFollowsPrimaryHold() {
        // 主行挂起时，同码次行随主行挂起、同种缘故；不能只挂主行漏了次行
        List<TItemCatalogRow> rows = book(
                row("ITEM-7777", "10"),
                row("ITEM-7777", "20"));
        int[] r = eval(1, rows, null, null);
        assertEquals(0, r[0]);
        assertEquals(2, r[1]);
        assertEquals(CatalogRules.REASON_ITEM_NOT_FOUND, rows.get(1).getHoldReason());
        assertNull(rows.get(1).getQty());
    }

    @Test
    public void heldRowsAgeTwoPeriodsThenArchive() {
        TItemCatalogRow held = row("ITEM-1002", null);
        held.setStatus(CatalogRules.STATUS_HOLD);
        held.setHoldReason(CatalogRules.REASON_QTY_BLANK);
        held.setPeriodNo(Integer.valueOf(1));
        held.setHoldPeriods(Integer.valueOf(0));
        held.setArchived(Integer.valueOf(0));
        List<TItemCatalogRow> one = book(held);

        assertEquals(0, CatalogRules.ageHeldRows(2, one));
        assertEquals(1, held.getHoldPeriods().intValue());
        assertEquals(0, held.getArchived().intValue());

        // 满两个核对期：挪进往期册页
        assertEquals(1, CatalogRules.ageHeldRows(3, one));
        assertEquals(2, held.getHoldPeriods().intValue());
        assertEquals(1, held.getArchived().intValue());
    }

    @Test
    public void samePeriodBooksDoNotAgeTwice() {
        TItemCatalogRow held = row("ITEM-1002", null);
        held.setStatus(CatalogRules.STATUS_HOLD);
        held.setPeriodNo(Integer.valueOf(2));
        held.setHoldPeriods(Integer.valueOf(0));
        held.setArchived(Integer.valueOf(0));
        List<TItemCatalogRow> one = book(held);
        // 同一核对期前后递几册，进门期号相同的不增岁
        assertEquals(0, CatalogRules.ageHeldRows(2, one));
        assertEquals(0, held.getHoldPeriods().intValue());
        assertFalse(held.getArchived().intValue() == 1);
    }

    @Test
    public void missingQtyColumnTreatsAsBlank() {
        List<TItemCatalogRow> rows = book(row("ITEM-1001", "10"));
        int[] r = CatalogRules.evaluateLines(1, rows, CITY, new HashMap<String, BigDecimal>(),
                new java.util.HashSet<String>(), false);
        assertEquals(CatalogRules.REASON_QTY_BLANK, rows.get(0).getHoldReason());
        assertEquals(1, r[1]);
    }

    @Test
    public void missingCodeColumnTreatsAsNotFound() {
        // 本册表头没有 item_code 栏：即使行里带着码，也按取不到、不在名录挂起
        List<TItemCatalogRow> rows = book(row("ITEM-1001", "10"));
        int[] r = CatalogRules.evaluateLines(1, rows, CITY, new HashMap<String, BigDecimal>(),
                new java.util.HashSet<String>(), false, true);
        assertEquals(CatalogRules.REASON_ITEM_NOT_FOUND, rows.get(0).getHoldReason());
        assertEquals(1, r[1]);
    }
}
