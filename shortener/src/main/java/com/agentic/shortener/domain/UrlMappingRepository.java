package com.agentic.shortener.domain;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    Optional<UrlMapping> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * Counts a click in a single UPDATE, so two redirects at the same moment
     * cannot overwrite each other's count.
     */
    @Modifying
    @Query("update UrlMapping u set u.clickCount = u.clickCount + 1, u.lastAccessedAt = :now where u.code = :code")
    int recordClick(@Param("code") String code, @Param("now") Instant now);
}
