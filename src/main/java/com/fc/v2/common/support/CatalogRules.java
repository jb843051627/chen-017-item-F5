package com.fc.v2.common.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fc.v2.model.auto.TItemCatalogRow;

/**
 * 下发册整册判定的纯规则（不碰库，进门逐行找落点只在此一处算）。
 * 行落点三种：已挂接 {@link #STATUS_LINKED}、挂起 {@link #STATUS_HOLD}（待核 0 为入库初态，不经本引擎）。
 *
 * @author fuce
 * @date 2026-10-02
 */
public final class CatalogRules {

    /** 行落地情形：待核 */
    public static final int STATUS_PENDING = 0;
    /** 行落地情形：已挂接（挂上） */
    public static final int STATUS_LINKED = 1;
    /** 行落地情形：挂起（挂住） */
    public static final int STATUS_HOLD = 2;

    /** 挂起缘故：事项码不在本市名录上 */
    public static final String REASON_ITEM_NOT_FOUND = "ITEM_NOT_FOUND";
    /** 挂起缘故：待核项数那栏空着 */
    public static final String REASON_QTY_BLANK = "QTY_BLANK";
    /** 挂起缘故：报成零或负数 */
    public static final String REASON_QTY_NONPOSITIVE = "QTY_NONPOSITIVE";
    /** 挂起缘故：上一册那笔还没了结又来报这一册 */
    public static final String REASON_PRIOR_UNRESOLVED = "PRIOR_UNRESOLVED";

    /** 挂起满几个核对期仍未落地，挪进往期册页 */
    public static final int HOLD_PERIODS_TO_ARCHIVE = 2;

    private CatalogRules() {
    }

    /** 一句话挂起缘故（写在 remark，回执/界面直接念得出来） */
    public static String reasonText(String reasonCode) {
        if (REASON_ITEM_NOT_FOUND.equals(reasonCode)) {
            return "事项码不在本市名录上";
        }
        if (REASON_QTY_BLANK.equals(reasonCode)) {
            return "待核项数那栏空着";
        }
        if (REASON_QTY_NONPOSITIVE.equals(reasonCode)) {
            return "项数报成零或负数";
        }
        if (REASON_PRIOR_UNRESOLVED.equals(reasonCode)) {
            return "上一册该事项那笔还没了结，又来报这一册";
        }
        return reasonCode;
    }

    /**
     * 一册逐行判定。入参 rows 按原册先后排好（物理行序），本方法：
     * 1) 行次进门照先后定死（rowNo=1..N，不信纸面自带行号）；
     * 2) 同一事项码本册占双行，并成一行：靠前行为主行、项数只记主行一次，次行挂 mergedIntoRowNo；
     * 3) 主行按四种挂起情形 + 核减数不超上一册挂下数逐条判，次行随主行落点；
     * 4) 同一回计算里数出挂上/挂住，保证挂上加挂住等于本册行数。
     *
     * @param periodNo          本册核对期号
     * @param rows              本册物理各行（按进门先后）
     * @param cityItemCodes     本市名录上的事项码
     * @param carryByItem       各事项上一册挂下未核余额（无此键表示头一回出现，不设限，核多少挂下多少）
     * @param priorUnresolved   上一册仍挂住未了结的事项码（已沉底往期的不在内，不挡来年）
     * @param qtyColumnPresent  本册表头是否带 qty 栏（表头缺栏视同全栏空）
     * @return [挂上数, 挂住数]
     */
    public static int[] evaluateLines(int periodNo, List<TItemCatalogRow> rows, Set<String> cityItemCodes,
                                      Map<String, BigDecimal> carryByItem, Set<String> priorUnresolved,
                                      boolean qtyColumnPresent) {
        return evaluateLines(periodNo, rows, cityItemCodes, carryByItem, priorUnresolved, true, qtyColumnPresent);
    }

    /**
     * 同上，另可声明本册表头是否带 item_code 栏：表头缺栏时事项码一律取不到，按不在名录挂起。
     */
    public static int[] evaluateLines(int periodNo, List<TItemCatalogRow> rows, Set<String> cityItemCodes,
                                      Map<String, BigDecimal> carryByItem, Set<String> priorUnresolved,
                                      boolean codeColumnPresent, boolean qtyColumnPresent) {
        Map<String, Integer> primaryRowNoByCode = new HashMap<String, Integer>();
        Map<Integer, TItemCatalogRow> primaryByRowNo = new HashMap<Integer, TItemCatalogRow>();

        for (int i = 0; i < rows.size(); i++) {
            TItemCatalogRow r = rows.get(i);
            int rowNo = i + 1;
            r.setRowNo(Integer.valueOf(rowNo));
            r.setPeriodNo(Integer.valueOf(periodNo));
            r.setHoldPeriods(Integer.valueOf(0));
            r.setArchived(Integer.valueOf(0));
            r.setMergedInto(null);
            r.setMergeRowNo(null);
            r.setDupRowNo(null);

            String code = codeColumnPresent ? normalizeCode(r.getItemCode()) : null;
            r.setItemCode(code);
            if (code != null && primaryRowNoByCode.containsKey(code)) {
                // 同码双行：次行挂到靠前主行上，项数不记
                int primaryRowNo = primaryRowNoByCode.get(code).intValue();
                r.setMergeRowNo(Integer.valueOf(primaryRowNo));
            } else {
                if (code != null) {
                    primaryRowNoByCode.put(code, Integer.valueOf(rowNo));
                }
                primaryByRowNo.put(Integer.valueOf(rowNo), r);
                evaluatePrimary(r, code, cityItemCodes, carryByItem, priorUnresolved, qtyColumnPresent);
            }
        }

        int linked = 0;
        int held = 0;
        List<Integer> mergedRowNos = new ArrayList<Integer>();
        for (TItemCatalogRow r : rows) {
            if (r.getMergeRowNo() != null) {
                TItemCatalogRow primary = primaryByRowNo.get(r.getMergeRowNo());
                // 次行随主行落点；项数只在主行记一次，次行 qty 一律空
                r.setStatus(primary.getStatus());
                r.setHoldReason(primary.getHoldReason());
                r.setQty(null);
                r.setCarryQty(null);
                r.setRemark("同事项码本册双行，并入行" + primary.getRowNo() + "，项数只记一次");
                mergedRowNos.add(r.getRowNo());
                primary.setDupRowNo(r.getRowNo());
            }
            if (STATUS_LINKED == r.getStatus().intValue()) {
                linked++;
            } else if (STATUS_HOLD == r.getStatus().intValue()) {
                held++;
            }
        }
        return new int[]{linked, held};
    }

