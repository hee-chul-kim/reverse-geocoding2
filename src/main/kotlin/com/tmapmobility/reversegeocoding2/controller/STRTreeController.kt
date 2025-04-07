package com.tmapmobility.reversegeocoding2.controller

import com.tmapmobility.reversegeocoding2.service.LocalSearchService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/strtree")
class KhcSTRtreeMbrController(private val localSearchService: LocalSearchService) {

    @GetMapping("/mbr")
    fun showSTRTreeMbr(model: Model): String {
        model.addAttribute("treeData", localSearchService.getVisualizationData())
        return "strtree-mbr"
    }
} 