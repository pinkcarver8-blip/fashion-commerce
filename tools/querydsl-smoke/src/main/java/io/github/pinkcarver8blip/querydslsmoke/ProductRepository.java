package io.github.pinkcarver8blip.querydslsmoke;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data 저장소에 QueryDSL 조회(ProductSearchRepository)를 붙인다. */
public interface ProductRepository extends JpaRepository<Product, Long>, ProductSearchRepository {
}
