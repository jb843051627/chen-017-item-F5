package com.fc.v2.controller.admin;

import java.math.BigDecimal;
import java.util.List;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TItemCatalogRow;
import com.fc.v2.model.custom.CatalogBookCommand;
import com.fc.v2.model.custom.CatalogReceipt;
import com.fc.v2.model.custom.CatalogReconcile;
import com.fc.v2.service.ITItemCatalogRowService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 上级目录下发册整册处理 Controller。
 * 一册进门到落账只有一条道：本控制器只调服务层 processBook 整册处理入口（同名两种递法），
 * 不自行判定落点、不自行数行补行；回执三数以服务层同一回计算为准。
 *
 * @author fuce
 * @date 2026-10-02
 */
@Api(value = "上级目录下发册整册处理")
@Controller
@RequestMapping("/itemCatalogBook")
public class ItemCatalogBookController extends BaseController {

    @Autowired
    private ITItemCatalogRowService itemCatalogRowService;

    @Log(title = "下发册整册进门", action = "process")
    @ApiOperation(value = "整册进门（旧递法：册次号+逐行）", notes = "默认表头与缺省校验规则")
    @PostMapping("/process")
    @RequiresPermissions("itemCatalogBook:process")
    @ResponseBody
    public AjaxResult process(@RequestParam("batchNo") String batchNo,
                              @RequestBody List<TItemCatalogRow> rows) {
        CatalogReceipt receipt = itemCatalogRowService.processBook(batchNo, rows);
        return AjaxResult.success(200, receiptMark(receipt), receipt);
    }

    @Log(title = "下发册整册进门", action = "intake")
    @ApiOperation(value = "整册进门（新递法：表头/规则随册命令）", notes = "册次号、期号、表头、缺项规则、逐行随命令来")
    @PostMapping("/intake")
    @RequiresPermissions("itemCatalogBook:process")
    @ResponseBody
    public AjaxResult intake(@RequestBody CatalogBookCommand command) {
        CatalogReceipt receipt = itemCatalogRowService.processBook(command);
        return AjaxResult.success(200, receiptMark(receipt), receipt);
    }

    @ApiOperation(value = "某册入账行", notes = "按原册行次升序")
    @GetMapping("/list")
    @RequiresPermissions("itemCatalogBook:view")
    @ResponseBody
    public AjaxResult list(@RequestParam("batchNo") String batchNo) {
        return retobject(200, itemCatalogRowService.listByBatch(batchNo));
    }

    @ApiOperation(value = "挂起层", notes = "每笔自带原册行次，不劳人翻纸页重数")
    @GetMapping("/held")
    @RequiresPermissions("itemCatalogBook:view")
    @ResponseBody
    public AjaxResult held() {
        return retobject(200, itemCatalogRowService.listHeld());
    }

    @ApiOperation(value = "往期册页", notes = "挂起满两个核对期沉底的行，事后翻得着、不挡来年")
    @GetMapping("/archived")
    @RequiresPermissions("itemCatalogBook:view")
    @ResponseBody
    public AjaxResult archived() {
        return retobject(200, itemCatalogRowService.listArchived());
    }

    @ApiOperation(value = "全辖对账", notes = "回执总数与逐行加总接不平时，当场点出差在哪几行")
    @GetMapping("/reconcile")
    @RequiresPermissions("itemCatalogBook:view")
    @ResponseBody
    public AjaxResult reconcile(@RequestParam(value = "screenTotalRows", required = false) Integer screenTotalRows) {
        CatalogReconcile result = itemCatalogRowService.reconcile(screenTotalRows);
        return retobject(200, result);
    }

    @Log(title = "下发册项数改动请签", action = "qtyChange")
    @ApiOperation(value = "项数确要改走核签单", notes = "复核不改写落地行，另起要素调整核签单")
    @PostMapping("/qtyChange")
    @RequiresPermissions("itemCatalogBook:process")
    @ResponseBody
    public AjaxResult qtyChange(@RequestParam("rowId") Long rowId,
                                @RequestParam("newQty") BigDecimal newQty,
                                @RequestParam(value = "reason", required = false) String reason) {
        return retobject(200, itemCatalogRowService.requestQtyChange(rowId, newQty, reason));
    }

    /** 回执字样：空册只回「空册」，绝不在空册上写核讫 */
    private static String receiptMark(CatalogReceipt receipt) {
        return "共" + receipt.getTotalRows() + "行，挂上" + receipt.getLinkedRows()
                + "行，挂住" + receipt.getHeldRows() + "行——" + receipt.getMark();
    }
}
