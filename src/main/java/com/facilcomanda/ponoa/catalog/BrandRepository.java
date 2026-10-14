package com.facilcomanda.ponoa.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface BrandRepository extends CrudRepository<Brand, Long> {

  @Query("SELECT * FROM brands WHERE is_active = TRUE ORDER BY name")
  List<Brand> findAllActive();

  @Query("SELECT * FROM brands WHERE id = :id AND is_active = TRUE")
  Optional<Brand> findByIdActive(@Param("id") Long id);

  @Query("SELECT COUNT(*) > 0 FROM brands WHERE name = :name AND is_active = TRUE")
  boolean existsByNameActive(@Param("name") String name);

  @Query("SELECT COUNT(*) > 0 FROM brands WHERE name = :name AND id != :id AND is_active = TRUE")
  boolean existsByNameAndIdNotActive(@Param("name") String name, @Param("id") Long id);

  @Modifying
  @Query("UPDATE brands SET is_active = FALSE, deleted_at = NOW() WHERE id = :id AND is_active = TRUE")
  int softDeleteBrand(@Param("id") Long id);

  @Modifying
  @Query("UPDATE brands SET name = :name, description = :description WHERE id = :id AND is_active = TRUE")
  int updateBrand(@Param("id") Long id, @Param("name") String name, @Param("description") String description);
}
