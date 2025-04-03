package com.tmapmobility.reversegeocoding2.controller

import com.tmapmobility.reversegeocoding2.service.rtree.RTreeService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/rtree")
class RTreeViewController(
    private val rTreeService: RTreeService
) {
    @GetMapping("/view")
    fun viewRTree(model: Model): String {
        val treeData = rTreeService.getTreeVisualizationData()
        model.addAttribute("treeData", treeData)
        return "rtree-view"
    }
} 