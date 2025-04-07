package com.tmapmobility.reversegeocoding2.controller

import com.tmapmobility.reversegeocoding2.service.rtree.RtreeSearchService
import com.tmapmobility.reversegeocoding2.service.strtree.STRtreeSearchService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/")
class RTreeController(
    private val rtreeSearchService: RtreeSearchService,
    private val stRtreeSearchService: STRtreeSearchService
) {

    @GetMapping("/rtree/mbr")
    fun viewRtreeMbr(model: Model): String {
        model.addAttribute("page", "mbr")
        model.addAttribute("treeData", rtreeSearchService.getVisualizationData())
        return "rtree-mbr"
    }

    @GetMapping("/strtree/mbr")
    fun viewStRtreeMbr(model: Model): String {
        model.addAttribute("page", "mbr")
        model.addAttribute("treeData", stRtreeSearchService.getVisualizationData())
        return "rtree-mbr"
    }

}