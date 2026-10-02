package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.mapper.auto.TItemCatalogRowMapper;
import com.fc.v2.mapper.auto.TItemCatalogVerMapper;
import com.fc.v2.model.auto.TItemCatalogRow;
import com.fc.v2.model.auto.TItemCatalogVer;
import com.fc.v2.model.custom.CatalogBooklet;
import com.fc.v2.model.custom.CatalogReceipt;
import com.fc.v2.model.custom.ReconcileResult;

/**
 * 整册处理入口单元测试：内存假库，不连真库。
 */
@ExtendWith(MockitoExtension.class)
class TItemCatalogRowServiceImplTest {

    @Mock
    private TItemCatalogRowMapper rowMapper;

    @Mock
    private TItemCatalogVerMapper verMapper;

    @InjectMocks
    private TItemCatalogRowServiceImpl service;

    /** 内存假库：insert 落这里，selectList 从这里读 */
    private List<TItemCatalogRow> db;

    /** 本市名录（已上柜的版次单号） */
    private Set<String> catalog;

    private long idSeq;

    @BeforeEach
    void setUp() {
        db = new ArrayList<TItemCatalogRow>();
        catalog = new HashSet<String>();
        idSeq = 0L;
        lenient().when(rowMapper.selectList(any())).thenAnswer(inv -> new ArrayList<TItemCatalogRow>(db));
        lenient().when(rowMapper.insert(any(TItemCatalogRow.class))).thenAnswer(inv -> {
            TItemCatalogRow r = inv.getArgument(0);
            r.setId(++idSeq);
            db.add(r);
            return 1;
        });
        lenient().when(rowMapper.updateById(any(TItemCatalogRow.class))).thenAnswer(inv -> 1);
        lenient().when(verMapper.selectList(any())).thenAnswer(inv -> {
            List<TItemCatalogVer> vers = new ArrayList<TItemCatalogVer>();
            for (String code : catalog) {
                TItemCatalogVer v = new TItemCatalogVer();
                v.setBizNo(code);
                v.setStage(3);
                v.setStatus(1);
                v.setDelFlag(0);
                vers.add(v);
            }
            return vers;
        });
    }

    private static TItemCatalogRow row(String code, String qty) {
        TItemCatalogRow r = new TItemCatalogRow();
        r.setItemCode(code);
        if (qty != null) {
            r.setQty(new BigDecimal(qty));
        }
        return r;
    }

    private TItemCatalogRow dbRow(String batchNo, int rowNo) {
        for (TItemCatalogRow r : db) {
            if (batchNo.equals(r.getBatchNo()) && r.getRowNo() != null && r.getRowNo() == rowNo) {
                return r;
            }
        }
        return null;
    }

    // ---------- 兜底：空册 ----------

    @Test
    void emptyBooklet_receiptSaysKongCe_neverHeQi() {
        int n = service.submitBatch("B1", new ArrayList<TItemCatalogRow>());
        assertEquals(0, n);

        CatalogReceipt receipt = service.submitBatch(new CatalogBooklet("B2", "前置项代号,待核项数", new ArrayList<String>()));
        assertEquals(CatalogReceipt.OUTCOME_EMPTY, receipt.getOutcome());
        assertEquals("空册", receipt.getOutcomeText());
        assertEquals(0, receipt.getTotalRows());
        assertEquals(0, receipt.getAcceptedRows());
        assertEquals(0, receipt.getSuspendedRows());
        verify(rowMapper, never()).insert(any(TItemCatalogRow.class));
    }

    // ---------- 兜底：行数越上限整册退回 ----------

    @Test
    void overLimit_wholeBookletReturned_nothingBooked() {
        List<TItemCatalogRow> rows = new ArrayList<TItemCatalogRow>();
        List<String> lines = new ArrayList<String>();
        for (int i = 0; i < 501; i++) {
            rows.add(row("A" + i, "1"));
            lines.add("A" + i + ",1");
        }
        assertEquals(-1, service.submitBatch("B1", rows));
        assertEquals(0, db.size());

        CatalogReceipt receipt = service.submitBatch(new CatalogBooklet("B2", "前置项代号,待核项数", lines));
        assertEquals(CatalogReceipt.OUTCOME_RETURNED, receipt.getOutcome());
        assertEquals("整册退回", receipt.getOutcomeText());
        assertEquals(501, receipt.getRawRows());
        assertEquals(0, db.size());
    }

