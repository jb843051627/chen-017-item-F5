package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.support.CatalogRules;
import com.fc.v2.mapper.auto.TItemCatalogRowMapper;
import com.fc.v2.mapper.auto.TItemChangeBillMapper;
import com.fc.v2.mapper.auto.TItemCityItemMapper;
import com.fc.v2.model.auto.TItemCatalogRow;
import com.fc.v2.model.auto.TItemChangeBill;
import com.fc.v2.model.auto.TItemCityItem;
import com.fc.v2.model.custom.CatalogBookCommand;
import com.fc.v2.model.custom.CatalogReceipt;
import com.fc.v2.model.custom.CatalogReconcile;
import com.fc.v2.service.ITItemCatalogRowService;

/**
 * 上级目录下发册核对行 Service业务层处理。
 * 一册自进门到落账只有一条道——{@link #processBook} 整册处理入口；某行挂上、挂住还是随整册
 * 被推回，一律在此一处判定；册共几行、收进几行、推出几行也在此一处给回，页面自数不作准。
 *
 * @author fuce
 * @date 2026-10-02
 */
@Service
public class TItemCatalogRowServiceImpl implements ITItemCatalogRowService {

    /** 单册行数上限：越线整册退回，一行不留 */
    private static final int MAX_ROWS_PER_BOOK = 500;

    /** 缺项校验规则套代号：今年只此一套，未知代号不许进门 */
    private static final String RULE_DEFAULT = "DEFAULT";

    private static final int DEL_FLAG_NORMAL = 0;

    @javax.annotation.Resource
    private TItemCatalogRowMapper itemCatalogRowMapper;

    @javax.annotation.Resource
    private TItemCityItemMapper itemCityItemMapper;

    @javax.annotation.Resource
    private TItemChangeBillMapper itemChangeBillMapper;

    @Override
    public TItemCatalogRow selectTItemCatalogRowById(Long id) {
        return this.itemCatalogRowMapper.selectById(id);
    }

