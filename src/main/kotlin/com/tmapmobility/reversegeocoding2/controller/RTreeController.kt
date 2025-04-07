package com.tmapmobility.reversegeocoding2.controller

import com.tmapmobility.reversegeocoding2.service.SearchService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/rtree")
class RTreeController(private val searchService: SearchService) {

    @GetMapping("/tree")
    fun showTreeStructure(model: Model): String {
        model.addAttribute("page", "tree")
        model.addAttribute("treeData", searchService.getVisualizationData())
        return "rtree-tree"
    }

    @GetMapping("/mbr")
    fun showMBRVisualization(model: Model): String {
        model.addAttribute("page", "mbr")
        model.addAttribute("treeData", searchService.getVisualizationData())
        return "rtree-mbr"
    }
} 