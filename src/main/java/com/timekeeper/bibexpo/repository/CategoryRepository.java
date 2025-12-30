package com.timekeeper.bibexpo.repository;

import com.timekeeper.bibexpo.model.entity.Category;
import com.timekeeper.bibexpo.model.entity.Gender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long>, JpaSpecificationExecutor<Category> {

    Optional<Category> findByIdAndDeletedFalse(Long id);

    List<Category> findByRaceIdAndDeletedFalse(Long raceId);

    List<Category> findByRaceIdAndGenderAndDeletedFalse(Long raceId, Gender gender);

    boolean existsByCategoryNameAndRaceIdAndDeletedFalse(String categoryName, Long raceId);

    Optional<Category> findByCategoryNameAndRaceIdAndDeletedFalse(String categoryName, Long raceId);
}