    // ---------- 单行不合只挂那一行，四种情形各记缘故 ----------

    @Test
    void mixedRows_eachRowJudgedOnItsOwn_restBooked() {
        catalog.add("A01");
        catalog.add("A04");
        catalog.add("A05");
        List<TItemCatalogRow> rows = Arrays.asList(
                row("A01", "10"),   // 干净 → 已挂接
                row("A02", "5"),    // 不在本市名录 → 挂起
                row("A03", null),   // 待核项数空着 → 挂起
                row("A04", "0"),    // 报成零 → 挂起
                row("A05", "-2"));  // 报成负数 → 挂起
        int accepted = service.submitBatch("B1", rows);

        assertEquals(1, accepted);
        assertEquals(5, db.size());
        assertEquals(1, (int) dbRow("B1", 1).getStatus());
        assertEquals(2, (int) dbRow("B1", 2).getStatus());
        assertEquals(2, (int) dbRow("B1", 3).getStatus());
        assertEquals(2, (int) dbRow("B1", 4).getStatus());
        assertEquals(2, (int) dbRow("B1", 5).getStatus());
        assertTrue(dbRow("B1", 2).getRemark().contains("事项码不在本市名录上"));
        assertTrue(dbRow("B1", 3).getRemark().contains("待核项数那栏空着"));
        assertTrue(dbRow("B1", 4).getRemark().contains("零或负"));
        assertTrue(dbRow("B1", 5).getRemark().contains("零或负"));
        // 册次号当场落章，各行归各行
        for (TItemCatalogRow r : db) {
            assertEquals("B1", r.getBatchNo());
        }
    }

    // ---------- 行序号进门那一刻照先后定死 ----------

    @Test
    void rowNumbersFixedAtEntry_bySubmissionOrder() {
        catalog.add("A01");
        catalog.add("A02");
        TItemCatalogRow r1 = row("A01", "1");
        r1.setRowNo(99);
        TItemCatalogRow r2 = row("A02", "2");
        r2.setRowNo(98);
        service.submitBatch("B1", Arrays.asList(r1, r2));

        assertEquals(1, (int) dbRow("B1", 1).getRowNo());
        assertEquals("A01", dbRow("B1", 1).getItemCode());
        assertEquals("A02", dbRow("B1", 2).getItemCode());
        assertNull(dbRow("B1", 99));
    }

    // ---------- 册级二：同一事项码占双行，并成一行 ----------

    @Test
    void duplicateItemCode_mergedKeepingEarlierRow() {
        catalog.add("A01");
        catalog.add("A02");
        CatalogReceipt receipt = service.submitBatch(new CatalogBooklet("B1", "前置项代号,待核项数",
                Arrays.asList("A01,10", "A02,5", "A01,20")));

        assertEquals(3, receipt.getRawRows());
        assertEquals(1, receipt.getMergedRows());
        assertEquals(2, receipt.getTotalRows());
        assertEquals(2, db.size());
        // 项数只记一次，行次取靠前的那一个
        assertEquals(new BigDecimal("10"), dbRow("B1", 1).getQty());
        assertEquals("A01", dbRow("B1", 1).getItemCode());
        assertEquals("A02", dbRow("B1", 2).getItemCode());
    }

    // ---------- 册级三：同册次号重递只当复核 ----------

    @Test
    void resubmission_reviewPassOnly_noRewriteNoNewRows() {
        catalog.add("A01");
        int first = service.submitBatch("B1", Arrays.asList(row("A01", "10"), row("A02", "5")));
        assertEquals(1, first);
        assertEquals(2, db.size());

        // 第二次递上来：不改写、不添行，返回照库里逐行算出的挂上行数
        int again = service.submitBatch("B1", Arrays.asList(row("A01", "99"), row("A02", "5")));
        assertEquals(1, again);
        assertEquals(2, db.size());
        verify(rowMapper, times(2)).insert(any(TItemCatalogRow.class));
        // 项数确要改也不在此改：账上还是 10
        assertEquals(new BigDecimal("10"), dbRow("B1", 1).getQty());

        CatalogReceipt receipt = service.submitBatch(new CatalogBooklet("B1", "前置项代号,待核项数",
                Arrays.asList("A01,99", "A02,5")));
        assertEquals(CatalogReceipt.OUTCOME_REVIEW, receipt.getOutcome());
        assertEquals("复核", receipt.getOutcomeText());
        assertEquals(2, receipt.getTotalRows());
        assertEquals(1, receipt.getAcceptedRows());
        assertEquals(1, receipt.getSuspendedRows());
        assertFalse(receipt.getNotes().isEmpty());
        assertTrue(receipt.getNotes().get(0).contains("项数确要改"));
        assertEquals(2, db.size());
    }

