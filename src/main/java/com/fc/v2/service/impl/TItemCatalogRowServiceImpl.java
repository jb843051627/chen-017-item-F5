package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TItemCatalogRowMapper;
import com.fc.v2.mapper.auto.TItemCatalogVerMapper;
import com.fc.v2.model.auto.TItemCatalogRow;
import com.fc.v2.model.auto.TItemCatalogVer;
import com.fc.v2.model.custom.CatalogBooklet;
import com.fc.v2.model.custom.CatalogReceipt;
import com.fc.v2.model.custom.ReconcileResult;
import com.fc.v2.service.ITItemCatalogRowService;

/**
 * 上级目录下发册核对行 Service业务层处理（batch-process 形状：整册提交）。
 *
 * 一册自进门到落账只有一条道，就是本类的 process()：两种递法都汇到这一处判定、
 * 落账、给回三个数，页面上自己数的那份不作准，也没有哪只手能往里补一行。
 *
 * 判定规矩：
 *   单行不合只挂那一行，四种情形各按各的前置项代号落到挂起，缘故一句话说清：
 *   事项码不在本市名录上；待核项数那栏空着；报成零或负数；上一册那笔还没了结又来报这一册。
 *   册级三条：本行核掉的数不能大过上一册挂下来的数（多出的一截不许塞进本行，也不许去抵邻行）；
 *   同一事项码在本册占了双行，落账时并成一行（项数只记一次，行次取靠前的那一个）；
 *   同一下发册次号重递只当复核走过一遍，不改写任何一行、不再添新行。
 *   兜底两道：行数越上限整册退回、一行不留；空册回执只落「空册」，不许写核讫；
 *   挂起连着满两个核对期未落地的，挪进往期册页。
 *
 * 台账一次读全、在内存里分派：册次归属、上册未了结、往期挪动、回执三个数同出这一回数。
 * （一册几十到上百行、一季几册，这个量级一次读全绰绰有余。）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TItemCatalogRowServiceImpl implements ITItemCatalogRowService {

    /** 单册行数上限：越线整册退回，一行不留 */
    private static final int MAX_ROWS = 500;

    /** 行落地情形：0待核 1已挂接 2挂起 */
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_LINKED = 1;
    private static final int STATUS_SUSPENDED = 2;

    /** 挂起行连着满这么多个核对期仍未落地，挪进往期册页 */
    private static final int SUSPEND_PERIODS = 2;

    /** 本市名录落点：事项目录条目版次单已上柜（stage=3）且未作废的，算在名录上 */
    private static final int CATALOG_STAGE_SHELVED = 3;
    private static final int CATALOG_STATUS_VOID = 2;

    /** 挂起缘故（一句话说清） */
    private static final String REASON_CODE_ABSENT = "事项码阙如，不在本市名录上";
    private static final String REASON_CODE_NOT_IN_CATALOG = "事项码不在本市名录上";
    private static final String REASON_QTY_BLANK = "待核项数那栏空着";
    private static final String REASON_QTY_MALFORMED = "待核项数那栏写不成数";
    private static final String REASON_QTY_NON_POSITIVE = "待核项数报成零或负数";
    private static final String REASON_PRIOR_OPEN = "上一册那笔还没了结，又来报这一册";
    private static final String REASON_OVER_CARRIED = "本行核掉的数大过上一册挂下来的数";

    @Resource
    private TItemCatalogRowMapper itemCatalogRowMapper;

    @Resource
    private TItemCatalogVerMapper itemCatalogVerMapper;

    @Override
    public TItemCatalogRow selectTItemCatalogRowById(Long id) {
        return this.itemCatalogRowMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitBatch(String batchNo, List<TItemCatalogRow> rows) {
        CatalogReceipt receipt = process(batchNo, rows, CatalogBookletParser.Profile.standard());
        // 原先的递法照旧使得：整册退回返回 -1，其余情形返回挂上行数（空册自然为 0）
        return receipt.getOutcome() == CatalogReceipt.OUTCOME_RETURNED ? -1 : receipt.getAcceptedRows();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CatalogReceipt submitBatch(CatalogBooklet booklet) {
        if (booklet == null) {
            throw new IllegalArgumentException("下发册不得为空");
        }
        // 册进门先过解析：这一册有几栏、缺项按哪一套校验规则点，都随本册的表头来
        CatalogBookletParser.Parsed parsed = CatalogBookletParser.parse(booklet);
        return process(booklet.getBatchNo(), parsed.getRows(), parsed.getProfile());
    }

    /**
     * 整册处理唯一通道：两种递法都汇到这一处。某行算挂上、算挂住，还是随整册被推回，
     * 一律在此判定；册共几行、收进几行、推出几行，也在此一处给回。
     */
    private CatalogReceipt process(String batchNo, List<TItemCatalogRow> rows, CatalogBookletParser.Profile profile) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            throw new IllegalArgumentException("一册一个下发册次号，册次号不得为空");
        }
        String no = batchNo.trim();

        // 空册：一行也没有，回执只落「空册」，不许写核讫
        if (rows == null || rows.isEmpty()) {
            return emptyReceipt(no);
        }

        // 台账一次读全：册次归属、上册未了结、往期挪动、回执三个数同出这一回数
        List<TItemCatalogRow> ledger = loadLedger();

        // 同一下发册次号第二次递上来：只当复核走过一遍——逐行比对，不改写任何一行，也不再添新行
        List<TItemCatalogRow> booked = rowsOf(ledger, no);
        if (!booked.isEmpty() || ledgerHasBatch(ledger, no)) {
            return reviewPass(no, rows, booked);
        }

        // 单册行数上限：越线整册退回，一行不留
        if (rows.size() > MAX_ROWS) {
            return returnedReceipt(no, rows.size());
        }

        // 每行在本册排第几，进门这一刻照先后定死；册次号当场落章，各行归各行
        List<TItemCatalogRow> stamped = new ArrayList<TItemCatalogRow>(rows.size());
        int seq = 0;
        for (TItemCatalogRow r : rows) {
            TItemCatalogRow row = r == null ? new TItemCatalogRow() : r;
            row.setId(null);
            row.setRowNo(++seq);
            row.setBatchNo(no);
            row.setItemCode(norm(row.getItemCode()));
            row.setDelFlag(0);
            row.setCreateBy(null);
            row.setCreateTime(null);
            row.setUpdateBy(null);
            row.setUpdateTime(null);
            stamped.add(row);
        }

        // 同一事项码在本册占了双行：落账时并成一行，项数只记一次，行次取靠前的那一个
        List<TItemCatalogRow> kept = new ArrayList<TItemCatalogRow>();
        Set<String> seen = new HashSet<String>();
        int merged = 0;
        for (TItemCatalogRow row : stamped) {
            String code = row.getItemCode();
            if (code != null && !seen.add(code)) {
                merged++;
                continue;
            }
            kept.add(row);
        }

        Set<String> catalog = loadCityCatalog(kept);
        Map<String, TItemCatalogRow> priorOpen = oldestOpenByCode(ledger, kept);

        int accepted = 0;
        int suspended = 0;
        List<TItemCatalogRow> toSettle = new ArrayList<TItemCatalogRow>();
        for (TItemCatalogRow row : kept) {
            TItemCatalogRow settled = judgeOne(row, profile, catalog, priorOpen);
            if (settled != null) {
                toSettle.add(settled);
            }
            this.itemCatalogRowMapper.insert(row);
            if (row.getStatus() != null && row.getStatus() == STATUS_SUSPENDED) {
                suspended++;
            } else {
                accepted++;
            }
        }
        // 上一册挂下来的那笔，本行在挂下来的数以内核掉的，同作了结
        for (TItemCatalogRow old : toSettle) {
            this.itemCatalogRowMapper.updateById(old);
        }

        // 挂起那一层的了断：同一行连着满两个核对期仍未落地，挪进往期册页
        ledger.addAll(kept);
        archiveLongSuspended(ledger, no);

        CatalogReceipt receipt = new CatalogReceipt();
        receipt.setBatchNo(no);
        receipt.setOutcome(CatalogReceipt.OUTCOME_BOOKED);
        receipt.setRawRows(stamped.size());
        receipt.setMergedRows(merged);
        receipt.setTotalRows(kept.size());
        receipt.setAcceptedRows(accepted);
        receipt.setSuspendedRows(suspended);
        receipt.setRows(kept);
        return receipt;
    }

    /**
     * 逐行找落点：落点只有三种——待核、已挂接、挂起。单行不合只挂那一行，同册其余各行照常进账。
     * 返回被本行了结的上一册那笔（挂起转已挂接），没有了结返回 null。
     */
    private TItemCatalogRow judgeOne(TItemCatalogRow row, CatalogBookletParser.Profile profile,
                                     Set<String> catalog, Map<String, TItemCatalogRow> priorOpen) {
        String code = row.getItemCode();
        BigDecimal qty = row.getQty();

        if (code == null) {
            suspend(row, REASON_CODE_ABSENT);
            return null;
        }
        if (qty == null && profile.isQtyColumnPresent()) {
            suspend(row, profile.isQtyMalformed(row.getRowNo()) ? REASON_QTY_MALFORMED : REASON_QTY_BLANK);
            return null;
        }
        if (qty != null && qty.compareTo(BigDecimal.ZERO) <= 0) {
            suspend(row, REASON_QTY_NON_POSITIVE);
            return null;
        }
        if (!catalog.contains(code)) {
            suspend(row, REASON_CODE_NOT_IN_CATALOG);
            return null;
        }

        // 上一册那笔：没了结的里头最早那笔说了算，挂下来的数以它为准
        TItemCatalogRow prior = priorOpen.get(code);
        if (prior != null) {
            if (prior.getStatus() == null || prior.getStatus() == STATUS_PENDING
                    || prior.getQty() == null || qty == null) {
                // 那笔还没了结（或两边项数阙如、无从核销），又来报这一册
                suspend(row, REASON_PRIOR_OPEN);
                return null;
            }
            if (qty.compareTo(prior.getQty()) > 0) {
                // 挂下三十这一期最多核三十，多出的一截不许塞进本行，也不许去抵邻行
                suspend(row, REASON_OVER_CARRIED + "（" + fmtQty(prior.getQty()) + "）");
                return null;
            }
            // 在挂下来的数以内：本行落账，上一册那笔同作了结
            prior.setStatus(STATUS_LINKED);
            prior.setRemark(appendNote(prior.getRemark(), "由册" + row.getBatchNo() + "第" + row.getRowNo() + "行了结"));
            land(row, profile);
            return prior;
        }

        land(row, profile);
        return null;
    }

    /** 干净的落已挂接；带软缺项的落待核，留待人工补核 */
    private void land(TItemCatalogRow row, CatalogBookletParser.Profile profile) {
        if (profile.isSoft(row.getRowNo())) {
            row.setStatus(STATUS_PENDING);
        } else {
            row.setStatus(STATUS_LINKED);
        }
    }

    /** 挂起那一行：前置项代号照录，缘故一句话说清 */
    private void suspend(TItemCatalogRow row, String reason) {
        row.setStatus(STATUS_SUSPENDED);
        row.setRemark(reason);
    }

    /**
     * 复核走过一遍：逐行比对，不改写任何一行的落地情形，也不再添新行；
     * 项数确要改，走那笔没了的事那一路。三个数照库里逐行算。
     */
    private CatalogReceipt reviewPass(String no, List<TItemCatalogRow> rows, List<TItemCatalogRow> booked) {
        List<String> notes = new ArrayList<String>();
        Map<Integer, TItemCatalogRow> byRowNo = new HashMap<Integer, TItemCatalogRow>();
        for (TItemCatalogRow b : booked) {
            if (b.getRowNo() != null) {
                byRowNo.put(b.getRowNo(), b);
            }
        }
        int seq = 0;
        for (TItemCatalogRow r : rows) {
            seq++;
            TItemCatalogRow b = byRowNo.get(seq);
            if (b == null) {
                notes.add("第" + seq + "行：账上无此行，重报不添行");
                continue;
            }
            String newCode = r == null ? null : norm(r.getItemCode());
            if (!Objects.equals(b.getItemCode(), newCode)) {
                notes.add("第" + seq + "行：账上事项码「" + b.getItemCode() + "」，重报「" + newCode + "」，以账上为准");
                continue;
            }
            BigDecimal newQty = r == null ? null : r.getQty();
            if (newQty != null && b.getQty() != null && newQty.compareTo(b.getQty()) != 0) {
                notes.add("第" + seq + "行：账上项数" + fmtQty(b.getQty()) + "，重报" + fmtQty(newQty)
                        + "；项数确要改，走那笔没了的事那一路");
            } else if (newQty == null && b.getQty() != null) {
                notes.add("第" + seq + "行：重报项数空着，账上项数" + fmtQty(b.getQty()) + "，以账上为准");
            } else if (newQty != null && b.getQty() == null) {
                notes.add("第" + seq + "行：账上项数阙如，重报" + fmtQty(newQty) + "，以账上为准");
            }
        }
        if (booked.size() > rows.size()) {
            notes.add("账上第" + (rows.size() + 1) + "行起共" + (booked.size() - rows.size()) + "行，重报册上未见，以账上为准");
        }
        if (booked.isEmpty()) {
            notes.add("该册现行账上无行（已挪往期册页），复核不改写、不添行");
        }

        CatalogReceipt receipt = new CatalogReceipt();
        receipt.setBatchNo(no);
        receipt.setOutcome(CatalogReceipt.OUTCOME_REVIEW);
        receipt.setRawRows(rows.size());
        receipt.setTotalRows(booked.size());
        receipt.setAcceptedRows(countByLanding(booked, true));
        receipt.setSuspendedRows(countByLanding(booked, false));
        receipt.setRows(booked);
        receipt.setNotes(notes);
        return receipt;
    }

    /**
     * 挂起那一层的了断：同一行连着满两个核对期仍未落地，挪进往期册页（del_flag=1），
     * 现行这册不再摆它；事后翻得着，来年再核也不受它挡。
     */
    private void archiveLongSuspended(List<TItemCatalogRow> ledger, String currentBatchNo) {
        // 册次先后照各册首行落账先后（id 小的先进门）；已挪往期的行仍在台账里，册次依旧数得着
        final Map<String, Long> firstSeen = new HashMap<String, Long>();
        for (TItemCatalogRow r : ledger) {
            if (r.getBatchNo() == null || r.getId() == null) {
                continue;
            }
            Long cur = firstSeen.get(r.getBatchNo());
            if (cur == null || r.getId() < cur) {
                firstSeen.put(r.getBatchNo(), r.getId());
            }
        }
        List<String> order = new ArrayList<String>(firstSeen.keySet());
        Collections.sort(order, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return firstSeen.get(a).compareTo(firstSeen.get(b));
            }
        });
        Map<String, Integer> batchIndex = new HashMap<String, Integer>();
        for (int i = 0; i < order.size(); i++) {
            batchIndex.put(order.get(i), i);
        }
        Integer currentIdx = batchIndex.get(currentBatchNo);
        if (currentIdx == null) {
            return;
        }
        for (TItemCatalogRow r : ledger) {
            if (r.getId() == null || r.getStatus() == null || r.getStatus() != STATUS_SUSPENDED) {
                continue;
            }
            if (r.getDelFlag() != null && r.getDelFlag() == 1) {
                continue;
            }
            if (currentBatchNo.equals(r.getBatchNo())) {
                continue;
            }
            Integer homeIdx = batchIndex.get(r.getBatchNo());
            if (homeIdx == null) {
                continue;
            }
            if (currentIdx - homeIdx >= SUSPEND_PERIODS) {
                r.setDelFlag(1);
                r.setRemark(appendNote(r.getRemark(), "满两个核对期未落地，挪入往期册页"));
                this.itemCatalogRowMapper.updateById(r);
            }
        }
    }

    @Override
    public CatalogReceipt receipt(String batchNo) {
        // 回执重算：与落账那回同一口径，从库里逐行加出来，界面自己数出来的不作数
        if (batchNo == null || batchNo.trim().isEmpty()) {
            throw new IllegalArgumentException("一册一个下发册次号，册次号不得为空");
        }
        String no = batchNo.trim();
        List<TItemCatalogRow> booked = rowsOf(loadLedger(), no);
        if (booked.isEmpty()) {
            // 整册退回的册一行不留，重算只见空册
            return emptyReceipt(no);
        }
        CatalogReceipt receipt = new CatalogReceipt();
        receipt.setBatchNo(no);
        receipt.setOutcome(CatalogReceipt.OUTCOME_BOOKED);
        receipt.setRawRows(booked.size());
        receipt.setTotalRows(booked.size());
        receipt.setAcceptedRows(countByLanding(booked, true));
        receipt.setSuspendedRows(countByLanding(booked, false));
        receipt.setRows(booked);
        return receipt;
    }

    @Override
    public List<TItemCatalogRow> listErrors(String batchNo) {
        // 挂起层明细：按行号升序，屏上直接带出行序号
        List<TItemCatalogRow> list = new ArrayList<TItemCatalogRow>();
        String no = batchNo == null ? null : batchNo.trim();
        for (TItemCatalogRow r : rowsOf(loadLedger(), no)) {
            if (r.getStatus() != null && r.getStatus() == STATUS_SUSPENDED) {
                list.add(r);
            }
        }
        return list;
    }

    @Override
    public List<TItemCatalogRow> listArchived() {
        // 往期册页：挪出去的行还在库里，事后翻得着
        List<TItemCatalogRow> list = new ArrayList<TItemCatalogRow>();
        for (TItemCatalogRow r : loadLedger()) {
            if (r.getDelFlag() != null && r.getDelFlag() == 1) {
                list.add(r);
            }
        }
        sortByBatchAndRow(list);
        return list;
    }

    @Override
    public ReconcileResult reconcile(String batchNo, Integer claimedTotal, Integer claimedAccepted, Integer claimedSuspended) {
        // 全辖对账：屏上自称的三个数与库里逐行加出的数对一遍，接不平的差在哪几行当场点出来
        String no = batchNo == null || batchNo.trim().isEmpty() ? null : batchNo.trim();
        List<TItemCatalogRow> scope = new ArrayList<TItemCatalogRow>();
        for (TItemCatalogRow r : loadLedger()) {
            if (r.getDelFlag() != null && r.getDelFlag() == 1) {
                continue;
            }
            if (no != null && !no.equals(r.getBatchNo())) {
                continue;
            }
            scope.add(r);
        }
        sortByBatchAndRow(scope);

        List<TItemCatalogRow> accepted = new ArrayList<TItemCatalogRow>();
        List<TItemCatalogRow> suspended = new ArrayList<TItemCatalogRow>();
        for (TItemCatalogRow r : scope) {
            if (r.getStatus() != null && r.getStatus() == STATUS_SUSPENDED) {
                suspended.add(r);
            } else {
                accepted.add(r);
            }
        }

        ReconcileResult result = new ReconcileResult();
        result.setBatchNo(no);
        result.setClaimedTotal(claimedTotal);
        result.setClaimedAccepted(claimedAccepted);
        result.setClaimedSuspended(claimedSuspended);
        result.setActualTotal(scope.size());
        result.setActualAccepted(accepted.size());
        result.setActualSuspended(suspended.size());
        result.setAcceptedRows(accepted);
        result.setSuspendedRows(suspended);
        result.setBalanced(claimedTotal != null && claimedAccepted != null && claimedSuspended != null
                && claimedTotal == scope.size() && claimedAccepted == accepted.size()
                && claimedSuspended == suspended.size());
        return result;
    }

    /** 台账一次读全（含已挪往期的行），后头分派都出这一回数 */
    private List<TItemCatalogRow> loadLedger() {
        List<TItemCatalogRow> all = this.itemCatalogRowMapper.selectList(new QueryWrapper<TItemCatalogRow>());
        return all != null ? all : new ArrayList<TItemCatalogRow>();
    }

    /** 该册现行在账的行（按行号升序） */
    private List<TItemCatalogRow> rowsOf(List<TItemCatalogRow> ledger, String batchNo) {
        List<TItemCatalogRow> list = new ArrayList<TItemCatalogRow>();
        for (TItemCatalogRow r : ledger) {
            if (Objects.equals(batchNo, r.getBatchNo()) && (r.getDelFlag() == null || r.getDelFlag() == 0)) {
                list.add(r);
            }
        }
        Collections.sort(list, new Comparator<TItemCatalogRow>() {
            @Override
            public int compare(TItemCatalogRow a, TItemCatalogRow b) {
                int ra = a.getRowNo() == null ? Integer.MAX_VALUE : a.getRowNo();
                int rb = b.getRowNo() == null ? Integer.MAX_VALUE : b.getRowNo();
                return ra - rb;
            }
        });
        return list;
    }

    /** 该册次号是否递过（哪怕行已挪往期，也算递过：一册一个下发册次号） */
    private boolean ledgerHasBatch(List<TItemCatalogRow> ledger, String batchNo) {
        for (TItemCatalogRow r : ledger) {
            if (Objects.equals(batchNo, r.getBatchNo())) {
                return true;
            }
        }
        return false;
    }

    /** 本市名录：事项目录条目版次单已上柜且未作废的，算在名录上 */
    private Set<String> loadCityCatalog(List<TItemCatalogRow> kept) {
        Set<String> codes = new HashSet<String>();
        for (TItemCatalogRow r : kept) {
            if (r.getItemCode() != null) {
                codes.add(r.getItemCode());
            }
        }
        Set<String> catalog = new HashSet<String>();
        if (codes.isEmpty()) {
            return catalog;
        }
        List<TItemCatalogVer> vers = this.itemCatalogVerMapper.selectList(new QueryWrapper<TItemCatalogVer>()
                .select("biz_no")
                .in("biz_no", codes)
                .eq("stage", CATALOG_STAGE_SHELVED)
                .eq("del_flag", 0)
                .and(w -> w.isNull("status").or().ne("status", CATALOG_STATUS_VOID)));
        if (vers != null) {
            for (TItemCatalogVer v : vers) {
                String no = norm(v.getBizNo());
                if (no != null) {
                    catalog.add(no);
                }
            }
        }
        return catalog;
    }

    /** 同一事项码往前数，没了结的（待核/挂起，现行在账）里头最早那笔 */
    private Map<String, TItemCatalogRow> oldestOpenByCode(List<TItemCatalogRow> ledger, List<TItemCatalogRow> kept) {
        Set<String> codes = new HashSet<String>();
        for (TItemCatalogRow r : kept) {
            if (r.getItemCode() != null) {
                codes.add(r.getItemCode());
            }
        }
        Map<String, TItemCatalogRow> map = new HashMap<String, TItemCatalogRow>();
        if (codes.isEmpty()) {
            return map;
        }
        for (TItemCatalogRow r : ledger) {
            if (r.getDelFlag() != null && r.getDelFlag() == 1) {
                continue;
            }
            if (r.getStatus() == null || (r.getStatus() != STATUS_PENDING && r.getStatus() != STATUS_SUSPENDED)) {
                continue;
            }
            String code = norm(r.getItemCode());
            if (code == null || !codes.contains(code)) {
                continue;
            }
            TItemCatalogRow cur = map.get(code);
            if (cur == null || (r.getId() != null && cur.getId() != null && r.getId() < cur.getId())) {
                map.put(code, r);
            }
        }
        return map;
    }

    /** 空册回执：只落「空册」两字，不许写核讫 */
    private CatalogReceipt emptyReceipt(String no) {
        CatalogReceipt receipt = new CatalogReceipt();
        receipt.setBatchNo(no);
        receipt.setOutcome(CatalogReceipt.OUTCOME_EMPTY);
        return receipt;
    }

    /** 整册退回回执：越过单册行数上限，一行不留 */
    private CatalogReceipt returnedReceipt(String no, int rawRows) {
        CatalogReceipt receipt = new CatalogReceipt();
        receipt.setBatchNo(no);
        receipt.setOutcome(CatalogReceipt.OUTCOME_RETURNED);
        receipt.setRawRows(rawRows);
        return receipt;
    }

    /** 挂上（待核＋已挂接）或挂住（挂起）的行数——挂住的加挂上的等于本册行数 */
    private int countByLanding(List<TItemCatalogRow> rows, boolean acceptedSide) {
        int n = 0;
        for (TItemCatalogRow r : rows) {
            boolean suspended = r.getStatus() != null && r.getStatus() == STATUS_SUSPENDED;
            if (suspended != acceptedSide) {
                n++;
            }
        }
        return n;
    }

    private void sortByBatchAndRow(List<TItemCatalogRow> list) {
        Collections.sort(list, new Comparator<TItemCatalogRow>() {
            @Override
            public int compare(TItemCatalogRow a, TItemCatalogRow b) {
                String ba = a.getBatchNo() == null ? "" : a.getBatchNo();
                String bb = b.getBatchNo() == null ? "" : b.getBatchNo();
                int c = ba.compareTo(bb);
                if (c != 0) {
                    return c;
                }
                int ra = a.getRowNo() == null ? Integer.MAX_VALUE : a.getRowNo();
                int rb = b.getRowNo() == null ? Integer.MAX_VALUE : b.getRowNo();
                return ra - rb;
            }
        });
    }

    private static String norm(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String fmtQty(BigDecimal qty) {
        return qty == null ? "空" : qty.stripTrailingZeros().toPlainString();
    }

    private static String appendNote(String old, String note) {
        return old == null || old.trim().isEmpty() ? note : old + "；" + note;
    }
}