    private static void evaluatePrimary(TItemCatalogRow r, String code, Set<String> cityItemCodes,
                                        Map<String, BigDecimal> carryByItem, Set<String> priorUnresolved,
                                        boolean qtyColumnPresent) {
        BigDecimal reported = r.getQty();
        if (code == null || !cityItemCodes.contains(code)) {
            hold(r, REASON_ITEM_NOT_FOUND, reported, carryByItem.get(code));
            return;
        }
        if (!qtyColumnPresent || reported == null) {
            hold(r, REASON_QTY_BLANK, reported, carryByItem.get(code));
            return;
        }
        if (reported.compareTo(BigDecimal.ZERO) <= 0) {
            hold(r, REASON_QTY_NONPOSITIVE, reported, carryByItem.get(code));
            return;
        }
        if (priorUnresolved.contains(code)) {
            hold(r, REASON_PRIOR_UNRESOLVED, reported, carryByItem.get(code));
            return;
        }

        BigDecimal carry = carryByItem.get(code);
        BigDecimal recorded;
        BigDecimal balance;
        String capNote = null;
        if (carry == null) {
            // 头一回出现：本行项数即上挂待核余额（挂下数），当期不设核减上限
            recorded = reported;
            balance = reported;
            capNote = "首见挂下" + reported.toPlainString() + "待后续核对期核";
        } else {
            if (reported.compareTo(carry) > 0) {
                // 多出来的那一截不许塞进本行，也不许抵邻行，只核到挂下余额为止
                recorded = carry;
                capNote = "报核" + reported.toPlainString() + "，上一册仅挂下" + carry.toPlainString()
                        + "，本行至多核" + carry.toPlainString() + "，多出的一截不入本行";
            } else {
                recorded = reported;
            }
            balance = carry.subtract(recorded);
        }
        r.setStatus(Integer.valueOf(STATUS_LINKED));
        r.setHoldReason(null);
        r.setQty(recorded);
        r.setCarryQty(balance);
        r.setRemark(capNote);
    }

    private static void hold(TItemCatalogRow r, String reasonCode, BigDecimal reported, BigDecimal carry) {
        r.setStatus(Integer.valueOf(STATUS_HOLD));
        r.setHoldReason(reasonCode);
        // 原样留证（空/零/负都不改），挂下余额不动
        r.setQty(reported);
        r.setCarryQty(carry);
        r.setRemark(reasonText(reasonCode));
    }

    private static String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 挂起行按期增岁：同一行连着满 {@link #HOLD_PERIODS_TO_ARCHIVE} 个核对期仍未落地，
     * 挪进往期册页（archived=1），现行册不再摆它；沉底后不挡来年再核。
     * 只对进门期号早于本期（periodNo 更大）的行增岁，同期递的多册不重复计期。
     *
     * @return 本回沉底的行数
     */
    public static int ageHeldRows(int periodNo, List<TItemCatalogRow> currentHeldRows) {
        int archived = 0;
        for (TItemCatalogRow r : currentHeldRows) {
            int entered = r.getPeriodNo() == null ? periodNo : r.getPeriodNo().intValue();
            if (entered >= periodNo) {
                continue;
            }
            int periods = (r.getHoldPeriods() == null ? 0 : r.getHoldPeriods().intValue()) + 1;
            r.setHoldPeriods(Integer.valueOf(periods));
            if (periods >= HOLD_PERIODS_TO_ARCHIVE) {
                r.setArchived(Integer.valueOf(1));
                archived++;
            }
        }
        return archived;
    }

    /** 挂上加挂住必须等于本册行数；同一回计算内兜底，不平抛错，不许拿成数摊平 */
    public static boolean balanced(int totalRows, int linked, int held) {
        return linked + held == totalRows;
    }

    /** 集合便捷构造（测试/服务层用） */
    public static Set<String> codeSet(String... codes) {
        Set<String> set = new HashSet<String>();
        for (String c : codes) {
            set.add(c);
        }
        return set;
    }
}