    // ---------- 挂起情形四：上一册那笔还没了结又来报这一册 ----------

    @Test
    void priorUnsettled_rereportSuspended() {
        // 上一册留下一笔待核（乙套软缺项落待核）
        catalog.add("A01");
        service.submitBatch(new CatalogBooklet("B1", "前置项代号", Arrays.asList("A01")));
        assertEquals(0, (int) dbRow("B1", 1).getStatus());

        // 这一册又报同一事项码 → 只挂那一行
        int accepted = service.submitBatch("B2", Arrays.asList(row("A01", "5")));
        assertEquals(0, accepted);
        assertEquals(2, (int) dbRow("B2", 1).getStatus());
        assertTrue(dbRow("B2", 1).getRemark().contains("上一册那笔还没了结"));
    }

    // ---------- 册级一：核掉的数不能大过上一册挂下来的数 ----------

    @Test
    void carriedDownCapsSettlement_excessNotBookedAnywhere() {
        // 上一册 A01 报 30，不在名录被挂起（挂下 30）
        service.submitBatch("B1", Arrays.asList(row("A01", "30")));
        assertEquals(2, (int) dbRow("B1", 1).getStatus());

        catalog.add("A01");
        // 这一册报 35：多出的一截不许塞进本行，也不许去抵邻行 → 只挂那一行
        int over = service.submitBatch("B2", Arrays.asList(row("A01", "35"), row("A02", "1")));
        assertEquals(0, over);
        assertEquals(2, (int) dbRow("B2", 1).getStatus());
        assertTrue(dbRow("B2", 1).getRemark().contains("大过上一册挂下来的数"));
        // 邻行 A02 不在名录照常挂起，不许拿多出的一截去抵
        assertEquals(2, (int) dbRow("B2", 2).getStatus());

        // 再一册报 30：在挂下来的数以内 → 本行落账，上一册那笔同作了结
        int ok = service.submitBatch("B3", Arrays.asList(row("A01", "30")));
        assertEquals(1, ok);
        assertEquals(1, (int) dbRow("B3", 1).getStatus());
        assertEquals(1, (int) dbRow("B1", 1).getStatus());
        assertTrue(dbRow("B1", 1).getRemark().contains("了结"));
    }

    // ---------- 兜底：挂起满两个核对期未落地，挪进往期册页 ----------

    @Test
    void suspendedTwoPeriods_movedToPastPages() {
        // 名录空着，三册各挂起一行
        service.submitBatch("B1", Arrays.asList(row("A01", "10")));
        service.submitBatch("B2", Arrays.asList(row("A02", "10")));
        // 第二册落定时，B1 那笔才满一个核对期，不挪
        assertEquals(0, (int) dbRow("B1", 1).getDelFlag());

        service.submitBatch("B3", Arrays.asList(row("A03", "10")));
        // 第三册落定，B1 那笔连着满两个核对期未落地 → 挪进往期册页
        assertEquals(1, (int) dbRow("B1", 1).getDelFlag());
        assertTrue(dbRow("B1", 1).getRemark().contains("挪入往期册页"));
        // B2 那笔才满一个核对期，B3 那笔是本册，都留在现行
        assertEquals(0, (int) dbRow("B2", 1).getDelFlag());
        assertEquals(0, (int) dbRow("B3", 1).getDelFlag());

        // 事后翻得着
        List<TItemCatalogRow> archived = service.listArchived();
        assertEquals(1, archived.size());
        assertEquals("A01", archived.get(0).getItemCode());
        // 现行挂起层不再摆它
        assertEquals(0, service.listErrors("B1").size());
        assertEquals(1, service.listErrors("B2").size());
    }

