package com.timekeeper.bibexpo.repository;

import com.timekeeper.bibexpo.model.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long>, JpaSpecificationExecutor<Category> {

    Optional<Category> findById(Long id);

    List<Category> findByRaceId(Long raceId);

    boolean existsByCategoryNameAndRaceId(String categoryName, Long raceId);

    Optional<Category> findByCategoryNameAndRaceId(String categoryName, Long raceId);
}
