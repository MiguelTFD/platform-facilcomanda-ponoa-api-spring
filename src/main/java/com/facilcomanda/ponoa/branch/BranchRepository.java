package com.facilcomanda.ponoa.branch;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

interface BranchRepository extends CrudRepository<Branch, Long> {

  Optional<Branch> findByCode(String code);

  boolean existsByCode(String code);

  boolean existsByName(String name);

  List<Branch> findAllByIsActiveTrue();
}