    // ---------- 回执三个数与库里逐行算出来的出自同一回计算 ----------

    @Test
    void receiptNumbers_sameComputationAsLedger() {
        catalog.add("A01");
        catalog.add("A03");
        CatalogReceipt receipt = service.submitBatch(new CatalogBooklet("B1", "前置项代号,待核项数",
                Arrays.asList("A01,10", "A02,5", "A03,3")));

        assertEquals(3, receipt.getTotalRows());
        assertEquals(2, receipt.getAcceptedRows());
        assertEquals(1, receipt.getSuspendedRows());
        // 挂住的加挂上的等于本册行数
        assertEquals(receipt.getTotalRows(), receipt.getAcceptedRows() + receipt.getSuspendedRows());

        // 回执重算与落账那回同一口径
        CatalogReceipt again = service.receipt("B1");
        assertEquals(receipt.getTotalRows(), again.getTotalRows());
        assertEquals(receipt.getAcceptedRows(), again.getAcceptedRows());
        assertEquals(receipt.getSuspendedRows(), again.getSuspendedRows());
        // 挂起那笔屏上直接带出行序号
        assertEquals(1, service.listErrors("B1").size());
        assertEquals(2, (int) service.listErrors("B1").get(0).getRowNo());
    }

    // ---------- 解析随本册表头：几栏、缺项按哪一套点 ----------

    @Test
    void parsing_followsBookletHeader() {
        catalog.add("A01");
        catalog.add("A02");
        catalog.add("A03");
        catalog.add("A04");
        // 甲套：表头立了待核项数栏，空着/写不成数落挂起；非要害栏短落落待核
        CatalogReceipt r1 = service.submitBatch(new CatalogBooklet("B1", "前置项代号,待核项数,备注",
                Arrays.asList("A01,10,x", "A02,20", "A03,", "A04,abc")));
        assertEquals(1, (int) dbRow("B1", 1).getStatus());
        assertEquals(0, (int) dbRow("B1", 2).getStatus()); // 短落非要害栏 → 待核
        assertEquals(2, (int) dbRow("B1", 3).getStatus()); // 那栏空着 → 挂起
        assertEquals(2, (int) dbRow("B1", 4).getStatus()); // 写不成数 → 挂起
        assertTrue(dbRow("B1", 4).getRemark().contains("写不成数"));
        assertEquals(2, r1.getAcceptedRows());
        assertEquals(2, r1.getSuspendedRows());

        // 乙套：表头没立待核项数栏，整栏阙如不硬点，落待核留待人工补核
        // （A01 在上一册已挂接、已了结，不挡这一册）
        service.submitBatch(new CatalogBooklet("B2", "前置项代号", Arrays.asList("A01")));
        assertEquals(0, (int) dbRow("B2", 1).getStatus());
        assertTrue(dbRow("B2", 1).getRemark().contains("乙套"));
    }

    // ---------- 全辖对账：接不平差在哪几行当场点出来 ----------

    @Test
    void reconcile_unbalanced_pointsOutDifferingRows() {
        catalog.add("A01");
        service.submitBatch("B1", Arrays.asList(row("A01", "10"), row("A02", "5")));

        ReconcileResult balanced = service.reconcile("B1", 2, 1, 1);
        assertTrue(balanced.isBalanced());

        // 屏上自称挂上 2 行，库里只挂上 1 行：接不平，差在哪几行当场点出来
        ReconcileResult off = service.reconcile("B1", 2, 2, 0);
        assertFalse(off.isBalanced());
        assertEquals(1, off.getActualAccepted());
        assertEquals(1, off.getActualSuspended());
        assertEquals(1, off.getSuspendedRows().size());
        assertEquals("A02", off.getSuspendedRows().get(0).getItemCode());
        assertEquals(2, (int) off.getSuspendedRows().get(0).getRowNo());
    }

    // ---------- 一册一个下发册次号 ----------

    @Test
    void blankBatchNo_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.submitBatch("  ", Arrays.asList(row("A01", "1"))));
        assertThrows(IllegalArgumentException.class,
                () -> service.submitBatch(new CatalogBooklet(null, "前置项代号", Arrays.asList("A01"))));
    }
}