    /** 旧递法：册次号 + 逐行，默认表头（item_code、qty 两栏都在）与缺省校验规则。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CatalogReceipt processBook(String batchNo, List<TItemCatalogRow> rows) {
        CatalogBookCommand command = new CatalogBookCommand();
        command.setBatchNo(batchNo);
        List<String> headers = new ArrayList<String>();
        headers.add("item_code");
        headers.add("qty");
        command.setHeaders(headers);
        command.setRows(rows == null ? new ArrayList<TItemCatalogRow>() : rows);
        return processBook(command);
    }

    /** 新递法：册次号、期号、表头、缺项规则、逐行都随本册命令来。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CatalogReceipt processBook(CatalogBookCommand command) {
        if (command == null || command.getBatchNo() == null || command.getBatchNo().trim().isEmpty()) {
            throw new IllegalArgumentException("下发册次号不能为空");
        }
        String batchNo = command.getBatchNo().trim();
        String ruleCode = command.getRuleCode();
        if (ruleCode != null && !ruleCode.trim().isEmpty() && !RULE_DEFAULT.equalsIgnoreCase(ruleCode.trim())) {
            throw new IllegalArgumentException("未知的缺项校验规则套：" + ruleCode + "（今年仅 DEFAULT 一套）");
        }
        List<TItemCatalogRow> incoming = command.getRows() == null
                ? new ArrayList<TItemCatalogRow>() : command.getRows();

        // 册级兜底一：一行也没有的空册，回执只落「空册」，不许写核讫，不落任何行
        if (incoming.isEmpty()) {
            CatalogReceipt receipt = newReceipt(batchNo, command.getPeriodNo());
            receipt.setOutcome(CatalogReceipt.OUTCOME_EMPTY);
            receipt.setMark(CatalogReceipt.MARK_EMPTY);
            return receipt;
        }

        // 册级兜底二：行数越上限，整册退回，一行不留
        if (incoming.size() > MAX_ROWS_PER_BOOK) {
            CatalogReceipt receipt = newReceipt(batchNo, command.getPeriodNo());
            receipt.setOutcome(CatalogReceipt.OUTCOME_REJECTED);
            receipt.setMark(CatalogReceipt.MARK_REJECTED);
            receipt.setTotalRows(incoming.size());
            receipt.setRejectedRows(incoming.size());
            return receipt;
        }

        // 册级规则三：同一下发册次号第二次递，只当复核走过一遍——逐行比对，不改写、不添新行
        List<TItemCatalogRow> stored = listByBatch(batchNo);
        if (!stored.isEmpty()) {
            return recheck(batchNo, stored, incoming);
        }

        int periodNo = resolvePeriodNo(command.getPeriodNo());

        // 先给历史挂起笔增岁：满两个核对期未落地的沉底往期，不挡本期/来年
        int archived = ageCurrentHolds(periodNo);

        Set<String> cityCodes = loadCityItemCodes();
        Map<String, BigDecimal> carryByItem = loadCarryByItem(batchNo);
        Set<String> priorUnresolved = loadPriorUnresolved(batchNo);
        boolean qtyColumnPresent = command.getHeaders() == null || command.getHeaders().isEmpty()
                || command.getHeaders().contains("qty");
        boolean codeColumnPresent = command.getHeaders() == null || command.getHeaders().isEmpty()
                || command.getHeaders().contains("item_code");

        List<TItemCatalogRow> working = new ArrayList<TItemCatalogRow>();
        for (TItemCatalogRow r : incoming) {
            working.add(r);
        }
        int[] linkedHeld = CatalogRules.evaluateLines(periodNo, working, cityCodes, carryByItem,
                priorUnresolved, codeColumnPresent, qtyColumnPresent);
        int linked = linkedHeld[0];
        int held = linkedHeld[1];

        persistLines(batchNo, working);

        CatalogReceipt receipt = newReceipt(batchNo, Integer.valueOf(periodNo));
        receipt.setOutcome(CatalogReceipt.OUTCOME_PROCESSED);
        receipt.setMark(CatalogReceipt.MARK_CHECKED);
        receipt.setTotalRows(working.size());
        receipt.setAcceptedRows(linked + held);
        receipt.setRejectedRows(0);
        receipt.setLinkedRows(linked);
        receipt.setHeldRows(held);
        receipt.setNewHeldRows(held);
        receipt.setArchivedRows(archived);
        for (TItemCatalogRow r : working) {
            if (r.getMergeRowNo() != null) {
                receipt.getMergedRowNos().add(r.getRowNo());
            }
        }
        // 三个数同一回计算，挂住加挂上必须等于本册行数；不平直接报错，不拿成数摊平
        if (!CatalogRules.balanced(receipt.getTotalRows(), linked, held)) {
            throw new IllegalStateException("回执三数接不平：总" + receipt.getTotalRows()
                    + " 挂" + linked + " 挂起" + held);
        }
        receipt.setBalanced(true);
        return receipt;
    }

    @Override
    public List<TItemCatalogRow> listByBatch(String batchNo) {
        return this.itemCatalogRowMapper.selectList(new QueryWrapper<TItemCatalogRow>()
                .eq("batch_no", batchNo)
                .eq("del_flag", DEL_FLAG_NORMAL)
                .orderByAsc("period_no", "row_no"));
    }

    @Override
    public List<TItemCatalogRow> listHeld() {
        return this.itemCatalogRowMapper.selectList(new QueryWrapper<TItemCatalogRow>()
                .eq("status", CatalogRules.STATUS_HOLD)
                .eq("archived", 0)
                .eq("del_flag", DEL_FLAG_NORMAL)
                .orderByAsc("period_no", "batch_no", "row_no"));
    }

    @Override
    public List<TItemCatalogRow> listArchived() {
        return this.itemCatalogRowMapper.selectList(new QueryWrapper<TItemCatalogRow>()
                .eq("archived", 1)
                .eq("del_flag", DEL_FLAG_NORMAL)
                .orderByDesc("period_no").orderByAsc("batch_no", "row_no"));
    }

    @Override
    public CatalogReconcile reconcile(Integer screenTotalRows) {
        List<TItemCatalogRow> all = listCurrentRows();

        // 按册汇总与逐行加总同一回算出
        Map<String, List<TItemCatalogRow>> byBatch = new LinkedHashMap<String, List<TItemCatalogRow>>();
        int linked = 0;
        int held = 0;
        List<String> diffs = new ArrayList<String>();
        for (TItemCatalogRow r : all) {
            List<TItemCatalogRow> bookRows = byBatch.get(r.getBatchNo());
            if (bookRows == null) {
                bookRows = new ArrayList<TItemCatalogRow>();
                byBatch.put(r.getBatchNo(), bookRows);
            }
            bookRows.add(r);
            Integer st = r.getStatus();
            if (st != null && st.intValue() == CatalogRules.STATUS_LINKED) {
                linked++;
            } else if (st != null && st.intValue() == CatalogRules.STATUS_HOLD) {
                held++;
            } else {
                // 落点既非挂上也非挂住的行，就是接不平的差行，当场点出来
                diffs.add("差行：册" + r.getBatchNo() + " 行" + r.getRowNo()
                        + " 事项" + r.getItemCode() + " 落点既非已挂接也非挂起(status=" + st + ")");
            }
        }
        for (Map.Entry<String, List<TItemCatalogRow>> e : byBatch.entrySet()) {
            int bLinked = 0;
            int bHeld = 0;
            for (TItemCatalogRow r : e.getValue()) {
                if (r.getStatus() != null && r.getStatus().intValue() == CatalogRules.STATUS_LINKED) {
                    bLinked++;
                } else if (r.getStatus() != null && r.getStatus().intValue() == CatalogRules.STATUS_HOLD) {
                    bHeld++;
                }
            }
            if (bLinked + bHeld != e.getValue().size()) {
                diffs.add("差册：册" + e.getKey() + " 共" + e.getValue().size()
                        + "行，挂上" + bLinked + " 挂住" + bHeld + "，缺"
                        + (e.getValue().size() - bLinked - bHeld) + "行对不上");
            }
        }

        CatalogReconcile result = new CatalogReconcile();
        result.setBookCount(byBatch.size());
        result.setTotalRows(all.size());
        result.setLinkedRows(linked);
        result.setHeldRows(held);
        result.setBalanced(diffs.isEmpty() && linked + held == all.size());
        // 全辖屏上自数的总数与逐行加出的接不平：把差摊到册、点到行，不用一个成数抹平
        if (screenTotalRows != null && screenTotalRows.intValue() != all.size()) {
            result.setBalanced(false);
            diffs.add(0, "全辖屏上总数" + screenTotalRows + "与逐行加出" + all.size()
                    + "不平，差" + (all.size() - screenTotalRows.intValue()) + "行，请按下列册/行核对");
        }
        result.setDiffs(diffs);
        return result;
    }

    /**
     * 项数确要改：复核发现项数对不上时，不在复核里改写，走要素调整核签单那一路。
     */
    public TItemChangeBill requestQtyChange(Long rowId, BigDecimal newQty, String reason) {
        TItemCatalogRow row = selectTItemCatalogRowById(rowId);
        if (row == null || (row.getDelFlag() != null && row.getDelFlag().intValue() == 1)) {
            throw new IllegalArgumentException("该行不存在");
        }
        if (newQty == null || newQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("改后项数须为正数");
        }
        TItemChangeBill bill = new TItemChangeBill();
        bill.setBillNo("CB-" + row.getBatchNo() + "-R" + row.getRowNo());
        bill.setNodeNo(Integer.valueOf(0));
        bill.setSignMode(Integer.valueOf(0));
        bill.setNeedCount(Integer.valueOf(1));
        bill.setSignCount(Integer.valueOf(0));
        bill.setStatus(Integer.valueOf(0));
        bill.setDelFlag(Integer.valueOf(DEL_FLAG_NORMAL));
        bill.setRemark("册" + row.getBatchNo() + " 行" + row.getRowNo() + " 事项" + row.getItemCode()
                + " 项数由" + (row.getQty() == null ? "空" : row.getQty().toPlainString())
                + "改为" + newQty.toPlainString()
                + (reason == null || reason.trim().isEmpty() ? "" : "；" + reason));
        insertChangeBill(bill);
        return bill;
    }

