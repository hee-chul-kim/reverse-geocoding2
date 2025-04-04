package com.tmapmobility.reversegeocoding2.controller

import org.locationtech.jts.geom.Envelope
import com.tmapmobility.reversegeocoding2.service.strtree.STRTree
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import kotlin.random.Random

@Controller
class STRTreeMbrController {

    @GetMapping("/strtree-mbr")
    fun showSTRTreeMbr(model: Model): String {
        // 테스트용 데이터 생성
        val tree = STRTree<String>()
        
        // 기존 샘플 데이터 추가
        tree.insert(Envelope(0.0, 1.0, 0.0, 1.0), "A")
        tree.insert(Envelope(1.0, 2.0, 1.0, 2.0), "B")
        tree.insert(Envelope(2.0, 3.0, 2.0, 3.0), "C")
        tree.insert(Envelope(3.0, 4.0, 3.0, 4.0), "D")
        tree.insert(Envelope(4.0, 5.0, 4.0, 5.0), "E")
        tree.insert(Envelope(0.5, 1.5, 0.5, 1.5), "F")
        tree.insert(Envelope(1.5, 2.5, 1.5, 2.5), "G")
        tree.insert(Envelope(2.5, 3.5, 2.5, 3.5), "H")
        tree.insert(Envelope(3.5, 4.5, 3.5, 4.5), "I")
        tree.insert(Envelope(4.5, 5.5, 4.5, 5.5), "J")

        // 100개의 추가 샘플 데이터 생성
        val random = Random(System.currentTimeMillis())
        val gridSize = 10.0 // 전체 공간 크기
        val minEnvelopeSize = 0.2 // 최소 Envelope 크기
        val maxEnvelopeSize = 1.0 // 최대 Envelope 크기

        for (i in 1..100) {
            // 랜덤한 위치 생성
            val x = random.nextDouble(0.0, gridSize)
            val y = random.nextDouble(0.0, gridSize)
            
            // 랜덤한 크기의 Envelope 생성
            val width = random.nextDouble(minEnvelopeSize, maxEnvelopeSize)
            val height = random.nextDouble(minEnvelopeSize, maxEnvelopeSize)
            
            val envelope = Envelope(
                x, // minX
                x + width, // maxX
                y, // minY
                y + height // maxY
            )
            
            tree.insert(envelope, "Data_${i}")
        }
        
        // 트리 구축
        tree.build()
        
        // 트리 데이터를 JSON으로 변환하여 모델에 추가
        model.addAttribute("treeData", tree.toJson())
        
        return "strtree-mbr"
    }

    private fun convertTreeToJson(tree: STRTree<String>): Map<String, Any> {
        // TODO: STRTree를 JSON 형식으로 변환하는 로직 구현
        return mapOf()
    }
} 