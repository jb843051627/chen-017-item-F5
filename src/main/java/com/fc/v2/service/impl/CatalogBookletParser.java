package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.fc.v2.model.auto.TItemCatalogRow;
import com.fc.v2.model.custom.CatalogBooklet;

/**
 * 下发册解析：册进门先过解析——这一册有几栏、缺项按哪一套校验规则点，都随本册的表头来。
 *
 * 表头以逗号/竖线/制表符分栏，栏名照抄册上字样。认得的要害栏两栏：
 * 「前置项代号」（事项码）与「待核项数」（项数），兼认册上常见别写。
 *
 * 缺项校验两套规则，随表头定：
 *   甲套（表头立了「待核项数」栏）：那栏空着或写不成数的，由核心判挂起；
 *   乙套（表头没立「待核项数」栏）：项数整栏阙如，不硬点，记软缺项落待核，留待人工补核。
 *
 * 栏数照表头定死：行里非要害栏短落、或栏目溢出，都记软缺项落待核；
 * 要害栏（事项码、已立的项数栏）短落不在此列，由核心按硬情形判。
 * 空行不算一行；行序号由核心在进门那一刻照先后统一编定。
 *
 * @author fuce
 * @date 2026-10-02
 */
public final class CatalogBookletParser {

    /** 要害栏名：前置项代号（兼认别写） */
    private static final String[] ITEM_CODE_HEADS = {"前置项代号", "事项码", "事项代号"};
    /** 要害栏名：待核项数（兼认别写） */
    private static final String[] QTY_HEADS = {"待核项数", "项数"};

    /** 分栏符：半角/全角逗号、竖线、制表符 */
    private static final Pattern DELIM = Pattern.compile("[,，|｜\\t]");

    private CatalogBookletParser() {
    }

    /**
     * 解析产物：行对象一串（照原始行先后）＋本册解析口径（栏数、缺项规则套、软缺项行）。
     */
    public static final class Parsed {
        private final List<TItemCatalogRow> rows;
        private final Profile profile;

        private Parsed(List<TItemCatalogRow> rows, Profile profile) {
            this.rows = rows;
            this.profile = profile;
        }

        public List<TItemCatalogRow> getRows() {
            return rows;
        }

        public Profile getProfile() {
            return profile;
        }
    }

    /**
     * 本册解析口径：栏数、缺项按哪一套点、哪些行带软缺项。
     * 原先的递法（行已解析好）用 standard()：待核项数栏视作已立，无软缺项。
     */
    public static final class Profile {
        private final int columnCount;
        private final boolean qtyColumnPresent;
        private final Set<Integer> softRowNos;
        private final Set<Integer> malformedQtyRowNos;

        private Profile(int columnCount, boolean qtyColumnPresent,
                        Set<Integer> softRowNos, Set<Integer> malformedQtyRowNos) {
            this.columnCount = columnCount;
            this.qtyColumnPresent = qtyColumnPresent;
            this.softRowNos = softRowNos;
            this.malformedQtyRowNos = malformedQtyRowNos;
        }

        /** 原先的递法用的口径：待核项数栏视作已立（空着即硬情形），无软缺项 */
        public static Profile standard() {
            return new Profile(0, true, Collections.<Integer>emptySet(), Collections.<Integer>emptySet());
        }

        public int getColumnCount() {
            return columnCount;
        }

        /** 待核项数栏是否已立：未立则缺项按乙套，不硬点 */
        public boolean isQtyColumnPresent() {
            return qtyColumnPresent;
        }

        /** 该行是否带软缺项（落待核，留待人工补核） */
        public boolean isSoft(int rowNo) {
            return softRowNos.contains(rowNo);
        }

        /** 该行待核项数是否写不成数（挂起缘故的另一句话） */
        public boolean isQtyMalformed(int rowNo) {
            return malformedQtyRowNos.contains(rowNo);
        }
    }

    /**
     * 随本册表头过解析：表头定栏数与缺项规则套，原始行照先后出行对象。
     */
    public static Parsed parse(CatalogBooklet booklet) {
        String header = booklet.getHeader();
        String[] heads;
        if (header == null || header.trim().isEmpty()) {
            heads = new String[0];
        } else {
            heads = DELIM.split(header, -1);
            for (int i = 0; i < heads.length; i++) {
                heads[i] = heads[i].trim();
            }
        }
        int columnCount = heads.length;
        int codeIdx = indexOfAny(heads, ITEM_CODE_HEADS);
        int qtyIdx = indexOfAny(heads, QTY_HEADS);
        boolean qtyColumnPresent = qtyIdx >= 0;

        List<TItemCatalogRow> rows = new ArrayList<TItemCatalogRow>();
        Set<Integer> softRowNos = new HashSet<Integer>();
        Set<Integer> malformedQtyRowNos = new HashSet<Integer>();

        List<String> lines = booklet.getLines();
        int seq = 0;
        if (lines != null) {
            for (String line : lines) {
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }
                seq++;
                String[] cells = DELIM.split(line, -1);
                for (int i = 0; i < cells.length; i++) {
                    cells[i] = cells[i].trim();
                }

                TItemCatalogRow row = new TItemCatalogRow();

                // 前置项代号：栏未立或本行短落，都按阙如论，由核心判
                String code = codeIdx >= 0 && codeIdx < cells.length ? cells[codeIdx] : null;
                row.setItemCode(code == null || code.isEmpty() ? null : code);

                // 待核项数：栏已立才取；空着留空、写不成数记号，都由核心判
                if (qtyColumnPresent) {
                    String q = qtyIdx < cells.length ? cells[qtyIdx] : null;
                    if (q == null || q.isEmpty()) {
                        row.setQty(null);
                    } else {
                        try {
                            row.setQty(new BigDecimal(q));
                        } catch (NumberFormatException e) {
                            row.setQty(null);
                            malformedQtyRowNos.add(seq);
                        }
                    }
                }

                // 软缺项：本册未立项数栏（乙套），或栏数对不上而要害栏都在
                String softNote = null;
                if (!qtyColumnPresent) {
                    softNote = "本册未立待核项数栏，缺项按乙套留待人工补核";
                } else if (cells.length != columnCount) {
                    boolean keyMissing = (codeIdx >= 0 && codeIdx >= cells.length)
                            || qtyIdx >= cells.length;
                    if (!keyMissing) {
                        softNote = cells.length < columnCount
                                ? "册定" + columnCount + "栏，本行只" + cells.length + "栏，阙如栏目留待人工补核"
                                : "册定" + columnCount + "栏，本行多出" + (cells.length - columnCount) + "栏，多出部分不入账，留待人工补核";
                    }
                }
                if (softNote != null) {
                    softRowNos.add(seq);
                    row.setRemark(softNote);
                }
                rows.add(row);
            }
        }
        return new Parsed(rows, new Profile(columnCount, qtyColumnPresent, softRowNos, malformedQtyRowNos));
    }

    private static int indexOfAny(String[] heads, String[] names) {
        for (int i = 0; i < heads.length; i++) {
            for (String name : names) {
                if (name.equals(heads[i])) {
                    return i;
                }
            }
        }
        return -1;
    }
}