    /** 落要素调整核签单（单独成钩，便于内存自测接住） */
    protected void insertChangeBill(TItemChangeBill bill) {
        this.itemChangeBillMapper.insert(bill);
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /** 全辖现行行（未沉底往期、未删），按册、行次排 */
    protected List<TItemCatalogRow> listCurrentRows() {
        return this.itemCatalogRowMapper.selectList(new QueryWrapper<TItemCatalogRow>()
                .eq("del_flag", DEL_FLAG_NORMAL)
                .eq("archived", 0)
                .orderByAsc("batch_no", "row_no"));
    }

    /** 重复册次：逐行比对库里落地与本次报送，不改任何一行、不添新行；回执数从库里逐行重算 */
    private CatalogReceipt recheck(String batchNo, List<TItemCatalogRow> stored, List<TItemCatalogRow> incoming) {
        CatalogReceipt receipt = newReceipt(batchNo,
                stored.get(0).getPeriodNo());
        receipt.setOutcome(CatalogReceipt.OUTCOME_RECHECKED);
        receipt.setMark(CatalogReceipt.MARK_RECHECK);
        receipt.setTotalRows(stored.size());

        List<String> diffs = new ArrayList<String>();
        if (stored.size() != incoming.size()) {
            diffs.add("行数不一致：库存" + stored.size() + "行，本次报送" + incoming.size() + "行；不改写不添行");
        }
        int n = Math.min(stored.size(), incoming.size());
        for (int i = 0; i < n; i++) {
            TItemCatalogRow db = stored.get(i);
            TItemCatalogRow in = incoming.get(i);
            String dbCode = db.getItemCode();
            String inCode = in.getItemCode() == null ? null : in.getItemCode().trim();
            if (!equalsCode(dbCode, inCode)) {
                diffs.add("行" + db.getRowNo() + " 事项码不一致：库存" + dbCode + "，报送" + inCode);
            }
            if (!equalsQty(db.getQty(), in.getQty())) {
                diffs.add("行" + db.getRowNo() + " 项数不一致：库存"
                        + (db.getQty() == null ? "空" : db.getQty().toPlainString())
                        + "，报送" + (in.getQty() == null ? "空" : in.getQty().toPlainString())
                        + "；确要改走要素调整核签单");
            }
        }

        int linked = 0;
        int held = 0;
        for (TItemCatalogRow r : stored) {
            if (r.getStatus() != null && r.getStatus().intValue() == CatalogRules.STATUS_LINKED) {
                linked++;
            } else if (r.getStatus() != null && r.getStatus().intValue() == CatalogRules.STATUS_HOLD) {
                held++;
            }
        }
        receipt.setLinkedRows(linked);
        receipt.setHeldRows(held);
        receipt.setAcceptedRows(linked + held);
        receipt.setRejectedRows(0);
        receipt.setRecheckDiffs(diffs);
        receipt.setBalanced(CatalogRules.balanced(stored.size(), linked, held));
        return receipt;
    }

    /** 落库：主行先插、次行后插并回填 merged_into 主行id；行次进门已定死，不再动 */
    protected void persistLines(String batchNo, List<TItemCatalogRow> rows) {
        Map<Integer, Long> idByRowNo = new HashMap<Integer, Long>();
        for (TItemCatalogRow r : rows) {
            if (r.getMergeRowNo() != null) {
                continue;
            }
            r.setBatchNo(batchNo);
            r.setDelFlag(Integer.valueOf(DEL_FLAG_NORMAL));
            this.itemCatalogRowMapper.insert(r);
            idByRowNo.put(r.getRowNo(), r.getId());
        }
        for (TItemCatalogRow r : rows) {
            if (r.getMergeRowNo() == null) {
                continue;
            }
            r.setBatchNo(batchNo);
            r.setDelFlag(Integer.valueOf(DEL_FLAG_NORMAL));
            r.setMergedInto(idByRowNo.get(r.getMergeRowNo()));
            this.itemCatalogRowMapper.insert(r);
            TItemCatalogRow primary = findByRowNo(rows, r.getMergeRowNo().intValue());
            if (primary != null) {
                primary.setDupRowNo(r.getRowNo());
                this.itemCatalogRowMapper.updateById(primary);
            }
        }
    }

    private TItemCatalogRow findByRowNo(List<TItemCatalogRow> rows, int rowNo) {
        for (TItemCatalogRow r : rows) {
            if (r.getRowNo() != null && r.getRowNo().intValue() == rowNo && r.getMergeRowNo() == null) {
                return r;
            }
        }
        return null;
    }

    /** 历史挂起笔按期增岁；只动现行（未沉底）挂起行，满两期沉底 */
    protected int ageCurrentHolds(int periodNo) {
        List<TItemCatalogRow> held = this.itemCatalogRowMapper.selectList(new QueryWrapper<TItemCatalogRow>()
                .eq("status", CatalogRules.STATUS_HOLD)
                .eq("archived", 0)
                .eq("del_flag", DEL_FLAG_NORMAL));
        int archived = CatalogRules.ageHeldRows(periodNo, held);
        for (TItemCatalogRow r : held) {
            this.itemCatalogRowMapper.updateById(r);
        }
        return archived;
    }

    protected Set<String> loadCityItemCodes() {
        List<TItemCityItem> items = this.itemCityItemMapper.selectList(new QueryWrapper<TItemCityItem>()
                .eq("status", 0)
                .eq("del_flag", DEL_FLAG_NORMAL));
        Set<String> codes = new HashSet<String>();
        for (TItemCityItem item : items) {
            codes.add(item.getItemCode());
        }
        return codes;
    }

    /** 各事项上一册挂下未核余额：取沉底之外最近一笔的 carry_qty */
    protected Map<String, BigDecimal> loadCarryByItem(String currentBatchNo) {
        List<TItemCatalogRow> prior = this.itemCatalogRowMapper.selectList(new QueryWrapper<TItemCatalogRow>()
                .ne("batch_no", currentBatchNo)
                .eq("archived", 0)
                .eq("del_flag", DEL_FLAG_NORMAL)
                .orderByAsc("period_no", "id"));
        Map<String, BigDecimal> carry = new HashMap<String, BigDecimal>();
        for (TItemCatalogRow r : prior) {
            // 只认正余额：上一册已核清（余额0）的事项，下一季再报按新挂下处理，不受0封顶
            if (r.getItemCode() != null && r.getCarryQty() != null
                    && r.getCarryQty().compareTo(BigDecimal.ZERO) > 0) {
                carry.put(r.getItemCode(), r.getCarryQty());
            }
        }
        return carry;
    }

    /** 上一册（含同核对期早先各册）仍挂住未了结的事项；已沉底往期的不挡，来年再核不受它挡 */
    protected Set<String> loadPriorUnresolved(String currentBatchNo) {
        List<TItemCatalogRow> held = this.itemCatalogRowMapper.selectList(new QueryWrapper<TItemCatalogRow>()
                .ne("batch_no", currentBatchNo)
                .eq("status", CatalogRules.STATUS_HOLD)
                .eq("archived", 0)
                .eq("del_flag", DEL_FLAG_NORMAL));
        Set<String> codes = new HashSet<String>();
        for (TItemCatalogRow r : held) {
            if (r.getMergedInto() == null && r.getItemCode() != null) {
                codes.add(r.getItemCode());
            }
        }
        return codes;
    }

    /**
     * 期号：新递法命令里带了随册走；旧递法不带，默认沿用在册最新一期（同期前后递几册不跨期），
     * 头一册为期1。跨期（季度推进）一律走带期号的新递法。
     */
    protected int resolvePeriodNo(Integer specified) {
        if (specified != null && specified.intValue() > 0) {
            return specified.intValue();
        }
        List<TItemCatalogRow> latest = this.itemCatalogRowMapper.selectList(new QueryWrapper<TItemCatalogRow>()
                .eq("del_flag", DEL_FLAG_NORMAL)
                .orderByDesc("period_no")
                .last("limit 1"));
        if (latest.isEmpty() || latest.get(0).getPeriodNo() == null) {
            return 1;
        }
        return latest.get(0).getPeriodNo().intValue();
    }

    private static boolean equalsCode(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static boolean equalsQty(BigDecimal a, BigDecimal b) {
        if (a == null) {
            return b == null;
        }
        return b != null && a.compareTo(b) == 0;
    }

    private static CatalogReceipt newReceipt(String batchNo, Integer periodNo) {
        CatalogReceipt receipt = new CatalogReceipt();
        receipt.setBatchNo(batchNo);
        receipt.setPeriodNo(periodNo);
        return receipt;
    }
}
