package io.github.pinkcarver8blip.querydslsmoke;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/** 이슈 #7 테스트 시나리오. 실제 MySQL 8.4에서 QueryDSL 조회가 동작하는지 본다. */
@DataJpaTest(properties = {
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"logging.level.org.hibernate.SQL=debug" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ QuerydslConfig.class, TestcontainersConfiguration.class })
class ProductSearchRepositoryTest {

	@Autowired
	CategoryRepository categoryRepository;

	@Autowired
	ProductRepository productRepository;

	Category outer;
	Category top;

	@BeforeEach
	void setUp() {
		outer = categoryRepository.save(new Category("아우터"));
		top = categoryRepository.save(new Category("상의"));
		productRepository.save(new Product("코트", 50_000, outer));
		productRepository.save(new Product("티셔츠", 10_000, top));
		productRepository.save(new Product("셔츠", 30_000, top));
		productRepository.flush();
	}

	@Test
	void 카테고리_조건만_주면_그_카테고리_상품만_나온다() {
		Page<ProductSummary> page = search(new ProductSearchCondition(top.getId(), null, null), 10);

		assertThat(page.getContent()).extracting(ProductSummary::name).containsExactly("티셔츠", "셔츠");
	}

	@Test
	void 가격_범위만_주면_범위_안의_상품만_나온다() {
		Page<ProductSummary> page = search(new ProductSearchCondition(null, 20_000L, 60_000L), 10);

		assertThat(page.getContent()).extracting(ProductSummary::name).containsExactly("셔츠", "코트");
	}

	@Test
	void 조건을_모두_비우면_전체가_나온다() {
		Page<ProductSummary> page = search(new ProductSearchCondition(null, null, null), 10);

		assertThat(page.getContent()).hasSize(3);
		assertThat(page.getTotalElements()).isEqualTo(3);
	}

	@Test
	void 가격_오름차순으로_한_페이지에_2개씩_나누면_첫_페이지_2건_전체_3건이다() {
		Page<ProductSummary> page = search(new ProductSearchCondition(null, null, null), 2);

		assertThat(page.getContent()).extracting(ProductSummary::price).containsExactly(10_000L, 30_000L);
		assertThat(page.getTotalElements()).isEqualTo(3);
		assertThat(page.getTotalPages()).isEqualTo(2);
	}

	@Test
	void 카테고리를_조인해_DTO로_받으면_카테고리_이름이_채워져_있다() {
		Page<ProductSummary> page = search(new ProductSearchCondition(outer.getId(), null, null), 10);

		assertThat(page.getContent()).singleElement()
				.satisfies(s -> {
					assertThat(s.name()).isEqualTo("코트");
					assertThat(s.categoryName()).isEqualTo("아우터");
				});
	}

	private Page<ProductSummary> search(ProductSearchCondition condition, int size) {
		return productRepository.search(condition, PageRequest.of(0, size));
	}
}
