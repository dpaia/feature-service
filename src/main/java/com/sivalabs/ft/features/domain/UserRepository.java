package com.sivalabs.ft.features.domain;

import com.sivalabs.ft.features.domain.entities.User;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface UserRepository extends ListCrudRepository<User, Long> {

    Optional<User> findByUsername(String username);
}
