package com.facilcomanda.ponoa.sales;

import org.springframework.data.repository.CrudRepository;

interface OrderRepository extends CrudRepository<CustomerOrder, Long> {}
