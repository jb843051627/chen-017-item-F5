package com.fc.v2.controller.admin;

import java.util.List;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TItemCatalogRow;
import com.fc.v2.model.custom.CatalogBooklet;
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
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 上级目录下发册核对 Controller（batch-process 形状：整册递入，回执、挂起层、往期册页、全辖对账）
 *
 * 屏上数目一律从服务层取：册共几行、收进几行、推出几行都在整册处理入口一处给回，
 * 界面自己数出来的不作数。
 *
 * @author fuce
 * @date 2026-10-02
 */
@Api(value = "上级目录下发册核对")
@Controller
@RequestMapping("/itemCatalogRow")
public class ItemCatalogRowController extends BaseController {

    @Autowired
    private ITItemCatalogRowService itemCatalogRowService;

    @Log(title = "上级目录下发册整册递入", action = "submit")
    @ApiOperation(value = "整册递入（册次号＋表头＋原始行）", notes = "解析、判定、落账、回执一处完成，回执三个数与库里逐行算出来的出自同一回计算")
    @PostMapping("/submit")
    @RequiresPermissions("itemCatalogRow:submit")
    @ResponseBody
    public AjaxResult submit(@RequestBody CatalogBooklet booklet) {
        return retobject(200, itemCatalogRowService.submitBatch(booklet));
    }

    @Log(title = "上级目录下发册整册递入", action = "submitRows")
    @ApiOperation(value = "整册递入（已解析的行，原先的递法）", notes = "返回挂上行数；越上限整册退回返回 -1；空册返回 0")
    @PostMapping("/submitRows")
    @RequiresPermissions("itemCatalogRow:submit")
    @ResponseBody
    public AjaxResult submitRows(String batchNo, @RequestBody(required = false) List<TItemCatalogRow> rows) {
        int accepted = itemCatalogRowService.submitBatch(batchNo, rows);
        if (accepted < 0) {
            return error("越过单册行数上限，整册退回，一行不留");
        }
        // 回执三个数从服务层同一口径取，界面不自己数
        return retobject(200, itemCatalogRowService.receipt(batchNo));
    }

    @ApiOperation(value = "回执重算", notes = "与落账那回同一口径，从库里逐行加出那三个数")
    @GetMapping("/receipt")
    @RequiresPermissions("itemCatalogRow:receipt")
    @ResponseBody
    public AjaxResult receipt(String batchNo) {
        return retobject(200, itemCatalogRowService.receipt(batchNo));
    }

    @ApiOperation(value = "挂起层", notes = "该册挂起行明细，屏上直接带出行序号，不劳重新数纸页")
    @GetMapping("/suspended")
    @RequiresPermissions("itemCatalogRow:suspended")
    @ResponseBody
    public AjaxResult suspended(String batchNo) {
        return retobject(200, itemCatalogRowService.listErrors(batchNo));
    }

    @ApiOperation(value = "往期册页", notes = "连着满两个核对期未落地的挂起行挪在此处，事后翻得着")
    @GetMapping("/archived")
    @RequiresPermissions("itemCatalogRow:archived")
    @ResponseBody
    public AjaxResult archived() {
        return retobject(200, itemCatalogRowService.listArchived());
    }

    @ApiOperation(value = "全辖对账", notes = "屏上自称的三个数与库里逐行加出的数对一遍，接不平的差在哪几行当场点出来")
    @GetMapping("/reconcile")
    @RequiresPermissions("itemCatalogRow:reconcile")
    @ResponseBody
    public AjaxResult reconcile(String batchNo, Integer total, Integer accepted, Integer suspended) {
        return retobject(200, itemCatalogRowService.reconcile(batchNo, total, accepted, suspended));
    }
}
