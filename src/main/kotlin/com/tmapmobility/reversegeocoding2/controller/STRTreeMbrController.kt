package com.tmapmobility.reversegeocoding2.controller

import com.tmapmobility.reversegeocoding2.service.SearchService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/api/v1/khc-strtree")
class KhcSTRtreeMbrController(private val searchService: SearchService) {

    @GetMapping("/mbr")
    fun showSTRTreeMbr(model: Model): String {
        model.addAttribute("treeData", searchService.getTreeVisualizationData())
        return "strtree-mbr"
    }
} 