package com.timekeeper.bibexpo.repository;

import com.timekeeper.bibexpo.model.entity.Race;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface RaceRepository extends JpaRepository<Race, Long>, JpaSpecificationExecutor<Race> {

    Optional<Race> findByIdAndDeletedFalse(Long id);

    List<Race> findByEventIdAndDeletedFalse(Long eventId);

    boolean existsByRaceNameAndEventIdAndDeletedFalse(String raceName, Long eventId);

    Optional<Race> findByRaceNameAndEventIdAndDeletedFalse(String raceName, Long eventId);

    int countByEventIdAndDeletedFalse(Long eventId);
}
