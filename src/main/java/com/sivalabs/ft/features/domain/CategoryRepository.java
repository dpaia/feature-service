package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.Category;
import org.springframework.data.jpa.repository.JpaRepository;

interface CategoryRepository extends JpaRepository<Category, Long> {}
