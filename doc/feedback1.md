### 📌 전국 주소도 같은 구조로 서비스 가능한가?

> 전국 데이터를 **RTREE**로 만들었을 때 **용량**과 **검색 속도**가 어느 정도인가?

---

#### 📦 용량 정보 (서울 기준)

- **피처 개수**: 약 900,000개
    
- **로드 후 메모리**: 약 500MB (shp + dbf 파일 크기의 약 2.5배)
    
- **RTREE 트리 깊이**: 6
    
- **검색 속도**: 약 45,000 ops/sec
    
- `simplify`하면 **노드 개수는 같더라도 geometry가 차지하는 용량이 줄어듬**
    
- 메타데이터(dbf)는 **모두 로드하지 않아도 되며**, **캐시 적용 여지**도 많음
    

---

#### 💾 용량 문제

1. `feature`는 `simplify`하고, `metadata`는 필요한 정보만 추출하여 **용량을 줄인다**.
    
2. 그래도 **메모리 사용량이 많다면**:
    
    - 2-1. **지역별로 쪼개서** 여러 서버에 분산하거나
        
    - 2-2. **in-memory RTREE**에는 **index(mbr)**만 올려두고, `geometry`, `metadata`는 **파일로 저장**해두고 필요할 때 읽는 방식으로 (일반 DB처럼)
        

---

#### ⚡ 속도 문제

- RTREE 평균 검색 속도는:  
    O(\log_{M}(n))
    
- 예시: **지번 개수 4,000만 개**일 경우
    
    - `entity = 4` → 트리 깊이 약 13
        
    - `entity = 10` → 트리 깊이 약 8
        
- 현재 **검색 속도 약 45,000 ops/sec**는 충분히 빠름
    
- **전국 단위로 범위가 늘어나도**, 검색 속도에 큰 변화 없을 것으로 예상됨
    
- **필요시 서버 수평 확장** 가능
    
